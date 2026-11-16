# Magdi IPTV

Personal Android WebView IPTV player, built for this workspace. Package: `com.magdi.iptv`. Android 11 or newer. Independent from `com.magdi.tube`.

The first release combines the MIT-licensed IPTVnator embedded Java backend with a new Arabic/English web interface and selected lifecycle, diagnostics and experimental car integration components from the user's Magdi Tube project. It is not the full Angular IPTVnator application.

## Install

- `dist/Magdi-IPTV-0.1.5-personal.apk`: private first-run account provisioning, as requested. Keep this APK private: its provisioning asset contains the supplied account. The password can be changed in Settings. The installed account is encrypted using Android Keystore.
- `dist/Magdi-IPTV-0.1.7.apk`: identical application without account provisioning. Enter your own Xtream account or M3U URL.
- The clean 0.1.7 update and earlier personal APK share the package and signing certificate; installing the update preserves the saved account. Neither replaces Magdi Tube.

Open the app and tap **Connect subscription / اتصال بالاشتراك**. Settings support server address (including optional port), username and password, or a remote M3U URL. The configured account is never submitted to an app-operated server. Local storage contains favorites/history; M3U favorites may contain their stream URLs.

Live TV supports HLS and MPEG-TS through bundled HLS.js and MPEGTS.js. Auto tries HLS then TS. Movies and series support the formats/codecs available to the device's WebView. Unsupported codecs (for example some AC3/HEVC/MKV combinations) are not converted. No native transcoder, external player, DRM engine, recording or Stalker UI is included.

Tap a channel, movie or episode once to start it. Artwork, title and surrounding row spacing form one touch target; the favorite star remains separate. The selected row immediately shows a starting/playing/paused/error state, with a visible confirmation bar and a **Show player** button when browsing lower in the list. Selecting does not rebuild the list or automatically jump its scroll position. Repeated taps on the same starting/playing item do not reopen its stream; a paused item resumes. An unsuccessful item can be retried with one tap. Series dialogs show an explicit loading state while fetching episodes.

## Android Auto

The personal preview includes the same legacy projected WebView service approach used in Magdi Tube, a phone-only King Installer setup screen, a retained car document and a control-only audio media card. Choose content in Magdi IPTV before using its audio card. The application does not claim Google Play eligibility or guaranteed host acceptance. Car display discovery and the Honda hardware need a real-device test; ordinary Android installation alone does not establish car compatibility.

**Settings → King Installer / Android Auto** offers the built-in classic King method: copy the currently installed signed APK to private cache, open Android's system installer with installation-source hints, and observe the package update and source metadata afterwards. Android's normal install confirmation remains necessary. This method does not need the separate King Installer app.

The second button sends the same APK directly to the full King Installer app (`com.example.kinginstaller`) if installed. Otherwise it opens the upstream official releases page. This handoff provides access to methods supported by that external app; Root and Shizuku are not embedded here. A personal APK includes the first-run account seed, so the handoff explicitly confirms sending that file. See [integration details](licenses/KingInstaller-integration.md).

The keep-alive service never asks for a second audio-focus owner. Explicit Pause clears resume intent. Temporary coverage uses the existing Magdi Tube state tracker and bounded redraw/resume attempts. Process death cannot preserve a live WebView document.

## Diagnostics

Settings → **Copy diagnostics** copies the report as text; **Export diagnostics** shares it as a `.txt` file. Both options also appear in the King Installer setup screen and use the same report format: device/WebView versions, installation metadata and recent `phone` / `car` playback events. Copy completion is confirmed in the selected language. Long reports retain the header and newest 180,000 characters of events, with an explicit notice when older entries are omitted, so clipboard transfer stays within Android limits.

Diagnostics exclude provider URLs, account secrets, channel names and raw network exceptions. Frame/time observations do not independently verify audible output or display behavior on a vehicle.

## Build

For a fresh checkout: install JDK 17 and Android SDK API 36 / build-tools 36.0.0, configure `ANDROID_HOME` or a local `sdk.dir`, then run `./gradlew :app:assembleDebug :backend:check :app:testDebugUnitTest`. On Windows use `gradlew.bat`. This uses a development signing key when no private release key is supplied and does not require subscription credentials. The provisioning task removes any previous generated account asset on clean builds, including direct Gradle builds.

`./build.ps1 -Personal -Checks` builds signed release/debug APKs, executes the upstream backend checks and application tests, runs Android lint and verifies the APK signature. `./build.ps1 -Checks` builds the clean APK. The script uses the existing local JDK 17, Gradle 8.11.1, Android API 36 and build-tools 36.0.0 from the previous workspace, with a separate Gradle cache here. Change the tool path for another workstation. Private signing/account inputs are excluded by `.gitignore` and from source packages.

Third-party licenses are under `licenses/` and are shown in Settings. The backend source was downloaded from `4gray/iptvnator` master on 2026-11-15. See `VALIDATION.md` for what was actually checked and remaining limits.

The source archive excludes signing keys and provider credentials. For another workstation, set the tool paths in `build.ps1`, supply your own `.private/signing/magdi-release.p12` with alias `magdi-mirror` and its password in `.private/signing/store-password.txt`, or adapt the signing configuration to your key. A clean build needs no provider file. Personal provisioning optionally reads `.private/provider.json` with `mode`, `host`, `username` and `password`. The included legacy `aauto.aar` was carried over from the user's existing project for private testing; its public redistribution rights are not established. Review `licenses/` before public distribution.

The GitHub repository contains source and test fixtures. Its release APK is the clean variant. Personal APKs, signing keys, subscription data, generated build directories and local logs are excluded from Git.

Category, season, stream-format and source choices use embedded scrollable dialogs in 0.1.4 to avoid native Android popup windows in the projected host. See PICKER-FIX.md for diagnosis and test scope.

In 0.1.5 the fullscreen control toggles both into and out of fullscreen; the dedicated Exit fullscreen button and Back continue to work.

## Settings keyboard in 0.1.7
Tap a server, username, password or M3U URL field on the car to open the embedded keyboard. On the phone use the keyboard button beside a field, or type normally. Switch Arabic/English, case and symbols as needed. Done applies the text to that field; Cancel/Back discards the draft. Save the account using the existing Save button. Passwords are masked by default and can be explicitly revealed while editing.
