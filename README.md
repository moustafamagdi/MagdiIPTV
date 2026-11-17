# Magdi IPTV

Android IPTV player for your own Xtream subscription, M3U playlist, or optional free public channel directories. The app supports Arabic and English, favorites, and playback history. Package: `com.magdi.iptv`. Requires Android 11 or later.

## Install and set up

1. Download the APK from [GitHub Releases](https://github.com/moustafamagdi/MagdiIPTV/releases).
2. Choose **Free public channels** to browse without an account, or **Connect subscription** to enter your Xtream server address, username and password / M3U URL.

The release contains no subscription. The app stores your account on the device using Android Keystore and connects to your configured provider. Updates signed with the same certificate can be installed over the existing app to retain settings and favorites.

Live playback supports HLS and MPEG-TS. Movie and series playback depends on the formats and codecs supported by the device's Android WebView.

## Free public channels

Choose **Free public channels** on the welcome screen or above the library, select a list, then **Open channels**:

- **Worldwide — Free-TV:** [Free-TV/IPTV](https://github.com/Free-TV/IPTV), grouped by country.
- **Egypt — iptv-org:** [Egypt streams](https://github.com/iptv-org/iptv/blob/master/streams/eg.m3u).
- **Saudi Arabia — iptv-org:** [Saudi streams](https://github.com/iptv-org/iptv/blob/master/streams/sa.m3u).

These independent directories are downloaded from GitHub when opened. The app streams directly from the listed hosts; it does not host or redistribute video. Availability and geographic restrictions depend on each broadcaster. Inclusion in a directory is not a blanket license to redistribute its content. Unsupported addresses and duplicate stream URLs are omitted; some remaining streams may still be unavailable or need codecs the device does not support.

Use **↻** to fetch a fresh copy. A failed refresh leaves the previous library available and offers a retry. Each public list has separate favorites and history. **My subscription** returns to the saved account without replacing its credentials or favorites. Public directories provide live channels, not a movie or series subscription.

## Android Auto

Android Auto projection uses a legacy integration and compatibility varies with the host and device. Settings includes a King Installer setup screen for the current release. The separate **Magdi IPTV Audio** entry controls content already selected in the app.

Settings can copy or export playback diagnostics without including account credentials, provider URLs or channel names.

## Embedded keyboard

On the car, tap the server address, username, password or M3U URL field to open the embedded keyboard. On the phone, use the keyboard button beside a field or type normally. Arabic/English, uppercase/lowercase, digits and symbols are available. Done applies the draft to the field; Cancel or Back discards it. Use the existing Save button to save the account. Passwords are masked by default with an optional reveal button.

## Build and test

Use JDK 17 and the Android SDK with API 36 and Build Tools 36.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`. Run:

```sh
./gradlew :app:assembleDebug :backend:check :app:testDebugUnitTest
```

On Windows, use `gradlew.bat`. The development build requires no subscription credentials. Browser and emulator tests use the synthetic fixtures under `tests/`, including regression tests for touch selection, in-app pickers, settings keyboard and fullscreen.

Third-party notices are in [licenses/](licenses/) and in the app's Settings.
