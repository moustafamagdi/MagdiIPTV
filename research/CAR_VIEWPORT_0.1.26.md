# Car viewport correction — 0.1.26

The report showed the owned IPTV HTML page confined to a small top-left rectangle in the 800 x 400 car content area. This matched the density mismatch reproduced and corrected in MM Tube.

PlayerView now sets a car-only initial physical WebView scale using the host context's density. Phone WebViews retain their default scale. No YouTube document helpers, URL bindings or page selectors are introduced. The existing local player, native request generations, stream proxy and controller boundaries are unchanged.

A real car-service disconnect or service replacement marks the retained player for replacement. The next attachment removes and closes the old PlayerView, including its stream/artwork proxy resources and asynchronous executors, before creating a fresh local page. Temporary host coverage/recreation within the same connection retains its player. Late callbacks from obsolete services cannot invalidate the replacement connection.

## Verification on 2026-11-22

- 149 JVM/Robolectric tests passed. Three added regressions verify differing phone/car densities at 160/240/320 dpi, unchanged phone scale, and same-connection retention versus true disconnect replacement including obsolete-service callbacks.
- Signed release build and release Lint passed: 0 errors, 26 warnings.
- Checked the built APK contains no initial-account.json provisioning asset.
- Verified the installed baseline and new APK share certificate SHA-256 `6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65`.
- Upgraded the connected Samsung SM-S948B in place to 0.1.26-car-viewport (versionCode 27), with no uninstall or data clearing. Opened the phone interface, then observed the real projected car interface in Windows DHU 2.1 at 800 x 480. The header, welcome panel and buttons filled the car content area; the saved-account indicator was still present. This was the production signed release APK.

The physical-phone session was time-limited. IPTV reconnect behavior was verified by JVM tests, not a second physical-phone/DHU reconnect cycle. No IPTV stream playback, fullscreen video, audible sound, camera cover during playback or physical vehicle head unit test was performed for this release. The Tube reconnect result is not presented as an IPTV device test.

Device follow-up while parked: open the phone controller before the car interface; check normal full-width layout. Disconnect/reconnect, reopen the car interface and confirm layout remains correct with no automatic playback. Select a channel and verify playback, controls and fullscreen separately.

APK: MM-IPTV-0.1.26.apk

SHA-256: 9538a0ccca39d66cca262580207169500e0b3adf68996d243ea25d2e5d4a8692
