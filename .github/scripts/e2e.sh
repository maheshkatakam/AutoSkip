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
#   - "Keep playing when asked": every round ends with the pause prompt ("Video paused.
#     Continue watching?", or "Music paused" / "Continue listening?"). With the setting on,
#     AutoSkip must press its "Yes" and count it once; with it off, it must leave it alone.
#     Its traps (another Yes/No dialog, a poll asking "Are you still watching?" in a
#     scrolling list, a "Continue watching" shelf, the prompt's own "No") must never be
#     pressed. The last round turns skipping off to check the setting works on its own.
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
# app:mute:keep:skip, where mute is "Mute ads while they play", keep is "Keep playing when
# asked" and skip is "Skip ads automatically". Each app with muting off and on, and with
# keep-playing on and off; then one round with skipping off and no ads, only the prompt.
ROUNDS=(youtube:off:on:on music:off:on:on youtube:on:off:on music:on:off:on youtube:off:on:off)

api="?"
failed=0
expected_total=0
expected_music=0
want_total=0
want_kept=0

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

# AutoSkip's saved counts, as "total music kept": ads skipped in all, in YouTube Music, and
# pause prompts answered.
saved_counts() {
  local prefs total music kept
  prefs="$(sh_ run-as "$AUTOSKIP" cat "/data/data/$AUTOSKIP/shared_prefs/autoskip.xml" 2>/dev/null)"
  total="$(sed -n 's/.*name="skipped_count" value="\([0-9]*\)".*/\1/p' <<<"$prefs" | head -n 1)"
  music="$(sed -n 's/.*name="skipped_count_youtube_music" value="\([0-9]*\)".*/\1/p' <<<"$prefs" | head -n 1)"
  kept="$(sed -n 's/.*name="kept_playing_count" value="\([0-9]*\)".*/\1/p' <<<"$prefs" | head -n 1)"
  echo "${total:-0} ${music:-0} ${kept:-0}"
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
  IFS=: read -r app mute keep skip <<<"$spec"
  if [ "$app" = music ]; then pkg=$MUSIC; name="YouTube Music"; else pkg=$YOUTUBE; name="YouTube"; fi
  if [ "$skip" = on ]; then ads=$ADS_PER_ROUND; what="$ads ads + 1 unskippable"; else ads=0; what="skipping off, no ads"; fi
  if [ "$keep" = on ]; then want_answered=1; else want_answered=0; fi
  label="Round $round_no ($name, $what, muting $mute, keep playing $keep)"

  # Switch the settings (debug builds accept these from the test). The screen is opened
  # fresh (clear-task): reopening an existing one would drop them.
  flag() { if [ "$1" = on ]; then echo true; else echo false; fi; }
  sh_ am start -W --activity-clear-task -n "$AUTOSKIP/.MainActivity" \
    --ez test_mute_ads "$(flag "$mute")" --ez test_keep_playing "$(flag "$keep")" \
    --ez test_skip_enabled "$(flag "$skip")" >/dev/null
  sleep 1
  sh_ input keyevent KEYCODE_HOME
  bad_setting=""
  for pair in "mute_ads:$mute" "keep_playing:$keep" "skip_enabled:$skip"; do
    key="${pair%%:*}"; want="$(flag "${pair#*:}")"; got="$(saved_setting "$key")"
    [ "$got" = "$want" ] || bad_setting+=" $key is '${got:-unset}', wanted '$want'."
  done
  if [ -n "$bad_setting" ]; then
    error "$label: couldn't switch AutoSkip's settings:$bad_setting"
    continue
  fi
  read -r before_total before_music before_kept <<<"$(saved_counts)"

  # A fresh ID per round: only a RESULT carrying it counts, so a leftover result from an
  # earlier round can never be mistaken for this one.
  token="r$round_no-$RANDOM$RANDOM"
  sh_ run-as "$pkg" rm -f files/events.log >/dev/null 2>&1 || true
  adb logcat -c
  if [ "$skip" = on ]; then prompt_only=false; else prompt_only=true; fi
  sh_ am start -W -n "$pkg/$FAKE_ACTIVITY" --ei ads "$ADS_PER_ROUND" --ez prompt_only "$prompt_only" \
    --es token "$token" >/dev/null
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
  read -r after_total after_music after_kept <<<"$(saved_counts)"
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
  answered="$(field answered)"; answered="${answered:-0}"
  expected_total=$((expected_total + skipped))
  [ "$app" = music ] && expected_music=$((expected_music + skipped))
  want_total=$((want_total + ads))
  want_kept=$((want_kept + want_answered))

  presses="$(grep -c "pressed Skip" <<<"$as_log")"
  counted_log="$(grep -c "skip counted" <<<"$as_log")"
  counted_saved=$((after_total - before_total))
  counted_music=$((after_music - before_music))
  want_music=0; [ "$app" = music ] && want_music=$skipped
  latencies="$(grep -v PROMPT <<<"$events" | grep -o 'latency=[0-9]*ms' | sed 's/latency=//' | paste -sd ' ' -)"
  prompt_latency="$(grep PROMPT_ANSWERED <<<"$events" | grep -o 'latency=[0-9]*' | sed 's/latency=//' | tail -n 1)"
  traps="$(grep -o 'TRAPS .*' <<<"$events" | tail -n 1)"
  answers_log="$(grep -c "answered the pause prompt" <<<"$as_log")"
  kept_log="$(grep -c "kept playing in" <<<"$as_log")"
  kept_saved=$((after_kept - before_kept))
  summary="fake app: $result. AutoSkip: $presses presses logged, $counted_log skips counted in its log, saved count +$counted_saved (YouTube Music +$counted_music), now $after_total in total; pause prompt answered $answers_log times in its log, $kept_log counted, saved +$kept_saved."

  muted_ads="$(field muted_ads)"; late_unmute="$(field late_unmute)"
  unskippable_muted="$(field unskippable_muted)"; unskippable_late="$(field unskippable_late)"
  muted_normal="$(field muted_normal)"

  problems=""
  [ "$skipped" = "$ads" ] || problems+=" $skipped of $ads ads were skipped."
  [ "${wrong:-0}" = 0 ] || problems+=" A trap was pressed."
  [ "${early:-0}" = 0 ] || problems+=" Skip was pressed during a countdown."
  [ "$counted_saved" = "$skipped" ] || problems+=" The saved count went up by $counted_saved, not $skipped."
  [ "$counted_music" = "$want_music" ] || problems+=" The YouTube Music count went up by $counted_music, not $want_music."
  [ "${muted_normal:-x}" = 0 ] || problems+=" The phone was muted during normal playback."
  if [ "$mute" = on ]; then
    [ "${muted_ads:-x}" = "$ads" ] || problems+=" Only ${muted_ads:-?} of $ads skippable ads played muted."
    [ "${unskippable_muted:-x}" = 1 ] || problems+=" The unskippable ad wasn't muted."
    [ "${late_unmute:-x}" = 0 ] || problems+=" After ${late_unmute:-?} skips the video started with the phone still muted (YouTube would turn captions on)."
    [ "${unskippable_late:-x}" = 0 ] || problems+=" After the unskippable ad the video started with the phone still muted (YouTube would turn captions on)."
    mute_summary="Every ad muted; sound back within 0.3 s of each Skip and 0.7 s after the unskippable ad."
  else
    [ "${muted_ads:-x}" = 0 ] && [ "${unskippable_muted:-x}" = 0 ] || problems+=" Ads were muted although muting is off."
    mute_summary="Nothing muted, as expected with muting off."
  fi
  if [ "$skip" != on ]; then
    [ "$presses" = 0 ] || problems+=" AutoSkip pressed Skip $presses times with skipping off."
  fi
  [ "$answered" = "$want_answered" ] || problems+=" The pause prompt was answered $answered times, wanted $want_answered (keep playing $keep)."
  [ "$answers_log" = "$want_answered" ] || problems+=" AutoSkip logged $answers_log answers to the pause prompt, wanted $want_answered."
  [ "$kept_saved" = "$want_answered" ] || problems+=" The saved keep-playing count went up by $kept_saved, not $want_answered."
  # The poll and shelf traps only test something if they were on screen and the poll's list scrolls.
  [ "$traps" = "TRAPS shelf_visible=true poll_visible=true poll_scrolls=true" ] ||
    problems+=" The keep-playing traps weren't all on screen (${traps:-no TRAPS line}), so they tested nothing."
  if [ "$keep" = on ]; then
    keep_summary="Pause prompt answered after ${prompt_latency:-?}ms; its traps left alone."
  else
    keep_summary="Pause prompt left alone, as expected with keep playing off."
  fi

  if [ -z "$problems" ]; then
    if [ "$ads" -gt 0 ]; then skip_summary="Skip pressed after $latencies."; else skip_summary="Nothing pressed but the prompt's Yes."; fi
    notice "$label: passed. $skip_summary $mute_summary $keep_summary $summary"
  else
    error "$label:$problems%0A$summary%0AFake app events:%0A$(echo "$events" | esc)AutoSkip log:%0A$(tail -n 60 <<<"$as_log" | esc)"
  fi
  sleep 2
done

# --- Totals ------------------------------------------------------------------------------
read -r final_total final_music final_kept <<<"$(saved_counts)"
if [ "$final_total" != "$expected_total" ] || [ "$final_total" != "$want_total" ] || [ "$final_music" != "$expected_music" ]; then
  error "Final skip count is wrong: AutoSkip saved $final_total ($final_music in YouTube Music); the fake apps saw $expected_total ($expected_music in YouTube Music); expected $want_total."
else
  notice "Final skip count is right: $final_total in total, $final_music of them in YouTube Music, matching the skips the fake apps saw."
fi
if [ "$final_kept" != "$want_kept" ]; then
  error "Final keep-playing count is wrong: AutoSkip saved $final_kept, expected $want_kept."
else
  notice "Final keep-playing count is right: $final_kept pause prompts answered, one per round with the setting on."
fi

# --- AutoSkip's screen shows the count without crashing -------------------------------------
adb logcat -c
sh_ am start -W -n "$AUTOSKIP/.MainActivity" >/dev/null
sleep 2
check_crashes "reopening AutoSkip after the test"

if [ "$failed" != 0 ]; then exit 1; fi
notice "All checks passed."
