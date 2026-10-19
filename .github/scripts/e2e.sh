#!/usr/bin/env bash
# End-to-end test on an Android emulator, run by the "Build APK" workflow on every push.
#
# Installs the debug build of AutoSkip plus two fake apps that pose as YouTube and YouTube
# Music (see fakeapps/), turns AutoSkip on, and plays fake ads in each app several times.
# After every round it checks both sides:
#   - what the fake app saw: every ad's Skip pressed, never during its countdown, and none
#     of the traps pressed (advertiser link, Premium upsell, a "Skip" button with no ad);
#   - what AutoSkip did: the presses it logged, and its saved skip counts (total and per
#     app), which must go up by exactly the number of skips the fake app saw;
#   - muting: in rounds with "Mute ads while they play" on, every ad (including one with no
#     Skip button) must play muted, and the sound must be back 0.3 s after Skip and 0.7 s
#     after an unskippable ad ends. The real YouTube turns captions on for muted playback,
#     so a video that starts muted starts with captions. With muting off, nothing is muted.
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
# app:muting. Each app twice, with "Mute ads while they play" off and then on.
ROUNDS=(youtube:off music:off youtube:on music:on)

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

# One of AutoSkip's saved true/false settings, e.g. mute_ads.
saved_setting() {
  sh_ run-as "$AUTOSKIP" cat "/data/data/$AUTOSKIP/shared_prefs/autoskip.xml" 2>/dev/null \
    | sed -n "s/.*name=\"$1\" value=\"\([a-z]*\)\".*/\1/p" | head -n 1
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
touch "${GITHUB_WORKSPACE:-.}/e2e-started.marker"   # tells the workflow the emulator came up
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
for spec in "${ROUNDS[@]}"; do
  round_no=$((round_no + 1))
  app="${spec%%:*}"; mute="${spec#*:}"
  if [ "$app" = music ]; then pkg=$MUSIC; name="YouTube Music"; else pkg=$YOUTUBE; name="YouTube"; fi
  label="Round $round_no ($name, $ADS_PER_ROUND ads + 1 unskippable, muting $mute)"

  # Switch "Mute ads while they play" (debug builds accept this from the test). The screen
  # is opened fresh (clear-task): reopening an existing one would drop the setting.
  if [ "$mute" = on ]; then mute_flag=true; else mute_flag=false; fi
  sh_ am start -W --activity-clear-task -n "$AUTOSKIP/.MainActivity" --ez test_mute_ads "$mute_flag" >/dev/null
  sleep 1
  sh_ input keyevent KEYCODE_HOME
  saved_mute="$(saved_setting mute_ads)"
  if [ "${saved_mute:-false}" != "$mute_flag" ]; then
    error "$label: couldn't switch muting $mute (AutoSkip's saved setting is '${saved_mute:-unset}')."
    continue
  fi
  read -r before_total before_music <<<"$(saved_counts)"

  # A fresh ID per round: only a RESULT carrying it counts, so a leftover result from an
  # earlier round can never be mistaken for this one.
  token="r$round_no-$RANDOM$RANDOM"
  sh_ run-as "$pkg" rm -f files/events.log >/dev/null 2>&1 || true
  adb logcat -c
  sh_ am start -W -n "$pkg/$FAKE_ACTIVITY" --ei ads "$ADS_PER_ROUND" --es token "$token" >/dev/null
  events=""
  result=""
  for _ in $(seq 1 120); do
    events="$(sh_ run-as "$pkg" cat files/events.log 2>/dev/null)"
    result="$(grep -o "RESULT .* token=$token\$" <<<"$events" | tail -n 1)"
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

  field() { sed -n "s/.* $1=\([0-9]*\).*/\1/p" <<<" $result"; }
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

  muted_ads="$(field muted_ads)"; late_unmute="$(field late_unmute)"
  unskippable_muted="$(field unskippable_muted)"; unskippable_late="$(field unskippable_late)"
  muted_normal="$(field muted_normal)"

  problems=""
  [ "$skipped" = "$ADS_PER_ROUND" ] || problems+=" Not every ad was skipped."
  [ "${wrong:-0}" = 0 ] || problems+=" A trap was pressed."
  [ "${early:-0}" = 0 ] || problems+=" Skip was pressed during a countdown."
  [ "$counted_saved" = "$skipped" ] || problems+=" The saved count went up by $counted_saved, not $skipped."
  [ "$counted_music" = "$want_music" ] || problems+=" The YouTube Music count went up by $counted_music, not $want_music."
  [ "${muted_normal:-x}" = 0 ] || problems+=" The phone was muted during normal playback."
  if [ "$mute" = on ]; then
    [ "${muted_ads:-x}" = "$ADS_PER_ROUND" ] || problems+=" Only ${muted_ads:-?} of $ADS_PER_ROUND skippable ads played muted."
    [ "${unskippable_muted:-x}" = 1 ] || problems+=" The unskippable ad wasn't muted."
    [ "${late_unmute:-x}" = 0 ] || problems+=" After ${late_unmute:-?} skips the video started with the phone still muted (YouTube would turn captions on)."
    [ "${unskippable_late:-x}" = 0 ] || problems+=" After the unskippable ad the video started with the phone still muted (YouTube would turn captions on)."
    mute_summary="Every ad muted; sound back within 0.3 s of each Skip and 0.7 s after the unskippable ad."
  else
    [ "${muted_ads:-x}" = 0 ] && [ "${unskippable_muted:-x}" = 0 ] || problems+=" Ads were muted although muting is off."
    mute_summary="Nothing muted, as expected with muting off."
  fi

  if [ -z "$problems" ]; then
    notice "$label: passed. Skip pressed after $latencies. $mute_summary $summary"
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
