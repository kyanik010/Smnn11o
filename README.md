<div align="center">
  <img src="sonix-logo.png" width="320" alt="SONIX IPTV logo">
  <h1>SONIX IPTV</h1>
  <p>Android mobile and Android TV player for user-provided IPTV playlists and streams.</p>
</div>

## Project status

IdealPlayer is a Kotlin/Jetpack Compose portfolio project with separate mobile and TV navigation,
M3U/M3U8 and Xtream input, XMLTV EPG support, Media3 playback, and LibVLC fallback.

The repository does not bundle channels, playlists, provider directories, stream URLs, accounts, or
copyrighted media. Users are responsible for supplying content they are authorized to access.

Current app version: `1.2.5` (`versionCode` 39). Android configuration: min SDK 26, compile/target
SDK 35, Java 17.

## Features

- User-provided M3U/M3U8 URLs, local playlist documents, and Xtream credentials.
- Live channels, movies, series, favorites, watch history, search, and continue watching.
- Mobile/TV timeline guide, XMLTV parsing, EPG channel matching, catch-up routing, and network-constrained synchronization.
- Bounded movie-poster recovery from Xtream VOD metadata and optional TMDB enrichment.
- Media3/ExoPlayer primary engine with automatic LibVLC recovery and privacy-safe per-stream engine profiles.
- M3U request-header compatibility for `EXTVLCOPT`, `EXTHTTP`, `KODIPROP`, and pipe-suffixed headers.
- Privacy-safe playback diagnostics on mobile and TV, with a copyable support report.
- Audio/subtitle track selection, playback speed, quality policy, and aspect ratio controls.
- Mobile gestures, edge-to-edge player UI, orientation handling, and Picture-in-Picture.
- Android TV launcher, D-pad navigation, explicit focus treatment, and TV player overlays.
- PBKDF2-hashed parental PIN with encrypted local preference storage and lockout behavior.
- Optional TMDB metadata enrichment when a developer supplies an API key.
- Optional self-hosted update and Connected IdealPlayer TV setup integrations, disabled in normal builds unless
  explicitly configured.

## Architecture

The app is a single Gradle module (`:app`) organized around MVVM and repository boundaries:

```text
com.idealplayer.app
├── core/common       Shared utilities and privacy-safe logging
├── core/model        UI-independent domain models
├── core/database     Room entities, DAOs, migrations, and mappers
├── core/datastore    Preferences DataStore settings
├── core/network      Retrofit APIs and DTOs
├── core/player       Player contract, Media3, LibVLC, retry, and timers
├── core/worker       Hilt WorkManager jobs
├── data/parser       M3U, XMLTV, Xtream, and EPG matching
├── data/repository   Data orchestration and persistence
├── metadata          Optional TMDB lookup/cache
├── di                Hilt configuration
└── ui                Compose screens, ViewModels, mobile/TV navigation
```

Composables render ViewModel state and emit callbacks. Network, parsing, persistence, playback
selection, and synchronization remain outside the UI layer. `PlayerManager` owns player engines;
Composables only attach the active engine to a surface.

## Technology

| Area | Version / implementation |
|---|---|
| Kotlin | 2.0.21 |
| Android Gradle Plugin / Gradle | 8.7.3 / 8.9 |
| Jetpack Compose BOM | 2024.09.03 |
| Hilt | 2.51.1 |
| Room | 2.6.1 |
| DataStore | 1.1.1 |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 |
| Media3 | 1.5.0 |
| LibVLC | 3.6.0 |
| Coroutines | 1.9.0 |

## Setup

Prerequisites:

- Android Studio with Android SDK 35 and build tools installed.
- JDK 17.
- Android SDK path in an untracked `local.properties` file.

Copy the documented settings and replace only what is needed:

```bash
cp local.properties.example local.properties
```

At minimum, set:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

`TMDB_API_KEY` is optional. It is compiled into the APK and must not be treated as a server-side
secret; use a backend proxy if the key needs stronger protection.

## Build and verification

```bash
./gradlew clean
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

The default `release` variant is Play-oriented: it does not contain
`REQUEST_INSTALL_PACKAGES`, the APK installer `FileProvider`, or the in-app APK update UI.
Without local signing configuration, Gradle produces an unsigned release artifact.

For a Play upload bundle:

```bash
./gradlew :app:bundleRelease
```

## Release variants

- `debug`: `.debug` application ID suffix, debug logging, no APK self-update permission.
- `release`: minified Play-oriented release; no self-hosted updater.
- `selfHostedRelease`: minified direct-distribution APK with explicit APK update support and
  `REQUEST_INSTALL_PACKAGES`. Do not upload this variant to Google Play.

Self-hosted update instructions are in [docs/self-hosted-updates.md](docs/self-hosted-updates.md).

## Rebrand installation and data note

IdealPlayer uses the Android application ID `com.idealplayer.app`. Android therefore installs it as
a separate app, not as an update over the former package. Local playlists, settings, watch history,
and other app-private data from the former app are not transferred automatically. This project does
not attempt unsafe, root-dependent, or automatic migration of that data.

## Connected IdealPlayer TV setup

Connected IdealPlayer is an opt-in TV setup flow and stays disabled unless both HTTPS settings are supplied:

```properties
REMOTE_SETUP_API_BASE_URL=https://setup-api.example.com
REMOTE_SETUP_WEB_BASE_URL=https://setup.example.com
```

No backend account, endpoint, or key is committed. A Supabase Edge Function scaffold is included for
the short-lived 8-character pairing API; deploy and configure it before building an APK with QR
setup enabled. See [docs/connected-idealplayer-supabase.md](docs/connected-idealplayer-supabase.md) and
[web/remote-setup/README.md](web/remote-setup/README.md). The QR image is generated locally.

## Security and privacy notes

- Android backup and device transfer are disabled because the database can contain playlist URLs
  and Xtream credentials.
- Release builds do not plant a Timber log tree. Sensitive URL logs retain only scheme, host, and
  port; Xtream network exceptions are not printed with credential-bearing request data.
- Cleartext HTTP is intentionally permitted for user-supplied legacy IPTV endpoints. HTTPS should
  be preferred; the app cannot safely restrict unknown user domains in a static network policy.
- Room migrations 1 through 7 are registered and schema 7 is exported. There is no destructive
  migration fallback.
- The direct APK updater requires HTTPS, a valid SHA-256 digest, and a bounded download size.

See [PRIVACY.md](PRIVACY.md) before publishing a store listing or hosting optional integrations.

## Quality and release documentation

- [Manual QA checklist](docs/MANUAL_QA.md)
- [Release checklist](docs/RELEASE_CHECKLIST.md)
- [Self-hosted update guide](docs/self-hosted-updates.md)

No emulator or physical-device results are claimed by this repository audit. Phone, TV, playback,
PiP, focus, and installer behavior still require the linked device checklist.

## Known limitations

- Stream and codec compatibility depends on the device decoder, provider, and network.
- Android TV focus and 10-foot layout require verification on real TV hardware or an emulator.
- Connected IdealPlayer needs the included Supabase backend, or an equivalent API, to be deployed
  separately; it is off by default.
- Playlist/Xtream credentials are stored in the app-private Room database. Backups are disabled,
  but this is not equivalent to a SQLCipher-encrypted database on a compromised device.
- Dependency and target-SDK policy must be reviewed again before each Play submission.

## Legal notice

IdealPlayer is a media player only. It does not provide, sell, recommend, index, or redistribute
media services or content. Do not use it to access material without permission. The project has no
affiliation with playlist providers, broadcasters, or channel operators.

## License

Licensed under the [MIT License](LICENSE).
