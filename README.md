# Magdi IPTV

Android IPTV player for your own Xtream subscription or M3U playlist. The app supports Arabic and English, favorites, and playback history. Package: `com.magdi.iptv`. Requires Android 11 or later.

## Install and set up

1. Download the APK from [GitHub Releases](https://github.com/moustafamagdi/MagdiIPTV/releases).
2. Open the app and select **Connect subscription**. Enter your Xtream server address, username and password, or supply an M3U URL.

The release contains no subscription. The app stores your account on the device using Android Keystore and connects to your configured provider. Updates signed with the same certificate can be installed over the existing app to retain settings and favorites.

Live playback supports HLS and MPEG-TS. Movie and series playback depends on the formats and codecs supported by the device's Android WebView.

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
