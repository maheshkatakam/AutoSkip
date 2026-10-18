# AutoSkip for Android

An Android app that presses the **Skip** button in the official YouTube app for you, as soon as YouTube shows it. It is the phone version of the idea behind the [YouTube Ad Auto-skipper](https://github.com/squgeim/yt-ad-autoskipper) browser extension: it does not block ads, it only presses the button YouTube already offers.

- Presses **Skip** the moment it becomes pressable, or after a delay you choose (0 to 10 seconds).
- Optionally **mutes** ads while they play, including ads that have no Skip button.
- Counts how many ads it has skipped.
- Reads only the YouTube app, stores nothing it sees, and has **no internet permission**.

Works on Android 8.0 and later. Built for Android 16 (API 36).

---

## How it works

Android's **Accessibility API** lets an app read what is on screen in other apps and press buttons there. Screen readers such as TalkBack use it. AutoSkip uses it for one app (YouTube) and one button (Skip):

1. Android sends AutoSkip a notification whenever YouTube's screen changes.
2. AutoSkip reads YouTube's screen as a tree of labelled elements and looks for an ad (`Sponsored · 0:16`, `Ad 1 of 2`, view IDs like `ad_progress_text`) and for the Skip control (view IDs containing `skip_ad`, or labels like "Skip ad" / "Skip").
3. While the countdown is still running, it waits. Once the button is pressable, it presses it with `ACTION_CLICK`, the same action TalkBack uses.
4. While an ad is on screen it re-checks every 300 ms; with YouTube open and no ad, once a second; with YouTube closed, never.

All the matching rules are in [`SkipDetector.kt`](app/src/main/java/io/github/autoskip/SkipDetector.kt), which has no Android code and is covered by unit tests. Safety rules built in:

- It never presses anything containing an advertiser link ("Visit advertiser", "Install", "Shop now"...). Pressing those would open the ad.
- It never presses a container bigger than a button.
- A bare "Skip" label only counts while something else on screen proves an ad is playing, so a video titled "Skip" is safe.
- A "Sponsored" label on its own is ignored, because promoted cards in the home feed use it with no ad playing.
- If YouTube ignores the press, it falls back to a tap at the button, but never if anything is drawn over the button (YouTube sometimes slides an install card over Skip).

---

## 1. Build the app

Pick one.

### Option A: Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio) (free, Windows/macOS/Linux).
2. **File → Open** and choose this `AutoSkip` folder. Let Gradle sync finish. If it offers to install SDK Platform 36 or Build Tools 36.1.0, accept.
3. On your phone: **Settings → About phone →** tap **Build number** 7 times to unlock Developer options. Then **Settings → System → Developer options → USB debugging: on**.
4. Plug the phone in, accept the "Allow USB debugging?" prompt, choose your phone in the device list at the top of Android Studio, and press **Run ▶**.

Installing this way (over USB/adb) avoids Android's "restricted settings" block, described below.

### Option B: GitHub builds it for you (nothing to install)

1. Create a free GitHub account and a new repository, then upload this folder's contents (keep the `.github` folder).
2. Open the **Actions** tab. The **Build APK** workflow runs the unit tests and builds the app.
3. Open the finished run and download **AutoSkip-debug-apk**. Unzip it to get `app-debug.apk`, and copy that to your phone.

### Option C: Command line

Needs JDK 17+ and the Android SDK (platform 36, build-tools 36.1.0) with `ANDROID_HOME` set.

```bash
./gradlew testDebugUnitTest     # run the detector's unit tests
./gradlew assembleDebug         # build app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 2. Install the APK (only if you didn't use Run ▶)

1. Open `app-debug.apk` on the phone and allow your browser or file manager to install unknown apps when asked.
2. **Restricted settings (Android 13 and later).** Android blocks Accessibility for apps installed from a downloaded APK. If the AutoSkip switch is greyed out or you see "Restricted setting": open **Settings → Apps → AutoSkip**, tap **⋮** (top right) → **Allow restricted settings**, confirm with your PIN, then go back and turn it on. The app has a button that opens this screen.
3. **Developer verification.** Google is rolling out a requirement that apps come from registered developers. From 30 September 2026 it applies in Brazil, Indonesia, Singapore and Thailand, with a global rollout planned for 2027. Installing with adb (Option A or `adb install`) remains an official route for unverified apps. Otherwise Android offers an "advanced flow" for unverified apps in Developer options, with a one-time 24-hour wait.

---

## 3. Turn it on

1. Open **AutoSkip** and read what it accesses.
2. Tap **Open Accessibility settings → Installed apps (or Downloaded apps) → AutoSkip for YouTube → On**, and confirm. Android does not let any app turn this on for itself.
3. Go back to AutoSkip. The status should say **On**.

## 4. Test it

Open YouTube and play videos until a skippable ad starts. When the Skip button appears it should be pressed within about a third of a second, and the count in AutoSkip goes up. Ads without a Skip button still play (turn on **Mute ads while they play** for those).

---

## When YouTube changes its UI

YouTube renames buttons and view IDs between app versions, so expect to update the rules now and then. Here's the routine:

1. In AutoSkip, turn on **Debug logging**.
2. With the phone connected over USB, run `adb logcat -s AutoSkip`, then play videos until an ad appears. If an ad is on screen for 6 seconds with no Skip button recognised, AutoSkip prints every labelled element it sees once.
3. Or take a snapshot while the Skip button is visible:
   ```bash
   adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
   ```
   Search `ui.xml` for `skip` and look at the `resource-id`, `text` and `content-desc` of the button.
4. Add what you find to the lists at the top of `SkipDetector.kt`, add a test for it in `SkipDetectorTest.kt`, and run `./gradlew testDebugUnitTest`.

**Other languages.** View IDs work in any language. Labels are English only out of the box, so if your phone is set to Hindi, Spanish and so on, and the ID is missing on your device, add your language's wording for "Skip ad" and "Ad" to the lists.

## Keeping it running

Some phones (Xiaomi, Samsung, OnePlus, Oppo, Vivo, and others) kill background apps aggressively. If skipping works and then silently stops, the service was killed, and Accessibility settings may still show it as on (AutoSkip's status then says **Turned on, but not running**). Fixes: set AutoSkip's battery use to **Unrestricted**, allow auto-start if your phone has that option, and see [dontkillmyapp.com](https://dontkillmyapp.com) for your brand.

## Limitations

- **Skippable ads only.** It presses the button YouTube shows you; it can't shorten ads that have none. Muting is the most an outside app can do about those.
- **Official YouTube app only** (`com.google.android.youtube`). Not YouTube in a browser, YouTube Music, or YouTube on TV.
- **Advanced Protection Mode (Android 17).** When that security mode is on, Android disables accessibility services that aren't declared assistive tools, which includes AutoSkip.
- **Needs maintenance** after some YouTube updates (see above).

## Privacy and safety

An accessibility service is powerful: it can read the screen. That's why this app is scoped tightly. It receives events from YouTube only, re-checks the app of every window before reading it, keeps nothing, and declares **no permissions at all**. Without `INTERNET`, Android won't let it open a network connection, so nothing it reads can leave the phone. If you ever fork or share this code, keep it that way, and don't install builds of apps like this from people you don't trust.

## Rules worth knowing

- **YouTube's Terms of Service** forbid accessing the service "using any automated means" without permission. An app that presses Skip for you is automated interaction, even though it only presses a button you could press yourself. YouTube doesn't publish how (or whether) it enforces this against skip helpers, so use it knowing that. The fully sanctioned way to avoid ads is YouTube Premium.
- **Google Play won't accept it.** Play's Device and Network Abuse policy prohibits apps that "interfere with another app displaying ads", and Play's Accessibility API policy limits how automation apps may use the API. Share it as a GitHub release, or through F-Droid / IzzyOnDroid, rather than Play.
- **Licence.** Pick one (for example MIT) before publishing.

## Ideas for next steps

- A Quick Settings tile to pause skipping.
- YouTube Music support (it has its own Skip button and package name).
- A Firefox for Android add-on for `m.youtube.com`, for people who watch in the browser.
- A Safari Web Extension for iPhone (iOS doesn't let apps control the YouTube app, but Safari extensions can work on `m.youtube.com`).

## Credits

- Idea: [squgeim/yt-ad-autoskipper](https://github.com/squgeim/yt-ad-autoskipper) (GPL-3.0). No code is copied from it.
- YouTube view IDs (`skip_ad_button`, `ad_progress_text`, `player_learn_more_button`) and lessons about feed "Sponsored" cards were learned from [Javid-Git/ad-skipper](https://github.com/Javid-Git/ad-skipper) (MIT). The code here is written separately.
- The Gradle wrapper (`gradlew`, `gradle/wrapper/`) is part of Gradle, Apache-2.0.

Not affiliated with YouTube or Google. "YouTube" is a trademark of Google LLC.
