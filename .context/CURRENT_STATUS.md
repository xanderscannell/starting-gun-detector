# Project Status

**Last updated**: 2026-04-19

## Current Position

**Phase**: All phases complete
**Progress**: 100% — app fully built, Firebase session feature shipped, release APK generated

## Recently Completed (2026-04-19 session)

- Built complete Android project from scratch (Kotlin, Compose, MVVM)
- `AudioDetector.kt` — RMS + rolling baseline detection, sub-buffer peak timing, 2.5s cooldown
- `GunShotViewModel.kt` — full state machine (IDLE/LISTENING), `StateFlow<UiState>`
- `StartingGunScreen.kt` — live clock, detection history, star toggle, sensitivity slider, auto-sizing timestamp text
- Firebase Firestore shared session feature:
  - `DeviceIdProvider.kt` — UUID in SharedPreferences, `shortId()` for badge display
  - `SessionRepository.kt` — create/join session, write detection, stream via `callbackFlow`
  - `GunShotViewModelFactory.kt` — manual DI without Hilt
  - `SessionDialog.kt` — join/create UI with 4-char code input
  - `SessionBar` composable — shows session code + Leave button when in session
  - `DeviceBadge` composable — colour-coded pill per device in history rows
- Release APK build guidance provided (Android Studio → Generate Signed Bundle / APK)
- Committed as: `feat: shared session via Firebase Firestore` (commit `301c2b4`)

## In Progress

Nothing.

## Next Up (future sessions)

- **Phase 5.1** — Camera preview in Capture tab (start here next session)
- **Phase 5.2** — Record video with `recordingStartMillis` captured
- **Phase 5.3** — Live clock overlay on preview
- **Phase 5.4** — Frame-by-frame scrubber playback (ExoPlayer)
- **Phase 5.5** — Split time calculation from gun timestamp
- Manual latency offset entry (Phase 4.2, optional)
- Firestore security rules (currently open — restrict to session-member writes)
- Session expiry / cleanup (stale sessions accumulate)

## Active Files and Modules

```
app/src/main/java/com/xanderscannell/startinggundetector/
├── MainActivity.kt                    [complete]
├── audio/
│   └── AudioDetector.kt               [complete]
├── device/
│   └── DeviceIdProvider.kt            [complete]
├── session/
│   └── SessionRepository.kt           [complete]
├── ui/
│   ├── StartingGunScreen.kt           [complete]
│   ├── SessionDialog.kt               [complete]
│   └── theme/Theme.kt                 [complete]
├── utils/
│   └── TimestampFormatter.kt          [complete]
└── viewmodel/
    ├── GunShotViewModel.kt            [complete]
    └── GunShotViewModelFactory.kt     [complete]
```

## Open Questions

- **Firestore security rules**: Currently open-read/write. Should restrict so only devices in a session can write detections. Low priority until wider distribution.
- **Session expiry**: Sessions accumulate indefinitely. Consider a TTL or manual cleanup function.
- **Keystore management**: Release keystore was created locally. User should back it up — without it, future updates cannot be signed with the same key.
- **Known gap — scrubber calibration warning**: In `VideoScrubber`, if `serverOffsetMs` is null when the user opens the scrubber, the timestamp silently falls back to 0ms offset (client time) with no warning. The "Clock not synced" warning only exists on the camera preview screen. Fix: show a warning banner in the scrubber when `serverOffsetMs == null`.
