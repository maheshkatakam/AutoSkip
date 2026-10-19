# AutoSkip for Android

An Android app that presses the **Skip** button in the official **YouTube** and **YouTube Music** apps for you, as soon as the app shows it. It is the phone version of the idea behind the [YouTube Ad Auto-skipper](https://github.com/squgeim/yt-ad-autoskipper) browser extension: it does not block ads, it only presses the button the app already offers.

- Presses **Skip** the moment it becomes pressable, or after a delay you choose (0 to 10 seconds).
- Optionally **mutes** ads while they play, including ads that have no Skip button (such as audio ads in YouTube Music), and unmutes the moment Skip is pressed or the ad ends.
- Works in YouTube, YouTube Music or both: each has its own switch.
- Counts how many ads it has skipped, per app.
- Reads only those two apps, stores nothing it sees, and has **no internet permission**.
- **Free**, with no ads and no tracking. Open source under the [MIT licence](LICENSE).

Works on Android 8.0 and later. Built for Android 16 (API 36).

---

## Install guide

It takes about five minutes. You need:

- An Android phone running **Android 8.0 or later**.
- The official **YouTube** and/or **YouTube Music** app.

Menu names below are from Google Pixel phones; where Samsung or Xiaomi phones differ, it says so. If something on your screen doesn't match, see [Troubleshooting](#troubleshooting).

### Step 1. Download the APK

1. On your phone, open the [latest release](../../releases/latest).
2. Under **Assets**, tap **AutoSkip-v1.1.apk** (the number is the version, so it may be higher). Pick the file ending in `.apk`, not `.apk.sha256`. It's about 700 KB.
3. If your browser warns that this type of file can harm your device, tap **Download anyway**.

### Step 2. Allow installing from your browser or Files app

Android only installs apps from outside the Play Store if you allow the app you install from. You only do this once.

1. Open the downloaded file: tap the download notification, or open the **Files** app → **Downloads** → **AutoSkip-v1.1.apk**.
2. If Android says your phone isn't allowed to install unknown apps from this source, tap **Settings**, turn on **Allow from this source**, then go back.

### Step 3. Install

1. Tap **Install**.
2. Google Play Protect or your phone maker may step in. What you see depends on your phone and country:
   - **Play Protect offers to scan the app:** let it. If it then shows a warning, tap **More details** → **Install anyway**.
   - **"App blocked to protect your device"**, with only an **OK** button: in India, Singapore, Thailand, Brazil and some other countries, Play Protect blocks every downloaded app that uses Accessibility, whatever the app does. Either [install from a computer](#install-from-a-computer-instead) (recommended), or turn off scanning for a moment: **Play Store → your profile picture → Play Protect → ⚙️ (top right) → Scan apps with Play Protect: off**, install AutoSkip, then turn it **back on** straight away.
   - **Samsung, "Blocked by Auto Blocker":** **Settings → Security and privacy → Auto Blocker → off**, install AutoSkip, then turn Auto Blocker back on.
   - **"Unverified developer":** Google is phasing in developer verification (from 30 September 2026 in Brazil, Indonesia, Singapore and Thailand, everywhere in 2027). [Installing from a computer](#install-from-a-computer-instead) isn't affected. Android also offers an "advanced flow" for unverified apps, with a one-time 24-hour wait.
3. When it says **App installed**, tap **Open**.

### Step 4. Open AutoSkip

AutoSkip's screen shows a status card that says **Off**, and what AutoSkip can access: the screens of the YouTube and YouTube Music apps, nothing else, and it has no internet access. Below that:

- **Skip ads automatically** is on.
- Under **Use in**, both apps are switched on. One marked *(not installed)* isn't on your phone; that's fine.

### Step 5. Turn AutoSkip on in Accessibility settings

Android never lets an app switch this on for itself, so this one is up to you.

1. In AutoSkip, tap **Open Accessibility settings**.
2. Find AutoSkip in the list:
   - Pixel and most phones: under **Downloaded apps**.
   - Samsung: under **Installed apps**.
   - Xiaomi, Redmi, POCO: under **Downloaded apps** (it's **Settings → Additional settings → Accessibility** if you go there yourself).
3. Tap **AutoSkip** and turn on **Use AutoSkip** (on Samsung, the switch at the top).
4. Android asks *"Allow AutoSkip to have full control of your device?"* (on Android 11 and older: *"Use AutoSkip?"*). Tap **Allow** (or **OK**). Every accessibility app gets this same warning. What AutoSkip does with it is set out in [Free, private and offline](#free-private-and-offline-and-checked-on-every-build).
5. If there's also an **AutoSkip shortcut** switch, leave it off. You don't need it.

**If the switch is greyed out, or you see "Restricted setting"** (Android 13 and later, for apps installed from a download):

1. Tap **OK** on the "Restricted setting" message. Seeing it once is what makes the next option appear.
2. Go back to AutoSkip and tap **Open App info** (or open **Settings → Apps → AutoSkip**).
3. Tap **⋮** (top right) → **Allow restricted settings**, and confirm with your PIN, pattern or fingerprint. On Samsung, **Allow restricted settings** may be on the App info page itself: scroll down.
4. Go back to Accessibility settings and turn AutoSkip on again (steps 1 to 4 above). This time it works.

### Step 6. Let it run in the background

Some phones close background apps to save battery, which quietly stops AutoSkip. To stop that:

- **Pixel and most phones:** **Settings → Apps → AutoSkip → App battery usage → Unrestricted** (on some versions, turn on **Allow background usage** first).
- **Samsung:** **Settings → Apps → AutoSkip → Battery → Unrestricted**. Also make sure AutoSkip isn't listed in **Settings → Battery → Background usage limits → Sleeping apps** or **Deep sleeping apps**.
- **Xiaomi, Redmi, POCO:** **Settings → Apps → Manage apps → AutoSkip**: turn on **Autostart**, and set **Battery saver** (or **Battery**) to **No restrictions**.
- **Other brands:** look yours up on [dontkillmyapp.com](https://dontkillmyapp.com).

### Step 7. Check it works

1. Open AutoSkip. The status says **On** and *"Watching YouTube and YouTube Music"*.
2. Open YouTube or YouTube Music and play something until a skippable ad starts. When the Skip button appears, AutoSkip presses it within about a third of a second.
3. Back in AutoSkip, the count under **Skipped** has gone up.

You're done. The other settings are optional:

- **Press Skip as soon as it appears:** drag the slider under it if you'd rather see a few seconds of each ad first.
- **Mute ads while they play:** also silences ads that have no Skip button, such as YouTube Music's audio ads. YouTube shows captions while your phone is muted; if you'd rather it didn't, turn off **Show when muted** in YouTube's caption settings.
- **Debug logging:** only needed when [fixing AutoSkip after a YouTube update](#when-youtube-changes-its-ui).

### Updating to a new version

Download the new APK from the [latest release](../../releases/latest) and open it, as in steps 1 and 2. Android asks whether you want to update the app: tap **Update**. The warnings from Step 3 may appear again. Your settings and counts are kept, and AutoSkip stays switched on. If its status says **Turned on, but not running** afterwards, turn it off and on again in Accessibility settings.

### Uninstalling

**Settings → Apps → AutoSkip → Uninstall.** AutoSkip has no account and stores nothing anywhere else, so nothing is left behind.

### Install from a computer instead

Use this if your phone blocks the download (Play Protect, Auto Blocker or "unverified developer"), or if you'd simply rather. Apps installed this way also skip the restricted-settings part of Step 5.

1. On your computer (Windows, macOS or Linux), download Google's [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) and unzip them. Download the AutoSkip APK from the [latest release](../../releases/latest) into the unzipped `platform-tools` folder.
2. On your phone, turn on Developer options: open **Settings → About phone** and tap **Build number** seven times, then enter your PIN if asked.
   - Samsung: **Settings → About phone → Software information → Build number**.
   - Xiaomi, Redmi, POCO: tap **OS version** (or **MIUI version**) instead.
3. Turn on **USB debugging** in Developer options:
   - Pixel and most phones: **Settings → System → Developer options**.
   - Samsung: **Settings → Developer options**, at the bottom of the list.
   - Xiaomi, Redmi, POCO: **Settings → Additional settings → Developer options**. Also turn on **Install via USB** there (it asks you to sign in to a Xiaomi account).
4. Connect the phone with a USB cable. When it asks *"Allow USB debugging?"*, tap **Allow**.
5. Open a terminal in the `platform-tools` folder (on Windows, type `cmd` in the folder's address bar and press Enter) and run:
   ```bash
   adb install AutoSkip-v1.1.apk
   ```
   Use the name of the file you downloaded if it's a newer version. On macOS, Linux or in Windows PowerShell, type `./adb` instead of `adb`. It prints **Success** when it's done.
6. Carry on from [Step 4](#step-4-open-autoskip) on your phone. You can turn USB debugging off again afterwards.

### Troubleshooting

| What you see | What to do |
| --- | --- |
| *"App not installed as package conflicts with an existing package"* | The AutoSkip already on your phone was signed with a different key (an early test build, one from Android Studio, or a fork). Uninstall it, then install again. |
| *"There was a problem parsing the package"* | Your phone runs Android 7 or older, which AutoSkip doesn't support. |
| *"App blocked to protect your device"*, *"Blocked by Auto Blocker"* or *"unverified developer"* | See [Step 3](#step-3-install), or [install from a computer](#install-from-a-computer-instead). |
| The AutoSkip switch in Accessibility is greyed out, or *"Restricted setting"* | Follow the restricted-settings steps in [Step 5](#step-5-turn-autoskip-on-in-accessibility-settings). |
| No **Allow restricted settings** in the **⋮** menu | Try to turn AutoSkip on in Accessibility first and tap **OK** on the "Restricted setting" message: the option only appears after that. On Samsung, look on the App info page itself. If it still isn't there (some OnePlus phones), [install from a computer](#install-from-a-computer-instead), which doesn't need it. |
| AutoSkip isn't in the Accessibility list | Check it's installed (**Settings → Apps**), then look under **Downloaded apps** or **Installed apps** rather than the main list. |
| Status says **Turned on, but not running** | Android stopped it. Turn it off and on again in Accessibility settings, then do [Step 6](#step-6-let-it-run-in-the-background). Accessibility settings can still show it as on while it isn't running. |
| Status says **On**, but ads aren't skipped | Check that **Skip ads automatically** and the app you're using (under **Use in**) are switched on, and that AutoSkip and YouTube are both up to date. If they are, YouTube may have changed its buttons: see [When YouTube changes its UI](#when-youtube-changes-its-ui), or open an issue on this repository. |
| Captions turn on during ads | That's YouTube's **Show when muted** setting reacting to **Mute ads while they play**. Turn off one or the other. |

### Check a download is genuine (optional)

Every official release is signed with the same key, which is also what lets a new version install over the old one. To check a download really came from this repository, its signing certificate's SHA-256 fingerprint must be:

```
c5e60d4195e3fd86e3c4f407ec998d184a478b935770e7add7552afb50afeb62
```

On a computer with the Android SDK build tools:

```bash
apksigner verify --print-certs AutoSkip-v1.1.apk   # "certificate SHA-256 digest" must match the line above
```

Each release also has the file's own SHA-256 checksum in **AutoSkip-v….apk.sha256**.

---

## How it works

Android's **Accessibility API** lets an app read what is on screen in other apps and press buttons there. Screen readers such as TalkBack use it. AutoSkip uses it for two apps (YouTube and YouTube Music) and one button (Skip). YouTube Music plays its ads in the same video player as YouTube, so one set of rules covers both:

1. Android sends AutoSkip a notification whenever either app's screen changes.
2. AutoSkip reads the app's screen as a tree of labelled elements and looks for an ad (`Sponsored · 0:16`, `Ad 1 of 2`, "Your music will resume after this ad", view IDs like `ad_progress_text`) and for the Skip control (view IDs containing `skip_ad`, or labels like "Skip ad" / "Skip").
3. While the countdown is still running, it waits. Once the button is pressable, it presses it with `ACTION_CLICK`, the same action TalkBack uses.
4. While an ad is on screen it re-checks every 300 ms; with either app open and no ad, once a second; with neither on screen, never.

All the matching rules are in [`SkipDetector.kt`](app/src/main/java/io/github/autoskip/SkipDetector.kt), which has no Android code and is covered by unit tests. Safety rules built in:

- It never presses anything containing an advertiser link ("Visit advertiser", "Install", "Shop now"...). Pressing those would open the ad.
- It never presses an upsell such as "Skip ads with Premium" or "Try it free", and those never count as an ad either, so they can't trigger muting.
- It never presses a container bigger than a button.
- A bare "Skip" label only counts while something else on screen proves an ad is playing, so a video titled "Skip" is safe. A "Skip ad" label only counts if nothing but numbers follows it, so a song called "Skip Ads (Remix)" is safe too.
- It never presses a disabled control. YouTube Music greys out its track buttons during ads.
- A "Sponsored" label on its own is ignored, because promoted cards in the home feed, and YouTube Music's Now Playing banner, use it with no ad playing.
- If the app ignores the press, it falls back to a tap at the button, but never if anything is drawn over the button (YouTube sometimes slides an install card over Skip).

## Limitations

- **Skippable ads only.** It presses the button the app shows you; it can't shorten ads that have none. Muting is the most an outside app can do about those. Many YouTube Music ads are unskippable audio ads.
- **The official apps only:** YouTube (`com.google.android.youtube`) and YouTube Music (`com.google.android.apps.youtube.music`). Not YouTube in a browser or on TV.
- **The app has to be on screen.** If YouTube Music plays an ad while it's in the background or the screen is off, there's no Skip button on screen to press, and nothing to detect for muting.
- **YouTube Music is less tested.** It shares YouTube's player, so the same rules should apply, but its ad wording is less documented. If an ad isn't skipped or muted there, turn on Debug logging and follow [When YouTube changes its UI](#when-youtube-changes-its-ui).
- **Advanced Protection Mode (Android 17).** When that security mode is on, Android disables accessibility services that aren't declared assistive tools, which includes AutoSkip.
- **Needs maintenance** after some app updates (see [When YouTube changes its UI](#when-youtube-changes-its-ui)).
- **Captions and muting.** Since February 2026 YouTube turns captions on whenever your phone is muted (its **Show when muted** setting, on by default), and off again when the sound returns. With **Mute ads while they play** on, captions can therefore show during the ad. AutoSkip unmutes the instant it presses Skip, and as soon as an ad leaves the screen, so the video itself starts with sound. If captions still come on, turn off **Show when muted** in YouTube's caption settings. If they come on with AutoSkip's muting off, AutoSkip isn't involved: check YouTube's own caption settings (**Settings → Captions**) and Android's **Live Caption**.

## Free, private and offline, and checked on every build

An accessibility service is powerful: it can read the screen. So AutoSkip makes these promises, and the build enforces the technical ones:

- **No permissions at all, and never INTERNET.** Without the internet permission Android won't let the app open a network connection, so nothing it reads can leave the phone. [`verify-apk.sh`](.github/scripts/verify-apk.sh) inspects every APK the workflows build and fails the build if it requests *any* permission. A release can't be published without passing it.
- **YouTube and YouTube Music only.** It receives events from those two apps only, and re-checks the app of every window before reading it.
- **Keeps nothing.** It stores only its own settings and the skip counter.
- **Free, with no ads, accounts, analytics or tracking.**

If you fork this, keep it that way, and don't install builds of apps like this from people you don't trust. Check the [signing fingerprint](#check-a-download-is-genuine-optional).

## Rules worth knowing

- **YouTube's Terms of Service** forbid accessing the service "using any automated means" without permission. An app that presses Skip for you is automated interaction, even though it only presses a button you could press yourself. YouTube doesn't publish how (or whether) it enforces this against skip helpers, so use it knowing that. The fully sanctioned way to avoid ads is YouTube Premium.
- **Google Play won't accept it.** Play's Device and Network Abuse policy prohibits apps that "interfere with another app displaying ads", and Play's Accessibility API policy limits how automation apps may use the API. It is distributed through this repository's Releases page instead.
- **Licence.** MIT, see [LICENSE](LICENSE). The software comes with no warranty.

---

## Build the app yourself (optional)

Only needed if you want to change the code. Otherwise use the [Releases page](../../releases/latest).

### Option A: Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (free, Windows/macOS/Linux).
2. **File → Open** and choose this folder. Let Gradle sync finish. If it offers to install SDK Platform 36 or Build Tools 36.1.0, accept.
3. On your phone: **Settings → About phone →** tap **Build number** 7 times to unlock Developer options. Then **Settings → System → Developer options → USB debugging: on**.
4. Plug the phone in, accept the "Allow USB debugging?" prompt, choose your phone in the device list at the top of Android Studio, and press **Run ▶**.

Installing this way (over USB/adb) avoids Android's [restricted settings](#step-5-turn-autoskip-on-in-accessibility-settings) block. Android Studio signs with your computer's own debug key, so you'll need to uninstall that build before installing an official release, and the other way round.

### Option B: GitHub builds it for you

Every push runs the **Build APK** workflow: unit tests, a build, a check that the APK requests no permissions, and the [emulator test](#how-its-tested). Open the **Actions** tab, click the latest green run, and download **AutoSkip-apk** (GitHub asks you to sign in to download these). On this repository the APK is signed with the release key, so it installs over an official release. Forks get a throwaway key instead.

### Option C: Command line

Needs JDK 17+ and the Android SDK (platform 36, build-tools 36.1.0) with `ANDROID_HOME` set.

```bash
./gradlew :app:testDebugUnitTest   # run the unit tests
./gradlew :app:assembleRelease     # build app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without the release key (see [Maintainers](#maintainers)), `:app:assembleRelease` signs with your local debug key. The `:app:` part skips the test-only fake apps in `fakeapps/`.

## How it's tested

Every push runs three kinds of test on GitHub, and a red ❌ on a commit means one failed:

- **Detection rules** ([`SkipDetectorTest`](app/src/test/java/io/github/autoskip/SkipDetectorTest.kt)): fake screens, checking which button would be pressed, including every trap below.
- **Pressing and counting** ([`PressTrackerTest`](app/src/test/java/io/github/autoskip/PressTrackerTest.kt)): scan-by-scan timelines checking when Skip is pressed and that each skipped ad is counted exactly once, including a button that disappears 0.3 s after the press (the case version 1.0 got wrong, leaving the count at 0), flickers, retries and back-to-back ads.
- **Muting** ([`AdMuterTest`](app/src/test/java/io/github/autoskip/AdMuterTest.kt)): when the phone is muted and unmuted, including that Skip unmutes at once, a run of ads stays muted, a volume key wins, and a phone you muted yourself is never unmuted.
- **The real app on an emulator** ([`e2e.sh`](.github/scripts/e2e.sh)), on Android 11 and Android 16. Two small fake apps ([`fakeapps/`](fakeapps)) install under YouTube's and YouTube Music's package names and play fake ads: YouTube-style with view IDs and a countdown inside the button, YouTube Music-style with no IDs and "Your music will resume after this ad". Each app plays 3 skippable ads plus one unskippable ad, twice: once with **Mute ads while they play** off and once with it on. The test checks that every Skip was pressed, never during its countdown, that none of the traps was pressed ("Visit advertiser", "Skip ads with Premium", and a plain "Skip" button next to a song called "Skip Ads (Remix)" with no ad playing), that AutoSkip's saved counts, total and YouTube Music, match the skips that happened, and, with muting on, that every ad played muted and the sound was back within 0.3 s of each Skip and 0.7 s after the unskippable ad (so YouTube wouldn't start the video with captions). The results appear as notes on the workflow run.

To run the unit tests yourself: `./gradlew :app:testDebugUnitTest`.

## When YouTube changes its UI

YouTube and YouTube Music rename buttons and view IDs between app versions, so expect to update the rules now and then. Here's the routine (the same for both apps; log lines say which app they came from):

1. In AutoSkip, turn on **Debug logging**.
2. With the phone connected over USB, run `adb logcat -s AutoSkip`, then play videos until an ad appears. If an ad is on screen for 6 seconds with no Skip button recognised, AutoSkip prints every labelled element it sees once.
3. Or take a snapshot while the Skip button is visible:
   ```bash
   adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
   ```
   Search `ui.xml` for `skip` and look at the `resource-id`, `text` and `content-desc` of the button.
4. Add what you find to the lists at the top of `SkipDetector.kt`, add a test for it in `SkipDetectorTest.kt`, and run `./gradlew :app:testDebugUnitTest`.

**Other languages.** View IDs work in any language. Labels are English only out of the box, so if your phone is set to Hindi, Spanish and so on, and the ID is missing on your device, add your language's wording for "Skip ad" and "Ad" to the lists.

---

## Maintainers

### Publishing a new version

1. In [`app/build.gradle.kts`](app/build.gradle.kts), raise `versionCode` by 1 and set `versionName` (for example `1.2`).
2. Commit and push, and wait for the **Build APK** run to go green.
3. On GitHub: **Releases → Draft a new release → Choose a tag → type `v1.2` → Create new tag → Publish release**. Write what's new in the description if you like. (From a terminal: `gh release create v1.2 --title "AutoSkip v1.2" --notes "…"`.)

Publishing starts the **Release** workflow. It runs the tests, builds the APK, checks it requests no permissions and is signed with the release key, and attaches `AutoSkip-v1.2.apk` plus its checksum to the release, with install and verification notes added below yours. If the tag doesn't match `versionName`, nothing is attached and the run fails: delete the release and its tag, fix the version, and publish again.

### The signing key

The release key is never stored in this repository. It lives in two GitHub secrets (**Settings → Secrets and variables → Actions**):

| Secret | Contents |
| --- | --- |
| `AUTOSKIP_KEYSTORE_BASE64` | The PKCS12 keystore, base64-encoded |
| `AUTOSKIP_KEYSTORE_PASSWORD` | Its password (also the key's password; alias `autoskip`) |

Its certificate fingerprint is recorded in [`.github/release-cert-sha256`](.github/release-cert-sha256), and releases signed with anything else are refused.

Keep an offline backup of the key. GitHub never shows a secret again after you save it, and if the key is lost, no future version can install over existing ones: everyone would have to uninstall and reinstall. Never commit the key (`.gitignore` blocks `*.p12`, `*.jks` and `*.keystore`).

To build a signed APK on your own computer, decode the keystore to a file outside the repository and set `AUTOSKIP_KEYSTORE` (its path) and `AUTOSKIP_KEYSTORE_PASSWORD` before running `./gradlew :app:assembleRelease`.

### Keeping the workflows safe

On GitHub the key is only ever handed to the one build step that signs the APK ([`load-release-key.sh`](.github/scripts/load-release-key.sh)), and deleted straight after. Pull requests from forks never receive it. Every GitHub Action the workflows use is pinned to an exact commit, so a changed or compromised action can't quietly run next to the key; once a month Dependabot opens a pull request to update them, and it's safe to merge when its checks pass. The workflows use only open-source actions. Gradle's `setup-gradle` action is deliberately left out, because since version 6 its caching code is closed-source; `setup-java` caches Gradle's downloads instead, and Gradle's open-source `wrapper-validation` action checks `gradle-wrapper.jar`.

---

## Ideas for next steps

- A Quick Settings tile to pause skipping.
- A Firefox for Android add-on for `m.youtube.com`, for people who watch in the browser.
- A Safari Web Extension for iPhone (iOS doesn't let apps control the YouTube app, but Safari extensions can work on `m.youtube.com`).

## Credits

- Idea: [squgeim/yt-ad-autoskipper](https://github.com/squgeim/yt-ad-autoskipper) (GPL-3.0). No code is copied from it.
- YouTube view IDs (`skip_ad_button`, `ad_progress_text`, `player_learn_more_button`) and lessons about feed "Sponsored" cards were learned from [Javid-Git/ad-skipper](https://github.com/Javid-Git/ad-skipper) (MIT). That YouTube Music uses the same `skip_ad_button` ID, and the `ad_skip_button` variant, come from [wolverinesingh1-droid/skipper-android](https://github.com/wolverinesingh1-droid/skipper-android) (MIT). The code here is written separately.
- The Gradle wrapper (`gradlew`, `gradle/wrapper/`) is part of Gradle, Apache-2.0.

Not affiliated with YouTube or Google. "YouTube" is a trademark of Google LLC.
