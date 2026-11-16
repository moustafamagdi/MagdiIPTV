# Magdi IPTV — validation

## 0.1.3 — single-tap playback selection

Follow-up reproduction of the user's Endgame screenshot: the clean 0.1.2 APK's web assets were loaded into the local synthetic test fixture. A touch on the movie artwork left `current.type=live` and the old channel title while CSS highlighted the movie row; touching its text button switched to the movie. This reproduces the screenshot's visible symptom, but does not establish which APK was installed on the user's phone. On current 0.1.3 assets, four additional checks passed: one movie-artwork tap switched both playing and paused live channels to the movie immediately, and the movie decoded new frames in both cases. The test deliberately reused a live stream ID for the movie to verify that type-aware selection prevents a false duplicate. No further application-code change was needed for this reproduction. Scripts: `tests/selection-repro.mjs`, `tests/live-to-movie.mjs`; the fixture server optionally accepts a web-assets directory for version comparisons.

Channel/movie/episode artwork and row padding now belong to the same native button as the title. Pointer-down adds immediate visual feedback; moving more than 10 pixels, cancelling, releasing or leaving clears it. Playback still starts only from the normal click event, so scrolling does not trigger playback. Touch-only devices no longer depend on hover styling.

Selection updates existing rows rather than replacing the list DOM. Starting/playing/paused/failure states appear in the selected row and confirmation bar. Repeated selection of the same starting/playing item does not issue another stream request; a paused selection resumes. A failed item remains retryable. Series dialogs explicitly show loading, retry and empty states and reject responses for an obsolete dialog request.

Validation: **15 CDP emulated-touch checks passed**, including artwork/padding taps, touch-down feedback, stable row identity, duplicate-tap suppression, independent favorites, paused-item resume, series/episode selection, failure/retry, and scrolling without accidental activation. **13 browser playback checks passed**, including decoded HLS/TS/MP4 frames, VOD resume and series enumeration. Responsive checks passed at 412 × 915 and 1000 × 500. Screenshots reviewed for selected-row and confirmation-bar layout. These tests use synthetic fixtures in Chrome; they do not establish physical Samsung or Android Auto touch behavior.

Android tests: 23 passed. Backend checks: 61 passed. Release lint: 0 errors, 14 warnings. Both variants built and signed as version code 4 / `0.1.3-preview`. Evidence: `logs/touch-flows-0.1.3.json`, `logs/browser-flows-0.1.3.json`, `logs/selection-mobile.png` and Gradle reports. Personal provisioning stays local; the GitHub release is clean.

## 0.1.2 — copy or export diagnostics

Added separate copy/export actions in both web settings and native King Installer setup. Both paths use one report builder. Reports retain device and installation details plus the newest 180,000 characters of events; an explicit marker identifies any omitted older entries. Clipboard access and confirmation run on Android's UI thread after report preparation finishes off that thread.

Validation: 23 Android tests passed, including three new checks that compare actual Android clipboard content with the prepared report, compare the exported file with the same builder and verify a read-only share intent, and check that long Unicode logs retain the header/newest events within the clipboard budget. All 61 backend checks passed. These clipboard/share checks use Robolectric API 32; clipboard interaction has not yet been tested on the user's Samsung phone. Existing playback behavior is unchanged.

Version code 3 / `0.1.2-preview`, signed with the existing personal certificate. The GitHub APK is clean; the personal APK remains local. Artifact and tracked-source privacy scans are required before publication.

## 0.1.1 — King Installer and GitHub delivery

Completed 16 November 2026. Both signed variants built successfully. Backend: 61 checks passed. Android tests: 20 passed (the previous 15 plus 5 installer integration checks). Release lint: 0 errors, 14 warnings.

The Arabic King Installer setup screen was opened from the actual application's WebView on the Android 16 emulator. Its built-in button opened the Android system update dialog. Confirming that dialog completed a same-version, same-signature reinstall, and the saved account/password remained configured. The app observed the update and correctly reported that installation-source metadata did **not** match the desired Play Store source on this AOSP emulator: both fields were `com.android.packageinstaller`. This verifies the reinstall flow and honest result reporting, not Android Auto acceptance. Evidence: `logs/king-setup.png`, `logs/king-install-dialog.png`, `logs/king-result.png`.

Unit checks cover the APK URI grant, source hints, an explicitly targeted King Installer handoff, rejection of shell as matching metadata, distinguishing a completed dialog from an observed reinstall, and avoiding third-party handlers in the classic system-install path. The full King Installer application and its Root/Shizuku methods were not exercised on this emulator.

The selected web-interface language is passed to the native setup screen. Both APKs use version code 2 / `0.1.1-preview` and the existing personal signing certificate. The repository and GitHub release use the clean variant only; personal APKs remain local. Tracked files and packaged artifacts are scanned for supplied account values and private signing material before upload.

## 0.1.0 playback baseline

Completed 16 November 2026 (Asia/Riyadh). These results distinguish synthetic playback tests from the user's actual subscription and vehicle.

## Build and automated checks

- Signed release and debug builds completed for both personal and clean variants with JDK 17 / Gradle 8.11.1 / Android Gradle Plugin 8.9.1.
- Android API 36 target; minimum API 30 (Android 11); application ID `com.magdi.iptv`.
- IPTVnator Java backend: **61 passed, 0 failed**, including comparison with 43 M3U fixtures.
- Android unit/lifecycle/proxy tests: **15 passed, 0 failed** (8 lifecycle, 7 proxy). Checks cover retained car WebView, pause intent, audio-focus behavior, media controls, range forwarding, HLS rewriting, bounded URL retention, invalid schemes and HTML rejection.
- Android release lint: **0 errors, 16 warnings**. Warnings include intentionally retained legacy support-library versions, synchronous preference commits and manifest compatibility attributes; storage-permission removal directives also trigger lint warnings.
- Both APK signatures verified with `apksigner`. Release WebView debugging is disabled by the non-debuggable release application flag.

## Installed Android application

Test device: isolated Android 16 / API 36 x86_64 emulator, 720 × 1280 physical pixels. Final release APKs both installed successfully and opened with a cold launch. Clean first run opened empty account settings; personal first run opened the ready-to-connect welcome screen. Application data was reset only in this emulator between first-run checks.

The debug build of the same application source was tested through its actual WebView, Java bridge, encrypted account store and intercepted media requests, against a local synthetic Xtream/M3U fixture. All 13 main checks passed:

- Live categories/catalog, Arabic search and persisted favorites.
- HLS playback: 86 decoded frames at approximately 3.45 seconds.
- Explicit pause, resume and fullscreen.
- MPEG-TS playback: 32 decoded frames; live latency adjustment advances into the finite test fixture.
- Movie catalog and MP4 playback: 99 decoded frames at approximately 3.93 seconds.
- VOD resume: playback advanced beyond the saved 12-second point to approximately 14.66 seconds, with 87 decoded frames. An initial Android AAC decoder failure on immediate metadata-time seeking was fixed by waiting for playback to advance before applying the saved position.
- Series season/episode enumeration and no horizontal page overflow.

Six additional native checks passed: switching while EPG is delayed, replacement stream playback, M3U import/playback, rejection of invalid credentials, and no password field returned to page JavaScript. Background playback continued from 3 to 6 seconds after the Android Home action, with 167 decoded frames. A separate observation showed increasing audio decoded-byte counts. An ADB screen capture visibly confirms the synthetic color/motion video on the Android display; decoded bytes alone are not an audible-output test.

Browser layout checks passed in portrait (412 × 915) and landscape (1000 × 500), including settings, car search keyboard and fullscreen height. The portrait fullscreen inherited-height issue was corrected.

Evidence in this workspace: `logs/android-flows.json`, `logs/android-extra.json`, `logs/android-background.json`, `logs/android-screen.png`, `logs/release-clean.png`, `logs/release-personal.png`, and Gradle test/lint reports. `dist/verification.json` records final artifact sizes, SHA-256 values and private-data checks.

## Limits requiring a real device/network

- The supplied provider returned a network **Web Filter Violation** page from this development machine. This prevented verification of the real subscription, real catalog and real channels. No successful authentication against that provider is claimed. The personal APK provisions the supplied settings and can be tested on the phone's network.
- Android Auto discovery, self-install handling, touch/video/audio behavior on the user's vehicle, and return from vehicle overlays have not been tested on physical hardware. The implementation carries over Magdi Tube's legacy projection integration and lifecycle approach; automated lifecycle tests do not establish host acceptance.
- Playback uses device WebView codecs. Synthetic H.264/AAC success does not establish compatibility with every provider codec, DRM or container. There is no transcoder.
- Playback checks are short functional tests, not an overnight soak or network-roaming test.
- The personal APK intentionally includes a plaintext first-run provisioning asset as authorized. Installed account storage is encrypted. The clean APK and source archive must pass the private-value scan before delivery. Updating an existing installation preserves its saved account.

## 0.1.5 fullscreen control

The user confirmed car channel playback, Next, Previous and Pause, and reported that the fullscreen icon entered fullscreen but could only be exited through the separate Exit fullscreen button. The handler always requested fullscreen=true. It now toggles the current state and exposes aria-pressed; the dedicated exit and Back still work.

Nine focused checks in tests/fullscreen-toggle.mjs passed using Chromium touch at 1000 x 500 and 412 x 915: entry, same-button exit, dedicated exit, Back, and continued playback. These are local synthetic playback checks, not a physical car retest. The 0.1.4 embedded-picker change is retained.

## 0.1.6 audio icon
The media browser service now has a dedicated gold headphones icon with a white
TV mark. The video/projection and phone application keep the existing green TV icon.
Both services remain enabled. Playback and account handling are unchanged.
Signed clean release/debug builds, backend checks, app tests and release lint passed.
The clean APK has no account seed or supplied private provider values. Host launcher
appearance and cache refresh remain a device check. No personal APK was rebuilt
for this icon-only update; the clean APK can update an existing personal installation
without deleting its saved account.
