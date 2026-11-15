# King Installer integration

The app contains its own small classic self-reinstall implementation using Android's package installer intents, plus an explicit APK handoff to the separately installed King Installer application (`com.example.kinginstaller`). It does not embed King Installer's APK, source code, Root or Shizuku implementation.

The integration was checked against the upstream intent and manifest interfaces on 16 November 2026:

- https://github.com/fcaronte/KingInstaller
- https://github.com/fcaronte/KingInstaller/blob/master/app/src/main/java/com/example/kinginstaller/InstallationUtils.kt
- https://github.com/fcaronte/KingInstaller/blob/master/app/src/main/AndroidManifest.xml
- Official downloads: https://github.com/fcaronte/KingInstaller/releases

King Installer is a separate GPL-3.0 project. Naming it here describes interoperability; this app is not an official King Installer release. Installer metadata observations do not establish Android Auto acceptance.
