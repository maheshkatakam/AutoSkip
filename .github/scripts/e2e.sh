#!/usr/bin/env bash
# End-to-end test on an Android emulator, run by the "Build APK" workflow on every push.
#
# Installs the debug build of AutoSkip plus two fake apps that pose as YouTube and YouTube
# Music (see fakeapps/), turns AutoSkip on, and plays fake ads in each app several times.
# After every round it checks both sides:
#   - what the fake app saw: every ad's Skip pressed, never during its countdown, and none
#     of the traps pressed (advertiser link, Premium upsell, a "Skip" button with no ad);
#   - what AutoSkip did: the presses it logged, and its saved skip counts (total and per
#     app), which must go up by exactly the number of skips the fake app saw.
# Results and failure details are written as annotations on the workflow run.
set -uo pipefail

APP_APK=app/build/outputs/apk/debug/app-debug.apk
FAKE_YOUTUBE_APK=fakeapps/build/outputs/apk/youtube/debug/fakeapps-youtube-debug.apk
FAKE_MUSIC_APK=fakeapps/build/outputs/apk/music/debug/fakeapps-music-debug.apk
AUTOSKIP=io.github.autoskip
SERVICE="$AUTOSKIP/$AUTOSKIP.AutoSkipService"
YOUTUBE=com.google.android.youtube
MUSIC=com.google.android.apps.youtube.music
FAKE_ACTIVITY=io.github.autoskip.fake.FakePlayerActivity
ADS_PER_ROUND=3
ROUNDS=(youtube music youtube music)   # each app twice, so counting is tested across sessions

api="?"
failed=0
expected_total=0
expected_music=0

esc() { sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"} {print}'; }   # multi-line annotation text
error()  { echo "::error title=Emulator test (Android API $api)::$1"; failed=1; }
notice() { echo "::notice title=Emulator test (Android API $api)::$1"; }
sh_() { adb shell "$@" | tr -d '\r'; }

autoskip_log() { adb logcat -d -v brief -s AutoSkip:V | tr -d '\r'; }

check_crashes() {
  local crashes
  crashes="$(adb logcat -d -b crash | grep -E "$AUTOSKIP|$YOUTUBE|$MUSIC" -A 15 | head -40)"
  if [ -n "$crashes" ]; then error "A crash was logged ($1):%0A$(echo "$crashes" | esc)"; fi
}

# AutoSkip's saved counts, as "total music".
saved_counts() {
  local prefs total music
  prefs="$(sh_ run-as "$AUTOSKIP" cat "/data/data/$AUTOSKIP/shared_prefs/autoskip.xml" 2>/dev/null)"
  total="$(sed -n 's/.*name="skipped_count" value="\([0-9]*\)".*/\1/p' <<<"$prefs" | head -n 1)"
  music="$(sed -n 's/.*name="skipped_count_youtube_music" value="\([0-9]*\)".*/\1/p' <<<"$prefs" | head -n 1)"
  echo "${total:-0} ${music:-0}"
}

# --- Emulator ready ---------------------------------------------------------------------
adb wait-for-device
for _ in $(seq 1 90); do [ "$(sh_ getprop sys.boot_completed)" = 1 ] && break; sleep 2; done
api="$(sh_ getprop ro.build.version.sdk)"
adb logcat -G 16M >/dev/null 2>&1 || true      # room for every line of every round
sh_ svc power stayon true
sh_ input keyevent KEYCODE_WAKEUP
sh_ wm dismiss-keyguard >/dev/null 2>&1 || true
sh_ input keyevent 82 >/dev/null 2>&1 || true

# --- Install and turn AutoSkip on ---------------------------------------------------------
for apk in "$APP_APK" "$FAKE_YOUTUBE_APK" "$FAKE_MUSIC_APK"; do
  if ! out="$(adb install -r -t "$apk" 2>&1)"; then
    error "Could not install $apk:%0A$(echo "$out" | esc)"; exit 1
  fi
done

adb logcat -c
# Open AutoSkip's own screen once: a smoke test that it starts, and it takes the app out of
# Android's "never launched" state.
start_out="$(sh_ am start -W -n "$AUTOSKIP/.MainActivity")"
sleep 2
if ! grep -q "Status: ok" <<<"$start_out" || [ -z "$(sh_ pidof "$AUTOSKIP")" ]; then
  error "AutoSkip's screen did not start:%0A$(echo "$start_out" | esc)"
fi
check_crashes "opening AutoSkip"
sh_ input keyevent KEYCODE_HOME

sh_ settings put secure enabled_accessibility_services "$SERVICE"
sh_ settings put secure accessibility_enabled 1
connected=false
for _ in $(seq 1 30); do
  if autoskip_log | grep -q "connected"; then connected=true; break; fi
  sleep 1
done
if [ "$connected" != true ]; then
  error "AutoSkip's accessibility service never connected.%0A$(sh_ dumpsys accessibility | grep -i -E 'enabled|bound|autoskip' | head -20 | esc)"
  exit 1
fi

# --- Play fake ads ----------------------------------------------------------------------
round_no=0
for app in "${ROUNDS[@]}"; do
  round_no=$((round_no + 1))
  if [ "$app" = music ]; then pkg=$MUSIC; name="YouTube Music"; else pkg=$YOUTUBE; name="YouTube"; fi
  label="Round $round_no ($name, $ADS_PER_ROUND ads)"
  read -r before_total before_music <<<"$(saved_counts)"

  adb logcat -c
  sh_ am start -W -n "$pkg/$FAKE_ACTIVITY" --ei ads "$ADS_PER_ROUND" >/dev/null
  events=""
  result=""
  for _ in $(seq 1 120); do
    events="$(sh_ run-as "$pkg" cat files/events.log 2>/dev/null)"
    result="$(grep -o 'RESULT .*' <<<"$events" | tail -n 1)"
    [ -n "$result" ] && break
    sleep 1
  done
  sleep 1   # let AutoSkip save the last count
  as_log="$(autoskip_log)"
  read -r after_total after_music <<<"$(saved_counts)"
  check_crashes "$label"
  sh_ am force-stop "$pkg"

  if [ -z "$result" ]; then
    error "$label: the fake app never finished.%0AFake app events:%0A$(echo "$events" | esc)AutoSkip log:%0A$(tail -n 40 <<<"$as_log" | esc)"
    sleep 2
    continue
  fi

  field() { sed -n "s/.*$1=\([0-9]*\).*/\1/p" <<<"$result"; }
  skipped="$(field skipped)"; wrong="$(field wrong)"; early="$(field early)"
  skipped="${skipped:-0}"
  expected_total=$((expected_total + skipped))
  [ "$app" = music ] && expected_music=$((expected_music + skipped))

  presses="$(grep -c "pressed Skip" <<<"$as_log")"
  counted_log="$(grep -c "skip counted" <<<"$as_log")"
  counted_saved=$((after_total - before_total))
  counted_music=$((after_music - before_music))
  want_music=0; [ "$app" = music ] && want_music=$skipped
  latencies="$(grep -o 'latency=[0-9]*ms' <<<"$events" | sed 's/latency=//' | paste -sd ' ' -)"
  summary="fake app: $result. AutoSkip: $presses presses logged, $counted_log skips counted in its log, saved count +$counted_saved (YouTube Music +$counted_music), now $after_total in total."

  problems=""
  [ "$skipped" = "$ADS_PER_ROUND" ] || problems+=" Not every ad was skipped."
  [ "${wrong:-0}" = 0 ] || problems+=" A trap was pressed."
  [ "${early:-0}" = 0 ] || problems+=" Skip was pressed during a countdown."
  [ "$counted_saved" = "$skipped" ] || problems+=" The saved count went up by $counted_saved, not $skipped."
  [ "$counted_music" = "$want_music" ] || problems+=" The YouTube Music count went up by $counted_music, not $want_music."

  if [ -z "$problems" ]; then
    notice "$label: passed. Skip pressed after $latencies. $summary"
  else
    error "$label:$problems%0A$summary%0AFake app events:%0A$(echo "$events" | esc)AutoSkip log:%0A$(tail -n 60 <<<"$as_log" | esc)"
  fi
  sleep 2
done

# --- Totals ------------------------------------------------------------------------------
read -r final_total final_music <<<"$(saved_counts)"
want_total=$(( ADS_PER_ROUND * ${#ROUNDS[@]} ))
if [ "$final_total" != "$expected_total" ] || [ "$final_total" != "$want_total" ] || [ "$final_music" != "$expected_music" ]; then
  error "Final skip count is wrong: AutoSkip saved $final_total ($final_music in YouTube Music); the fake apps saw $expected_total ($expected_music in YouTube Music); expected $want_total."
else
  notice "Final skip count is right: $final_total in total, $final_music of them in YouTube Music, matching the skips the fake apps saw."
fi

# --- AutoSkip's screen shows the count without crashing -------------------------------------
adb logcat -c
sh_ am start -W -n "$AUTOSKIP/.MainActivity" >/dev/null
sleep 2
check_crashes "reopening AutoSkip after the test"

if [ "$failed" != 0 ]; then exit 1; fi
notice "All checks passed."
