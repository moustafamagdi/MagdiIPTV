# MM IPTV 0.1.39 — download network consent

Download confirmation now has explicit Wi-Fi/mobile-data and unmetered Wi-Fi-only actions plus cancellation. The selected policy is captured per title/season, used by the initial Android DownloadManager request and HLS segments, and retained on retry. Global defaults and unrelated downloads are unchanged. Account changes while confirming abort the action, and repeated taps are guarded.

Validation: 167 app unit tests; 21 JavaScript tests; release build and lint passed. Tests cover request metered policy, persisted retry, HLS manifest policy, cancellation, account changes and duplicate taps. Release APK assets match source, official certificate retained, no private account seed or debug harness. No connected phone was available for this fix; real cellular/car testing remains outstanding.

Existing queued downloads retain their original policy; remove and add the title again to choose another network policy. Retrying an existing item keeps its saved policy. Existing files and accounts are preserved.
