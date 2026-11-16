# 0.1.4 category picker repair

The user confirmed that 0.1.3 opens in Android Auto, but opening the series category dropdown exits the application and Android reports a bug. The supplied report shows car page creation followed by two process restarts, without a fatal exception stack.

All four native HTML select controls are now hidden and represented by buttons opening an HTML dialog inside the WebView. This avoids Android select popup windows in the projected host. Native-popup incompatibility is the working diagnosis, not a verified exception: Chromium uses platform dialog/dropdown implementations for select elements. Source: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/input/SelectPopup.java

The original select elements remain as the value/event model. The replacement copies option labels as text, supports disabled choices, tracks dynamic options, dispatches one change only when the choice changes, and handles nested season/settings dialogs with topmost-first Back behavior.

Regression flow: tests/picker-flows.mjs. It uses real emulated touch in desktop Chromium and DOM activation in an Android WebView; neither is a physical Android Auto test. Existing playback and touch regression flows remain applicable. Actual check results are reported with the delivered artifact.

The earlier Endgame screenshot was ultimately confirmed by the user to be from an older app version; it is unrelated to this newly reported car dropdown crash.

## Completed validation (16 November 2026)

- Signed personal and clean release APKs built successfully; same signing certificate as 0.1.3.
- 61 backend checks and 23 Android unit/lifecycle/proxy/installer/diagnostics tests passed.
- 13 picker regression checks passed with Chromium landscape touch at 1000 x 500, including a 400-category mixed Arabic/English list.
- The same 13 picker checks passed through the installed Android 16/API 36 app WebView and Java bridge using DOM activation.
- An additional ADB touch opened the series category list and selected an item in the Android emulator. The label updated, the picker closed, and process ID stayed 1972. Screenshots: logs/pickers-native-open.png and logs/pickers-native-selected.png (local evidence, not uploaded).
- Existing 13 playback checks and 15 touch-interaction checks passed in Chromium; synthetic HLS, TS and MP4 decoded frames.
- No physical vehicle retest or crash stack is available. These results validate the replacement path; they do not prove the Samsung/Android Auto crash is resolved.
