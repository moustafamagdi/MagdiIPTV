# MM IPTV

Android Auto IPTV player with a phone controller, for your own Xtream subscription, M3U playlist, or optional free public channel directories. The app supports Arabic and English, favorites, and playback history. Package: `com.magdi.iptv`. Requires Android 11 or later.

## Install and set up

1. Download the APK from [GitHub Releases](https://github.com/moustafamagdi/MagdiIPTV/releases).
2. Choose **Free public channels** to browse without an account, or **Connect subscription** to enter your Xtream server address, username and password / M3U URL.

3. Connect Android Auto and open **MM IPTV** on the car screen. Choose content from the phone library to play it there.

The phone is a controller only: it does not play video or audio. The release contains no subscription. The app stores your account on the device using Android Keystore and connects to your configured provider. Updates signed with the same certificate can be installed over the existing app to retain settings and favorites.

Live playback supports HLS and MPEG-TS. Movie and series playback depends on the formats and codecs supported by the device's Android WebView.

## Phone controller

Keep MM IPTV open on the car screen. The phone shows whether the car player is ready, the current title and playback state. Browse channels, movies and episodes on the phone, then tap once to send your selection. Separate Play and Pause buttons follow observed car playback: Play is dimmed while playing, Pause is dimmed while paused, and both are disabled before readiness or after disconnection. Buffering preserves Pause; ended content offers Play; errors offer Retry. Seeking and next/previous are enabled only when the current player and queue support them. Stop, next/previous, movie seeking, retry and car fullscreen are available from the phone.

Without an available car player, selecting content displays a connection notice. Selections are not queued for later. Disconnecting or closing/leaving the car player stops playback and cancels pending selections; reconnecting does not resume automatically. Closing the phone interface does not stop an available car player. Phone browsing can use a different library while the controller continues to show what is playing on the car.

Fullscreen from the phone keeps only the car player and its own transport bar visible. Library tiles, episode Play buttons and page controls are hidden and inert until exit. The video element stays in place; entering/exiting does not reload or resume it. A small circular close button on the physical left edge exits both remote and native fullscreen; the phone fullscreen button also exits a native video overlay.

## Seeking and ten-second controls

Movies and episodes have Back 10 s / Forward 10 s controls on the phone and car, alongside the seek bar. They are disabled when no seekable car player is available and hidden for live channels. Seeking preserves Pause, later seeks replace earlier targets, and an explicit seek cancels an older saved-position resume. Phone controls never play local media.

A seek that stays unresolved for 18 seconds reloads once at the selected position. Another 18-second timeout stops that media load and offers Retry. Stop, a new selection or disconnection cancels pending recovery. A source that cannot supply the requested position shows a clear notice instead of silently clamping the jump or waiting forever.

HTTP byte ranges need special handling in the intercepted WebView stream: [Chromium's input-stream reader](https://github.com/chromium/chromium/blob/main/components/embedder_support/android/util/input_stream_reader.cc) seeks the Java stream by the original offset. Our upstream HTTP 206 body already begins there, so the adapter credits that initial skip instead of discarding the offset twice. Network buffer availability is not treated as the whole file size. Invalid/mismatched or ignored nonzero ranges are rejected with a fixed diagnostic reason, without logging addresses.

Run the throttled synthetic range fixture with `node scripts/seek-fixture.mjs` (port 8794), then `tests/seek-native.mjs` against the debug controller harness. Generate its local media first as described in `tests/media/README.md`. These tests exercise jumps outside the downloaded buffer, both fullscreen modes, rapid seeks, failure/retry and cancellation. Provider codec/range support and physical Android Auto still require device testing.

## Frozen-video recovery

The car player watches presented-frame progress (total frames minus dropped frames) every two seconds. If the media clock advances but video frames stay frozen for 20 seconds, it shows a recovery state on the car and phone and reloads the current stream. Movies and episodes resume near the last position where frames progressed; live channels reconnect without seeking. The same video element and native controller request are retained, including fullscreen.

Recovery is limited to two attempts per selection. A failed reconnect or repeated freeze stops with a Retry message; it does not loop indefinitely. Pause, Stop, a new selection and disconnection cancel applicable work. Detection ignores paused, seeking, ended, audio-only, hidden and inactive players, and restarts its observation window after long timer gaps. It does not compare image pixels: static shots with advancing video frames are healthy. Detection requires supported frame counters and cannot guarantee recovery from provider, network or device decoder faults.

Diagnostics record only fixed recovery event names, attempt counts and existing numeric media samples. No stream addresses or account information are added. Run `node --test tests/video-health.mjs` for detector tests (also included in `build.ps1 -Checks`), and `tests/video-recovery-native.mjs` against the debug harness for synthetic fault-injection coverage. Physical Android Auto/Samsung verification remains necessary.

## Posters, channel logos and descriptions

Xtream libraries display provider-supplied channel logos and movie/series posters. Personal and public M3U lists also display `tvg-logo` artwork. Images load as they approach the visible area, are resized and temporarily cached in memory, and keep a placeholder if absent, unsupported or unavailable. Supported artwork is HTTP/HTTPS raster imagery (such as PNG, JPEG and WebP); the app does not search third-party databases for missing images.

Tap a movie card to see its poster, description and any supplied year, rating, genre, duration, director or cast. **Play on car screen** sends the selection through the existing phone controller. Opening details alone never starts playback. Series descriptions appear above the season/episode list; **ⓘ** beside an episode opens its supplied image and description. Live channels still play with one selection. Missing or failed metadata does not prevent trying playback.

Artwork has a separate image-only native path with bounded downloads and caching; the phone's video/audio restrictions remain in force. Provider descriptions are displayed as text, not executable HTML. Artwork addresses and content metadata are not added to diagnostic logs.

## Free public channels

Choose **Free public channels** on the welcome screen or above the library, select a list, then **Open channels**:

- **Worldwide — Free-TV:** [Free-TV/IPTV](https://github.com/Free-TV/IPTV), grouped by country.
- **Egypt — iptv-org:** [Egypt streams](https://github.com/iptv-org/iptv/blob/master/streams/eg.m3u).
- **Saudi Arabia — iptv-org:** [Saudi streams](https://github.com/iptv-org/iptv/blob/master/streams/sa.m3u).

These independent directories are downloaded from GitHub when opened. The app streams directly from the listed hosts; it does not host or redistribute video. Availability and geographic restrictions depend on each broadcaster. Inclusion in a directory is not a blanket license to redistribute its content. Unsupported addresses and duplicate stream URLs are omitted; some remaining streams may still be unavailable or need codecs the device does not support.

Use **↻** to fetch a fresh copy. A failed refresh leaves the previous library available and offers a retry. Each public list has separate favorites and history. **My subscription** returns to the saved account without replacing its credentials or favorites. Public directories provide live channels, not a movie or series subscription.

## Android Auto

Android Auto projection uses a legacy integration and compatibility varies with the host and device. Settings includes a King Installer setup screen for the current release. The separate **MM IPTV Audio** entry controls content already selected in the app.

On the phone, open **Settings → Log sessions** (also available from the installation setup screen). Select a session and part, then **Copy selected part** or **Export selected part**. Both include the complete selected part and the same diagnostic header, without truncation. They use a snapshot captured when the part is selected; **Refresh list** loads newer events. Account credentials, provider addresses, stream URLs, tokens and channel names are excluded.

**Start new session** starts a manual log without deleting older sessions or changing playback. App processes and car connections start sessions automatically. Car reconnections within five minutes stay in the same session; temporary screen covers such as the camera do not create sessions. The current part is selected by default, with session type, date, time, part number and size displayed.

The store keeps the latest **40 parts** for at most **7 days**, deleting the oldest when either limit is reached. Each part is at most **128 KiB**, preserving complete event lines and UTF-8 Arabic text. File work runs on one background queue to preserve event order. Existing logs are imported once; originals are only removed after the import commits successfully. Exported files saved outside the app are not deleted by log retention.

Settings and other dialogs contain scroll gestures at their edges. The background page stays locked while any dialog remains open, including a keyboard or source picker over Settings; closing the last dialog restores normal page scrolling at its existing position.

## Embedded keyboard

The Arabic layout includes all 28 base letters, including د and ذ, plus ء، أ، إ، آ، ؤ، ئ، ة، ى. A regression test checks insertion, touch activation and reachable keys at narrow and landscape sizes in both interface languages (`tests/arabic-keyboard-native.mjs`).

On the car, tap the server address, username, password or M3U URL field to open the embedded keyboard. On the phone, use the keyboard button beside a field or type normally. Arabic/English, uppercase/lowercase, digits and symbols are available. Done applies the draft to the field; Cancel or Back discards it. Use the existing Save button to save the account. Passwords are masked by default with an optional reveal button.

## Build and test

From **0.1.26**, the projected car WebView starts at the car host's display density, avoiding a small top-left page when Chromium has initialized with the phone's density. A real car-service disconnect invalidates the old view; the next car attachment builds a fresh local player page. Temporary covers in the same connection retain the existing session. Phone scaling and controller-only playback restrictions remain unchanged. See [verification](research/CAR_VIEWPORT_0.1.26.md).

Use JDK 17 and the Android SDK with API 36 and Build Tools 36.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`. Run:

```sh
./gradlew :app:assembleDebug :backend:check :app:testDebugUnitTest
```

On Windows, use `gradlew.bat`. The development build requires no subscription credentials. Browser and emulator tests use the synthetic fixtures under `tests/`, including regression tests for touch selection, in-app pickers, settings keyboard and fullscreen. The debug APK includes `ControllerHarnessActivity` with two real WebViews for controller integration tests; it is excluded from the release APK. With the fixture server running, launch that activity, forward its WebView debugger to port 9225, set `ADB` to the adb executable, and run `node tests/controller-native.mjs`. The harness verifies routing, phone media blocking, playback controls, disconnect/reconnect, canceled requests, latest selection and personal M3U. Physical Android Auto compatibility still needs validation on the target car.

The controller uses native player, connection and media-request generations plus ordered reports. Missing native samples recover after six seconds; phone polling recovers within three seconds. Old callbacks cannot replace newer playback or release newer polling slots. No URL fragment binding is used for the owned IPTV page. Diagnostic session storage, copying/export and retention are unchanged.

Run `node tests/controller-controls-native.mjs` for actual phone-button enablement, dimming, loading/buffering/error/ended, seek, CSS/native fullscreen progress, stale callbacks, reconnect without autoplay, polling faults and phone closure. Set `CDP_PORT`, `FIXTURE_PORT`, `ADB_PORT` and `ADB_DEVICE` for isolated instances. Start the fixture server with `CONTROLLER_MEDIA=1` to use the generated 20-second H.264/AAC clip `tests/media/controller.mp4`. The older clip produced an emulator audio-decoder failure after a long seek; this does not establish real-provider codec compatibility. Synthetic buffering and missing-callback faults are explicitly injected by the test.

Third-party notices are in [licenses/](licenses/) and in the app's Settings.

Fullscreen layout regressions: `tests/fullscreen-layout-native.mjs` uses the debug harness to size the actual car WebView at 960×540, 800×480, 1280×480 and 360×780. It checks viewport coverage, hit targets, hidden/inert browsing controls, Arabic/English exit labels, modal dismissal, unchanged media element, paused-state preservation, progress, native/remote exit and Stop. Use an isolated emulator large enough to host those test views (2560×1000 at density 160).

Fullscreen controls automatically hide after 10 seconds without interaction while video is playing. One touch reveals the transport bar and exit button without pausing or activating a hidden button. Controls stay visible while paused, buffering, seeking, or holding a pointer; keyboard-focused controls remain accessible. Normal embedded playback and the phone controller do not auto-hide. `tests/fullscreen-autohide-native.mjs` verifies the real timer, touch reveal, remote controls, input holds, focus and cleanup using Android WebViews and synthetic media. Native video fullscreen continues to use WebView-owned controls.
Use the 180-second fixture described in `tests/media/README.md` with `scripts/seek-fixture.mjs` on port 8794 for the auto-hide timer tests.

If resuming a saved film/episode position fails, Retry attempts that position again. Start from beginning appears on the phone and car (including fullscreen); only that explicit action clears the selected title's saved position. Automatic failure does not erase it or silently restart the film. Seek-start diagnostic code 1 identifies saved-position resume, 2 an app seek control, and 0 a native video seek. Nonzero HTTP Range diagnostics contain only sequential request numbers, status, timings, byte counts and fixed outcomes; no URLs, headers, body contents or exception messages are logged. This improves diagnosis and provides a way out of failed resume; it does not establish why a particular provider cannot deliver the saved position. Run `tests/resume-failure-native.mjs` against the long synthetic seek fixture.

## Simple support reports

The phone now opens **Support & diagnostics** with an installation Support ID, consent-based automatic reports, Send report now, deletion, and simple manual sharing. Previous complete local sessions remain under Advanced during rollout. See [support and privacy](SUPPORT.md).

## Shared Support ID (0.1.21)

The updated MM apps can show one random Support ID on the same Android profile through MM Auto. Updates preserve data; linking keeps old installation IDs searchable. Consent remains separate per app, with renewed consent for shared-ID reporting. Settings now group secondary troubleshooting tools under Advanced. See [support guide](SUPPORT.md) for setup, privacy, deletion and test limitations.
