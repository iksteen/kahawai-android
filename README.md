# Kahawai — Android

A native Android client for a [kahawai](https://github.com/iksteen/kahawai)
hub: browse the catalog, search, and play. Kotlin + Jetpack Compose +
Media3 (ExoPlayer). Talks only to the hub's versioned `/api/v1/*`
surface — same contract the web UI (`../web/`) uses, no private
endpoints.

## Build

```sh
cd android
./gradlew assembleDebug
```

First run generates `local.properties` pointing at your Android SDK if
it's missing (`sdk.dir=...`); set `ANDROID_HOME` or edit it by hand if
`sdkmanager` can't find one. minSdk 26, compileSdk 36, JDK 17+ required
(the Gradle daemon needs `JAVA_HOME` pointed at a JDK 17+ install —
AGP 9's built-in Kotlin support won't configure otherwise).

## Run

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kolktech.kahawai/.ui.MainActivity
```

On first launch the app asks for the hub's address (e.g.
`192.168.1.20:8420` or `https://kahawai.example.com`) — self-hosted, so
there's no default. It defaults to `http://` if you don't specify a
scheme, since a bare `kahawai hub` process serves plain HTTP by default
(`bind` in `kahawai.toml`); put a reverse proxy or ACME cert in front
for a real deployment. From an emulator, the host machine's loopback is
`10.0.2.2`, not `127.0.0.1`.

## What's here

- **Auth**: bearer JWT + rotating refresh token, encrypted at rest via
  DataStore + Tink (Android Keystore-backed AES-256-GCM) — see
  `data/auth/TokenStore.kt`.
- **Catalog**: `data/repository/CatalogRepository.kt` — libraries, items
  (browse/search), item detail, children (episodes/tracks).
- **Playback**: `playback/CapabilityProfileBuilder.kt` probes the
  device's actual decoders (`MediaCodecList`) rather than hardcoding a
  claim, same principle as the web client's runtime `MediaSource`
  probing. `ui/player/PlayerViewModel.kt` owns the session lifecycle:
  start → attach a Media3 `MediaItem` (progressive for `direct` mode,
  HLS for `remux`/`transcode`) → report progress every 10s → seek
  (routed through the hub's seek-restart endpoint for HLS sessions,
  since those serve a growing EVENT playlist) → end session on exit.

## Known gaps (by design, this pass)

- No subtitle rendering (`ass_render`/`graphics_overlay` are sent
  `false`, so the hub only offers modes this app can actually honor).
- No downloads/offline playback.
- No Android TV/leanback UI.
- No live catalog refresh via `/api/v1/events` (SSE) — Home/Search just
  re-fetch on open.
- HLS seeks always round-trip through the hub's seek-restart endpoint,
  even when the target is already inside what's been produced —
  simpler than the web client's in-range check, and always correct,
  just one extra round trip on some seeks.

## Verifying against a real hub

```sh
# from the repo root
cargo build
pkill -f '^\./target/debug/kahawai hub'
nohup ./target/debug/kahawai hub >> ~/.local/share/kahawai/hub.log 2>&1 &
```

The hub alone is enough to verify auth, browsing, search, and session
negotiation. Actually decoding a file also needs a `mediahost` (serves
the bytes) and, for content that needs it, a `transcoder` — see
`../docs/kahawai-deployment.md`.
