#!/usr/bin/env bash
# End-to-end test on an Android emulator, run by the "Build APK" workflow on every push.
#
# Installs the debug build of AutoSkip plus two fake apps that pose as YouTube and YouTube
# Music (see fakeapps/), turns AutoSkip on, and plays fake ads in each app several times.
# Then it checks both sides:
#   - what the fake apps saw: every ad's Skip pressed, never during its countdown, and none
#     of the traps pressed (advertiser link, Premium upsell, a "Skip" button with no ad);
#   - what AutoSkip saved: its skip counts (total and per app) match the skips that happened.
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
total_skipped=0
music_skipped=0

esc() { sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"} {print}'; }   # multi-line annotation text
error()  { echo "::error title=Emulator test (Android API $api)::$1"; failed=1; }
notice() { echo "::notice title=Emulator test (Android API $api)::$1"; }
sh_() { adb shell "$@" | tr -d '\r'; }

recent_logs() { adb logcat -d -v brief -s AutoSkipTest:I AutoSkip:D | tail -n "${1:-60}" | esc; }

check_crashes() {
  local crashes
  crashes="$(adb logcat -d -b crash | grep -E "$AUTOSKIP|$YOUTUBE|$MUSIC" -A 15 | head -40)"
  if [ -n "$crashes" ]; then error "A crash was logged ($1):%0A$(echo "$crashes" | esc)"; fi
}

# --- Emulator ready ---------------------------------------------------------------------
adb wait-for-device
for _ in $(seq 1 90); do [ "$(sh_ getprop sys.boot_completed)" = 1 ] && break; sleep 2; done
api="$(sh_ getprop ro.build.version.sdk)"
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
  if adb logcat -d -s AutoSkip:D | grep -q "connected"; then connected=true; break; fi
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

  adb logcat -c
  sh_ am start -W -n "$pkg/$FAKE_ACTIVITY" --ei ads "$ADS_PER_ROUND" >/dev/null
  result=""
  for _ in $(seq 1 120); do
    result="$(adb logcat -d -s AutoSkipTest:I | grep -o 'RESULT .*' | tail -n 1 | tr -d '\r')"
    [ -n "$result" ] && break
    sleep 1
  done

  if [ -z "$result" ]; then
    error "$label: the fake app never finished.%0A$(recent_logs)"
  else
    field() { sed -n "s/.*$1=\([0-9]*\).*/\1/p" <<<"$result"; }
    skipped="$(field skipped)"; wrong="$(field wrong)"; early="$(field early)"
    total_skipped=$((total_skipped + ${skipped:-0}))
    [ "$app" = music ] && music_skipped=$((music_skipped + ${skipped:-0}))
    if [ "$skipped" = "$ADS_PER_ROUND" ] && [ "$wrong" = 0 ] && [ "$early" = 0 ]; then
      latencies="$(adb logcat -d -s AutoSkipTest:I | grep -o 'latency=[0-9]*ms' | sed 's/latency=//' | paste -sd ' ' -)"
      notice "$label: all skipped, nothing wrong pressed. Time from Skip appearing to being pressed: $latencies"
    else
      error "$label: $result%0A$(recent_logs)"
    fi
  fi
  check_crashes "$label"
  sh_ am force-stop "$pkg"
  sleep 2
done

# --- What AutoSkip counted ----------------------------------------------------------------
sleep 2   # counts are saved asynchronously
prefs="$(sh_ run-as "$AUTOSKIP" cat "/data/data/$AUTOSKIP/shared_prefs/autoskip.xml" 2>&1)"
pref() { sed -n "s/.*name=\"$1\" value=\"\([0-9]*\)\".*/\1/p" <<<"$prefs" | head -n 1; }
counted_total="$(pref skipped_count)"; counted_total="${counted_total:-0}"
counted_music="$(pref skipped_count_youtube_music)"; counted_music="${counted_music:-0}"
expected_total=$(( ADS_PER_ROUND * ${#ROUNDS[@]} ))

if [ "$counted_total" != "$total_skipped" ] || [ "$counted_total" != "$expected_total" ]; then
  error "Skip count is wrong: AutoSkip counted $counted_total, the fake apps saw $total_skipped skips, expected $expected_total.%0ASaved settings:%0A$(echo "$prefs" | esc)"
elif [ "$counted_music" != "$music_skipped" ]; then
  error "YouTube Music count is wrong: AutoSkip counted $counted_music, the fake app saw $music_skipped.%0A$(echo "$prefs" | esc)"
else
  notice "Skip count is right: $counted_total in total, $counted_music of them in YouTube Music, matching the $total_skipped skips the fake apps saw."
fi

# --- AutoSkip's screen shows the count without crashing -------------------------------------
adb logcat -c
sh_ am start -W -n "$AUTOSKIP/.MainActivity" >/dev/null
sleep 2
check_crashes "reopening AutoSkip after the test"

if [ "$failed" != 0 ]; then exit 1; fi
notice "All checks passed."
