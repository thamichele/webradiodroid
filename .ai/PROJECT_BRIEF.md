# PROJECT_BRIEF — Transistor

Reference brief for Transistor, read by the planning AI at the start
of every task and by the coding AI for the Build section. Keep it
terse and factual; no prose for human readers.

## Summary
Minimalist Android internet radio player; imports M3U/PLS streams, searches radio-browser.info, plays MP3/AAC/Ogg-Opus/HLS via Media3.

## Architecture
- Language: Kotlin only. No Java sources.
- UI: XML layouts + ViewBinding-free `findViewById` via `LayoutHolder` classes. AppCompat + Material + ConstraintLayout. No Compose.
- Single Gradle module: `:app`. Application id `org.y20k.transistor`, namespace `org.y20k.transistor`.
- Package layout under `org.y20k.transistor`:
  - root — `Transistor` (Application), `MainActivity`/`BaseMainActivity`, `MainFragment`, `SettingsFragment`, `PlayerService`/`BasePlayerService`, `Keys` (constants).
  - `ui` — layout-holder classes and `PlayerState`.
  - `collection` — `Collection` adapter + `CollectionViewModel`.
  - `core` — domain types `Collection`, `Station`.
  - `dialogs` — modal dialog fragments.
  - `extensions` — Kotlin extension functions.
  - `helpers` — file/network/audio/download/preferences/theme/backup/update utilities; `DownloadWorker`, `DownloadFinishedReceiver`.
  - `search` — radio-browser.info client and direct-URL input check.
- DI: none. Manual instantiation.
- Async: kotlinx-coroutines (core + android). LiveData via `androidx.lifecycle` through `CollectionViewModel`.
- Persistence: JSON files on disk via Gson; managed by `FileHelper` / `CollectionHelper` / `BackupHelper`. No Room/SQLite.
- Navigation: AndroidX Navigation component (fragments).
- Media: Media3 `MediaSessionService` in `PlayerService`; ExoPlayer with HLS support.

## Key files
| Path | Role |
|------|------|
| `app/src/main/AndroidManifest.xml` | Permissions, intent filters (M3U/PLS/HLS schemes + MIME types), service/receiver/provider declarations, Android Auto metadata. |
| `app/build.gradle.kts` | App module Gradle config, dependencies, SDK levels, R8 wiring. |
| `gradle/libs.versions.toml` | Version catalog — all dependency and plugin versions. |
| `settings.gradle.kts` | Repository declarations (plugins + dependencies) and module includes. |
| `app/src/main/java/org/y20k/transistor/Transistor.kt` | `Application` subclass; theme init. |
| `app/src/main/java/org/y20k/transistor/Keys.kt` | App-wide constants (intents, prefs keys, defaults). |
| `app/src/main/java/org/y20k/transistor/MainActivity.kt` | Single-activity host (extends `BaseMainActivity`). |
| `app/src/main/java/org/y20k/transistor/MainFragment.kt` | Primary screen: station list + playback controls. |
| `app/src/main/java/org/y20k/transistor/PlayerService.kt` | Media3 `MediaSessionService` (extends `BasePlayerService`); foreground playback. |
| `app/src/main/java/org/y20k/transistor/core/Station.kt` | Core station model. |
| `app/src/main/java/org/y20k/transistor/core/Collection.kt` | Core collection model (list of stations). |
| `app/src/main/java/org/y20k/transistor/collection/CollectionViewModel.kt` | LiveData-backed collection observation. |
| `app/src/main/java/org/y20k/transistor/helpers/CollectionHelper.kt` | CRUD over the on-disk collection JSON. |
| `app/src/main/java/org/y20k/transistor/search/RadioBrowserSearch.kt` | radio-browser.info HTTP search client. |

## System and external APIs in use
- `androidx.media3` `MediaSessionService` — foreground media playback session.
- `MediaButtonReceiver` (Media3) — hardware/headset media key handling.
- WorkManager (`androidx.work`) — background station-image download via `DownloadWorker`.
- Android `DownloadManager` — station image/playlist download; completion handled by `DownloadFinishedReceiver` listening on `ACTION_DOWNLOAD_COMPLETE`.
- `FileProvider` (`${applicationId}.provider`) — sharing local files.
- Android Auto — declared via `automotive_app_desc` meta-data.
- App Shortcuts (`@xml/shortcuts`).
- Voice search — `MEDIA_PLAY_FROM_SEARCH` intent filter on `MainActivity`.
- Network security config + cleartext traffic enabled (radio streams may be HTTP).
- Locale config (`@xml/locales_config`) for per-app language picker.
- radio-browser.info HTTP API — station search backend.
- Android `Vibrator` — haptic feedback.

## Key dependencies
- `androidx.media3` (exoplayer, exoplayer-hls, session) — playback, session, HLS.
- `androidx.media` — legacy media compat.
- `androidx.navigation` (fragment-ktx, ui-ktx) — fragment navigation.
- `androidx.work:work-runtime-ktx` — background downloads.
- `kotlinx-coroutines-core` / `-android` — async.
- `com.google.code.gson:gson` — JSON (collection, settings, search results).
- `com.android.volley:volley` — HTTP client for search/network calls.
- `com.github.bumptech.glide:glide` — station image loading.
- `com.google.android.material:material`, `androidx.appcompat`, `androidx.constraintlayout`, `androidx.preference`, `androidx.palette` — XML UI stack.

## Build
- Wrapper present. Debug APK: `./gradlew assembleDebug`. Release APK: `./gradlew assembleRelease`.
- **Toolchain:** Gradle 9.6.1, AGP 9.4.0, Kotlin 2.3.21, Java/Kotlin target 17; versions live in `gradle/libs.versions.toml`. Daemon JVM pinned to JetBrains Runtime 21 by `gradle/gradle-daemon-jvm.properties`. Android build-tools unpinned — use Android Studio's bundled version.
- `local.properties` must define `sdk.dir`. No other entries required by the build script.
- Release: `optimization { enable = true }` on the release build type; keep rules in `app/src/main/keepRules/rules.keep`. Debug installs beside release: `applicationIdSuffix ".debug"`, label "Transistor (debug)" via the `appLabel` manifest placeholder (`app/src/debug/res/values/strings.xml`).
- No product flavors. No `signingConfigs` in `app/build.gradle.kts` — release signing done manually via Android Studio.
- Lint: `MissingTranslation` disabled.
- `dependenciesInfo.includeInApk = false` and `includeInBundle = false`.
