# Fullscreen picture fit — 0.1.27

Enter Car fullscreen, then choose **Fill screen / بدون حواف** on the phone or car. Choose **Full picture / الصورة كاملة** to restore the full frame. Fill crops the edges while preserving aspect ratio. It does not stretch the picture or hide Android Auto's system navigation area. It cannot automatically remove black bars encoded inside the stream.

The footer now reads the installed app version instead of displaying an old hard-coded number.

This is presentation-only: no source replacement, seeking, play/pause commands or audio-focus changes. The phone stays a controller. Phone commands use existing player/connection/media-request validation and fresh capability reports; stale, disconnected and invalid commands are rejected. The Fit button is separate from the VOD seek controls so live channels can use it too. In the normal car fullscreen layout, it hides with the existing controls and returns on touch. Native WebView video fullscreen also exposes a Fit/Fill toggle.

Validation on 2026-11-22:

- Release assembly, Android lint and 150 Robolectric/JVM tests passed, including new capability/value/stale-request/Stop/disconnection coverage.
- `tests/fullscreen-fill-native.mjs` passed in API 36 emulator Android WebViews with synthetic media: actual phone buttons, cover/contain at 960×540, 1280×480 and 480×800, unchanged video/source/mute, preserved pause position, advancing decoded frames, car-local toggle, native fullscreen, exit, Stop and no phone playback. Set `TEST_APP=iptv`, use CDP 9225 and the mock server on 8787 with `CONTROLLER_MEDIA=1`.
- `tests/fullscreen-layout-native.mjs`: 25 checks passed, including Arabic/English, viewport coverage, reachable controls, modal dismissal, progress and native/remote exit.
- `tests/fullscreen-autohide-native.mjs`: 16 checks passed with the long synthetic fixture, including the ten-second timer, one-touch reveal, pause/buffering, seek holds, keyboard accessibility, phone control/progress and cleanup.
- `tests/video-health.mjs`: 16 checks passed.
- Package identity and signing certificate remain unchanged. The release excludes account seeds and debug fixture assets.

The new Fit/Fill option has not been tested against a real IPTV provider or on a physical Android Auto head unit in this release. Emulator results are not proof of physical car compatibility. Prior density/viewport fixes remain included.
