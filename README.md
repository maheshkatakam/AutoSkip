# AutoSkip for Android

An Android app that presses the **Skip** button in the official YouTube app for you, as soon as YouTube shows it. It is the phone version of the idea behind the [YouTube Ad Auto-skipper](https://github.com/squgeim/yt-ad-autoskipper) browser extension: it does not block ads, it only presses the button YouTube already offers.

- Presses **Skip** the moment it becomes pressable, or after a delay you choose (0 to 10 seconds).
- Optionally **mutes** ads while they play, including ads that have no Skip button.
- Counts how many ads it has skipped.
- Reads only the YouTube app, stores nothing it sees, and has **no internet permission**.
- **Free**, with no ads and no tracking. Open source under the [MIT licence](LICENSE).

Works on Android 8.0 and later. Built for Android 16 (API 36).

---

## Download

Get the latest **AutoSkip-v….apk** from the [Releases page](../../releases/latest), open it on your phone, then follow [Install](#2-install-the-apk) and [Turn it on](#3-turn-it-on) below.

Every official release is signed with the same key, so a new version installs over the old one and keeps your settings. To check a download is genuine, its signing certificate's SHA-256 fingerprint must be:

```
c5e60d4195e3fd86e3c4f407ec998d184a478b935770e7add7552afb50afeb62
```

```bash
apksigner verify --print-certs AutoSkip-v1.0.apk   # "certificate SHA-256 digest" must match the line above
```

`apksigner` comes with the Android SDK build tools. Builds from forks or from Android Studio are signed with a different key, so uninstall those before installing an official release.

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

## 1. Build the app yourself (optional)

Only needed if you want to change the code. Otherwise use the [Releases page](../../releases/latest).

### Option A: Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (free, Windows/macOS/Linux).
2. **File → Open** and choose this folder. Let Gradle sync finish. If it offers to install SDK Platform 36 or Build Tools 36.1.0, accept.
3. On your phone: **Settings → About phone →** tap **Build number** 7 times to unlock Developer options. Then **Settings → System → Developer options → USB debugging: on**.
4. Plug the phone in, accept the "Allow USB debugging?" prompt, choose your phone in the device list at the top of Android Studio, and press **Run ▶**.

Installing this way (over USB/adb) avoids Android's "restricted settings" block, described below. Android Studio signs with your computer's own debug key, so you'll need to uninstall that build before installing an official release, and the other way round.

### Option B: GitHub builds it for you

Every push runs the **Build APK** workflow: unit tests, a build, and a check that the APK requests no permissions. Open the **Actions** tab, click the latest green run, and download **AutoSkip-apk**. On this repository the APK is signed with the release key, so it installs over an official release. Forks get a throwaway key instead.

### Option C: Command line

Needs JDK 17+ and the Android SDK (platform 36, build-tools 36.1.0) with `ANDROID_HOME` set.

```bash
./gradlew testDebugUnitTest     # run the detector's unit tests
./gradlew assembleRelease       # build app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without the release key (see [Maintainers](#maintainers)), `assembleRelease` signs with your local debug key.

---

## 2. Install the APK

1. Open the APK on the phone and allow your browser or file manager to install unknown apps when asked.
2. **Restricted settings (Android 13 and later).** Android blocks Accessibility for apps installed from a downloaded APK. If the AutoSkip switch is greyed out or you see "Restricted setting": open **Settings → Apps → AutoSkip**, tap **⋮** (top right) → **Allow restricted settings**, confirm with your PIN, then go back and turn it on. The app has a button that opens this screen.
3. **Developer verification.** Google is rolling out a requirement that apps come from registered developers. From 30 September 2026 it applies in Brazil, Indonesia, Singapore and Thailand, with a global rollout planned for 2027. Installing with adb remains an official route for unverified apps. Otherwise Android offers an "advanced flow" for unverified apps in Developer options, with a one-time 24-hour wait.

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

---

## Free, private and offline, and checked on every build

An accessibility service is powerful: it can read the screen. So AutoSkip makes these promises, and the build enforces the technical ones:

- **No permissions at all, and never INTERNET.** Without the internet permission Android won't let the app open a network connection, so nothing it reads can leave the phone. [`verify-apk.sh`](.github/scripts/verify-apk.sh) inspects every APK the workflows build and fails the build if it requests *any* permission. A release can't be published without passing it.
- **YouTube only.** It receives events from YouTube only, and re-checks the app of every window before reading it.
- **Keeps nothing.** It stores only its own settings and the skip counter.
- **Free, with no ads, accounts, analytics or tracking.**

If you fork this, keep it that way, and don't install builds of apps like this from people you don't trust. Check the fingerprint above.

## Rules worth knowing

- **YouTube's Terms of Service** forbid accessing the service "using any automated means" without permission. An app that presses Skip for you is automated interaction, even though it only presses a button you could press yourself. YouTube doesn't publish how (or whether) it enforces this against skip helpers, so use it knowing that. The fully sanctioned way to avoid ads is YouTube Premium.
- **Google Play won't accept it.** Play's Device and Network Abuse policy prohibits apps that "interfere with another app displaying ads", and Play's Accessibility API policy limits how automation apps may use the API. It is distributed through this repository's Releases page instead.
- **Licence.** MIT, see [LICENSE](LICENSE). The software comes with no warranty.

---

## Maintainers

### Publishing a new version

1. In [`app/build.gradle.kts`](app/build.gradle.kts), raise `versionCode` by 1 and set `versionName` (for example `1.1`).
2. Commit and push.
3. Tag the commit `v` + versionName and push the tag:
   ```bash
   git tag v1.1 && git push origin v1.1
   ```
   Or on GitHub: **Releases → Draft a new release → Choose a tag → type `v1.1` → Create new tag → Publish release**.

The **Release** workflow then runs the tests, builds the APK, checks it requests no permissions and is signed with the release key, and attaches `AutoSkip-v1.1.apk` plus its checksum to the release. It refuses to publish if the tag doesn't match `versionName`.

### The signing key

The release key is never stored in this repository. It lives in two GitHub secrets (**Settings → Secrets and variables → Actions**):

| Secret | Contents |
| --- | --- |
| `AUTOSKIP_KEYSTORE_BASE64` | The PKCS12 keystore, base64-encoded |
| `AUTOSKIP_KEYSTORE_PASSWORD` | Its password (also the key's password; alias `autoskip`) |

Its certificate fingerprint is recorded in [`.github/release-cert-sha256`](.github/release-cert-sha256), and releases signed with anything else are refused.

Keep an offline backup of the key. GitHub never shows a secret again after you save it, and if the key is lost, no future version can install over existing ones: everyone would have to uninstall and reinstall. Never commit the key (`.gitignore` blocks `*.p12`, `*.jks` and `*.keystore`).

To build a signed APK on your own computer, decode the keystore to a file outside the repository and set `AUTOSKIP_KEYSTORE` (its path) and `AUTOSKIP_KEYSTORE_PASSWORD` before running `./gradlew assembleRelease`.

---

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
