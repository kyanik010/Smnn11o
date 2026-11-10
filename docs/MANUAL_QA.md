# Manual QA checklist

These checks require an Android phone, Android TV/emulator, or installed release artifact. Critical
phone and TV flows were exercised on connected emulators for the 1.2.0 release candidate on
2026-07-22; the complete matrix remains the certification checklist for additional devices and API
levels.

Use synthetic, owned, or otherwise authorized test playlists and streams. Never attach production
credentials to bug reports or screenshots.

## Android phone

- Install the debug build on API 26, API 31, API 33, and API 35+ where available.
- Verify a fresh install opens onboarding and does not request broad photo/video storage access.
- Add an M3U URL, Xtream account, and local M3U document through the system picker.
- Force-stop/relaunch after local document import and verify persisted URI access still works.
- Verify invalid URL, wrong credentials, empty playlist, timeout, and offline states are readable and
  do not crash or reveal a password.
- Confirm active playlist persistence, edit/delete behavior, counts, categories, search, favorites,
  history clearing, empty states, and loading states.
- Verify portrait, landscape, split-screen, display-size changes, font scaling, and edge-to-edge
  insets on gesture and three-button navigation.
- Play live HLS/TS and owned VOD samples with Media3; repeat with LibVLC.
- Switch engines while buffering, playing, paused, and after an error. Confirm live restarts from the
  live edge and VOD resumes near its prior position.
- Exercise pause/resume, seek limits, double-tap seek, volume/brightness gestures, screen lock,
  aspect modes, playback speeds, video quality, audio tracks, subtitles, and subtitle disable.
- Rotate during playback and after opening each player sheet. Verify no duplicate audio or leaked
  player surface.
- Press Home during playback on Android 8-11 and Android 12+. Verify PiP transition, 16:9 bounds,
  hidden overlays, resume to full screen, and correct teardown after closing PiP.
- Disconnect/reconnect Wi-Fi during live playback. Verify bounded retries and a usable final error.
- Test audio-only, video-only, malformed, HTTP 401/403/404/5xx, codec failure, and ended media.
- Verify parental PIN setup, five-failure lockout, successful unlock, category filtering, PIN change,
  and clearing.
- Verify Turkish, English, and system locale changes after process restart.
- Run TalkBack through actionable controls and confirm meaningful labels and logical traversal.

## Android TV

- Install on at least one Google TV/Android TV device and one TV emulator/API level if available.
- Confirm the Leanback launcher banner, app label, splash screen, and TV UI selection.
- Complete onboarding with only a D-pad: first focus, add source, type selection, fields, test, save,
  cancel, edit, delete, and focus recovery after every dialog.
- If remote setup is configured, verify the locally rendered QR, eight-character pairing code,
  success, timeout, cancel, invalid code, oversized file, and backend-unavailable states.
- Navigate every top-level destination using Up/Down/Left/Right/Center/Back. Look for focus traps,
  invisible focus, lost focus after list updates, unexpected drawer opening, or off-screen focus.
- Stress long category/channel/movie/series lists and rapid D-pad input. Verify stable card sizes,
  scrolling, retained selection, and no large frame stalls.
- Open search, invoke the TV keyboard, clear/query/filter results, open details, return, and confirm
  predictable focus restoration.
- Verify all text is readable at typical 2-4 meter distance and at 720p, 1080p, and 4K output.
- Play live and VOD with both engines. Verify black-frame handling, first-frame confirmation,
  buffering/error overlays, track dialogs, quality/aspect/speed dialogs, and sleep timer.
- Exercise Back, Center, arrows, channel Up/Down, numeric channel entry, Play/Pause, Fast Forward,
  Rewind, and any device-specific media keys.
- Switch channels quickly and queue another switch while the first is warming up. Confirm failure
  returns to a playable state and never leaves two audio streams running.
- Open/close channel and category panels repeatedly; verify first focus and focus recovery.
- Suspend/wake the TV, switch HDMI/input, background/foreground the app, and force-stop/relaunch.
- Check overscan/safe areas and ensure no essential control sits under system UI.

## Release APK/AAB

- Build from a clean checkout with the intended `version.properties` and JDK 17.
- Inspect signing certificate and confirm it matches the previous production certificate.
- Install release over the previous production version and verify data/migration retention.
- Verify `release`/Play artifacts contain no `REQUEST_INSTALL_PACKAGES` permission or update
  `FileProvider`; verify `selfHostedRelease` contains them only when direct distribution is intended.
- Run `apkanalyzer manifest permissions`, `apkanalyzer manifest print`, or equivalent against the
  exact upload artifact.
- Verify minification does not break Hilt, Room, Retrofit serialization, Media3, LibVLC, metadata,
  or worker creation.
- Test offline cold start, process death, low-storage download/import, and low-memory backgrounding.
- Confirm no debug logs, test endpoints, sample accounts, real streams, keys, keystores, or local
  paths are packaged.
- For self-hosted update only: verify HTTPS manifest, version comparison, mandatory/optional UI,
  SHA-256 rejection, oversized APK rejection, unknown-sources flow, signature continuity, and retry.

## Play Console and sensitive permissions

- Upload only the Play-oriented `release` AAB, never `selfHostedRelease`.
- Confirm Play's App bundle explorer does not list `REQUEST_INSTALL_PACKAGES`, storage/media read,
  or media-playback foreground-service-type permissions from app code.
- Review the final merged `FOREGROUND_SERVICE` permission added by WorkManager and confirm there is
  no app-declared long-running playback foreground service.
- Complete Data safety based on the exact distribution configuration, including optional TMDB or
  remote-setup network processing.
- Supply a privacy-policy URL and verify its claims match the build.
- Review cleartext traffic, TV screenshot/banner requirements, content rating, target audience,
  ads declaration, app access instructions, and intellectual-property declarations.
- Recheck current target API and restricted-permission policy immediately before submission.
