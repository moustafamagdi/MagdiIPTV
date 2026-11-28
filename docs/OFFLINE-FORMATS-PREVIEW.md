# Offline formats preview — 0.1.38-preview (39)

Adds direct MKV and MPEG-TS downloads alongside MP4/M4V/WebM. Local responses use their correct MIME types. The existing car WebView decodes MKV using device codecs and the bundled mpegts.js pipeline handles TS. Container support is not a promise that every video/audio codec is supported. Unsupported local playback retains the file and shows a codec explanation.

Finite, unencrypted HLS media playlists with TS segments are downloaded as one library title. Up to four segment transfers per title run through Android DownloadManager. A completion receiver advances pending segments; opening the library also reconciles persisted state. Only fully completed, length/header-validated segments become playable. Local playlists reference opaque local segment paths and preserve discontinuities/timing, rather than naively concatenating incompatible TS timelines. This is an offline bundle, not an exported single TS file.

Boundaries: no live recording, encrypted HLS, master/variant playlists, separate audio renditions, fMP4 maps, byte-range playlists or gap segments. These produce an explicit reason after manifest inspection. Media playlists are bounded to 2 MiB and 2,000 segments. Relative segment URLs resolve against the original playlist URL; playlists redirected to a different base may fail and can be retried. Direct TS seeking remains limited by the existing MPEG-TS engine. HLS supports its playlist-based seek path.

Every phone movie/episode retains a download action. Unsupported formats show a reason instead of disappearing. Episode download actions occupy their own row on narrow screens. Season download shows supported/total counts, disables when none are supported, and confirms how many will be skipped before queuing. An episode downloaded individually retains its season group.

Download/delete/retry use app-private files; package ID, signing, account data and existing release channel selection are unchanged. No stream URLs or credentials are added to reports. HLS manifests exposed to playback are rewritten locally and unknown tags are discarded; encrypted and unsupported constructs are rejected.

Validation includes real Samsung SM-S948B API37 device testing through a debug-only two-WebView harness, with no desktop test window. Synthetic MKV/H.264-AAC, TS and a 20-segment HLS title were downloaded by Android DownloadManager. Actual decoded frames were observed for all three with the fixture server stopped, while the phone remained a silent controller. Mixed-format season UI showed 2/3 supported episodes and an explicit AVI reason without horizontal overflow. Physical car behavior, provider-specific codecs, large seasons and OEM background restrictions still need field testing.

Only a prerelease is published; stable v0.1.35 remains unchanged. Canonical asset MM-IPTV.apk, official signer, versionCode 39.
