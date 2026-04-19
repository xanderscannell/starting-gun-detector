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

- Manual latency offset entry (Phase 4.2, optional)
- UI polish (loading states, empty state art, onboarding)
- Internal test track distribution (Firebase App Distribution or direct APK)
- Firestore security rules (currently open — restrict to session-member writes)
- Consider session expiry / cleanup (stale sessions accumulate)

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
