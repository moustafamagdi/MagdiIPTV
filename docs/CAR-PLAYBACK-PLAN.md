# Car playback and controller plan

Requested 2026-11-22. Baseline: IPTV v0.1.23-support-notice / dc60333;
Tube v0.1.39-support-notice. Preserve package IDs, signing, approved branding,
saved accounts, support reporting and phone-controller-only operation.

1. **IPTV covered-host lifecycle (implemented; validation below).** Reproduce host onStop cancelling
   the car request. Track the bound projection service separately from screen
   visibility, preserve the same player/request/status while covered or recreated,
   and cancel on actual service disconnection. Test Stop, late callbacks,
   reconnection without autoplay, fullscreen and phone closure. Background audio
   off must remain respected. Unit tests are not proof of audible car playback.
2. **Automatic media controls (implemented in the second increment).** Review both apps' media-card/service lifecycle;
   initialize controls with playback without a second player or audio-focus owner.
   Validate controls after changing to Maps/home and returning.
3. **Phone-requested car launch (best-effort implementation; physical validation pending).** Investigate the actual projection
   host APIs; do not assume Car App Library or Maps behavior applies. Test any
   supported launch path with bounded requests, no phone playback, no reconnection
   autoplay, and a clear fallback when the car host refuses launch.
4. **Launcher presentation and release.** Test whether one launcher entry can
   preserve projection UI and media controls. Keep separate entries if removal
   breaks functionality. Build/version/sign and release only validated changes;
   document emulator vs real-device evidence and physical car test steps.

This first increment changes IPTV lifecycle only. It does not promise automatic
car launch, remove either launcher entry, or change Tube's playback engine.

## First increment

The new `CarCoverTest.hostStopPreservesLoadingSelection` failed against the
unmodified baseline: `onStop` disconnected the controller. The fix tracks the
projection service instance; screen onStop/detach preserves the document, current
request, polling and fullscreen while that service remains bound. Unbind/destroy
stops playback, and callbacks from a replaced service cannot stop its successor.
With background audio disabled, covering pauses the media without losing the
selection or scheduling autoplay on return. Phone code remains a controller.

The 11 new Robolectric tests exercise the actual app activity/service lifecycle
methods, with the external Android Auto SDK host stubbed. They cover loading,
playing/progress/pause, fullscreen return, host replacement, disconnect/reconnect,
late Stop callbacks, superseded services, background-off, phone closure, and a
rejected service bind. They do not run an Android Auto host or decode audio.

Validation completed on 2026-11-22: all 118 app JVM/Robolectric tests passed
(zero failures/errors/skips); `assembleDebug`, `lintDebug` and `lintRelease`
passed. Lint retains existing warnings. The debug APK was inspected and contains
no `initial-account.json`. No emulator, real IPTV stream or physical Android Auto
test was performed in this increment; the local test ADB server had no devices.

Two pre-existing debug-harness lint issues were corrected while checking the
build: use the named orientation constant, and retain production's explicit
control-only (no voice catalog search) lint annotation in the debug manifest.

### Physical-car acceptance checks (pending)

1. Enable background audio, play a live channel and separately a VOD item; switch
   to Maps/home and back. Confirm uninterrupted audible sound, advancing progress,
   no reload, and working phone/media-card Pause and Play.
2. Repeat during loading, fullscreen, and a temporary camera overlay. Verify the
   same media/request remains selected and fullscreen returns correctly.
3. Press Stop while covered; return to the app and confirm no automatic restart.
4. Disconnect the cable/wireless car connection while loading and while playing.
   Confirm immediate stop, unavailable phone controls and no autoplay on reconnect.
5. Close only the phone controller while playing; car playback must continue.
6. Disable background audio and leave the car interface: playback must pause and
   stay paused on return until explicitly resumed.

No phone playback fallback is permitted. Whether an actual head unit keeps the
projection service bound while another car app is visible remains a device-test
dependency. This increment is not a published APK release; version/signing and
distribution were deferred until the second increment below.

## Second increment: 0.1.24-auto-audio / Tube 0.1.40-auto-audio

The existing MediaBrowserService now binds automatically from accepted car-player
observations. It uses the application context, creates no second player and takes
no extra audio focus. Pause retains the binding; Stop, a replacement selection or
disconnection clears obsolete state and releases it. Missing/failed bind callbacks
have a ten-second deadline and at most three attempts per selection. Old callbacks
cannot alter the new connection. The phone never becomes a playback target.

IPTV's media card now uses the controller's observed state and capabilities for
Play, Pause, Stop, next/previous and seek. Stop cancels playback instead of acting
as Pause. Buffering/error/ended states are represented explicitly, and observations
expire after 6.5 seconds. Commands cannot use a stale connection or selection.
The first increment's covered-host lifecycle fix is included in this release.

Validation on 2026-11-22: 131 IPTV and 118 Tube JVM/Robolectric tests passed, with
no failures/errors/skips; clean release builds and release lint passed. Existing
lint warnings remain. API 36 synthetic emulator checks use actual WebView decoding
and MediaController transport callbacks, with no manual media-service launch.
Six focused scenarios passed in each app: automatic session creation; observed
Pause/Play and phone buttons; covered fullscreen/progress/commands; Stop releases
the binding; reconnect stays idle; and phone closure preserves car playback while
the phone itself stays silent. Tube's existing 27-scenario native controller suite
also passed, along with IPTV's existing 17-scenario native controller suite.
Debug fixtures are excluded from release APKs.

These tests do not establish audible output, real-provider behavior or compatibility
with a physical Android Auto host. Run the physical-car acceptance checks above,
without first opening the Audio icon. Both launcher entries remain. Phone-requested
car UI launch is still step 3; this release does not implement it. Upgrade in place
using the existing package and signing certificate; no account is bundled.

## Third increment: 0.1.25-car-launch / Tube 0.1.41-car-launch

The bounded legacy SDK UI request, user-facing fallback and no-selection-queue policy are implemented. See [CAR-LAUNCH.md](CAR-LAUNCH.md) for API evidence, safeguards, validation limits and physical-car acceptance steps. This does not promise cold launch on every Android Auto version.
