# Offline library preview — 0.1.37-preview (38)

## User flow

On the phone, movies and episodes with direct MP4/M4V/WebM files have Download
beside Play. Movie details also offer both actions. Series offer Download this
season, with a confirmation and a limit of 200 selections per request. Existing
entries are skipped per account/type/provider ID. Unsupported items are counted
as not added, never silently treated as successful downloads.

Downloads is available from the first screen on phone and car, without connecting
to the provider. Completed items play from the phone's app-owned local files in
the existing car WebView. The phone remains a controller. Listing/playback do not
require the account or provider to be reachable. Download management is phone-only.

The library shows each file's progress, size/state and overall used/free space.
Users can cancel, retry (a fresh transfer), delete a file or delete a season with
confirmation. Metered/mobile/hotspot transfers are opt-in for new requests.
Android DownloadManager handles background work, network waiting and resumable
transfers where the server supports them. There is no manual pause/resume promise.
New entries are refused below 256 MiB free space or above 500 library entries;
Android handles insufficient space during a running transfer. Unknown sizes are
not invented. Downloaded files are removed when the application is uninstalled.

Start playback fullscreen on the car is an optional setting, initially off. It
applies to new playback attempts; the existing exit-fullscreen control remains.

## Boundaries

- This preview downloads direct Xtream movie/episode files, not live TV, M3U
  playlists, HLS/DASH segment collections, DRM licenses or arbitrary pasted URLs.
- MP4/M4V and WebM are supported containers; the device still needs to support the
  encoded video/audio tracks. MKV is not advertised as supported by this preview.
- Provider details/artwork are not required for offline playback. Local entries
  retain the title, type and season label; artwork is not downloaded in this build.
- Files are stored in the app-specific external files directory. No broad storage
  permission or exported file-serving endpoint was added.
- Native private state retains transfer URLs for retries. URLs and credentials
  are never returned by the downloads API or included in diagnostics. UI uses
  opaque tokens; local playback validates the token and recorded file length.
- System completion, expected length (when known), and MP4/WebM header validation
  are required before a file becomes playable. HTML/playlist error responses are
  rejected. Local byte ranges support seeking, suffixes and 416 responses.
- Controller connection/request guards remain in force for local playback.
  Account storage, package, signer, existing streaming and release channels stay.

The attached third-party debug APK was inspected only as reference. No code,
resources or binary components from it are included.

## Validation

Automated app tests cover ranges, invalid ranges/headers, duplicate account-scoped
selections, unsupported media, missing files, private metadata redaction, deletion
and a local stream after library recreation. Existing app/backend checks and lint
also run.

A hidden API36 emulator ran both real phone/controller and car/player WebViews
using the debug-only test harness and synthetic media. It downloaded a movie and
two episodes through Android DownloadManager. After stopping the fixture server,
the car WebView decoded local video, sought into it and obeyed the fullscreen
setting; the phone stayed silent. Deletion was verified. After process restart,
the remaining downloads still played without the server. Arabic and English
library layouts were checked at a narrow viewport.

This is emulator verification, not a physical car test. Real providers, very large
seasons, OEM background limits and unsupported codecs still need user testing.
Stable latest remains 0.1.35. This build is published only as a GitHub prerelease
with the official signer and the canonical MM-IPTV.apk asset for MM Auto previews.
