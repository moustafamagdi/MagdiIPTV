# MM IPTV project rules

- Canonical repository: `https://github.com/moustafamagdi/MagdiIPTV`. Fetch and inspect its current branch/release before editing; the older workspace backup is not the implementation baseline.
- Approved display names are **MM IPTV** and **MM IPTV Audio**. Keep labels, UI, notifications, diagnostics and release titles consistent. Android labels use `@string/app_name` and `@string/audio_app_name`.
- Preserve the approved green TV icon `app/src/main/res/drawable/ic_iptv.xml` and matching green headphones/TV audio icon `ic_iptv_audio.xml`. These approved resources were reconciled from the user's workspace; do not restore the historical monitor/play icon.
- Keep package `com.magdi.iptv`, repository identity, signing certificate, preference names and account storage unchanged for upgrades.
- MM Auto releases contain one APK named `MM-IPTV.apk`, with increasing versionCode and release-body metadata `<!-- mm-version-code: <code> -->`.
- The phone is a controller only. The car owns the local WebView HTML player; it does not need YouTube document-start binding. Guard asynchronous work by player instance, connection and media request.
- Preserve diagnostic sessions, copy/export, retention and credential redaction. Never log stream URLs or provider/account credentials.
