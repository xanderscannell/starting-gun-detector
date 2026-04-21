# Project Status

**Last updated**: 2026-04-21

## Current Position

**Phase**: Phase 7 complete — Race file persistence system shipped
**Progress**: 100% — all planned features implemented

## Recently Completed (2026-04-21 session)

- **Phase 7: Race File Persistence System**
  - Added `kotlinx-serialization-json` dependency
  - Created `data/RaceModels.kt` — Race, StartTime, FinishSplit, StartTimeSource
  - Created `data/RaceRepository.kt` — JSON file persistence + video management
  - Created `viewmodel/RaceViewModel.kt` + `RaceViewModelFactory.kt` — Race CRUD ViewModel
  - Created `ui/RaceBrowserScreen.kt` — three-level meet/event/race browser
  - Rewrote `ui/CaptureScreen.kt` — save dialog, start time selector, manual start time entry, separate scrubber modes for new vs saved races
  - Modified `ui/StartingGunScreen.kt` — added RACES page to drawer
  - Modified `MainActivity.kt` — wired RaceRepository, RaceViewModel, orphan video cleanup
  - Previous capture files now deleted on new recording start (prevents accumulation)

## Previously Completed

- Phase 1–4: Full app scaffold, audio engine, ViewModel + UI, polish + release
- Phase 5: Finish line capture with video scrubber and server clock sync
- Phase 6: Timing accuracy fixes (worst-case error reduced from ~550ms to ~130ms)

## Next Up (future sessions)

- Firestore security rules (restrict writes to session members)
- Session expiry / cleanup (stale sessions accumulate)
- Data export (CSV/PDF race results)
- Lane/athlete/wind metadata (future enhancement)

## Active Files and Modules

```
app/src/main/java/com/xanderscannell/startinggundetector/
├── MainActivity.kt                    [complete]
├── audio/
│   └── AudioDetector.kt               [complete]
├── data/
│   ├── RaceModels.kt                  [complete] ← NEW
│   └── RaceRepository.kt             [complete] ← NEW
├── device/
│   ├── DeviceIdProvider.kt            [complete]
│   └── UserPreferences.kt            [complete]
├── session/
│   └── SessionRepository.kt           [complete]
├── ui/
│   ├── CaptureScreen.kt              [complete] ← REWRITTEN
│   ├── RaceBrowserScreen.kt          [complete] ← NEW
│   ├── SessionDialog.kt               [complete]
│   ├── StartingGunScreen.kt           [complete]
│   └── theme/Theme.kt                 [complete]
├── utils/
│   └── TimestampFormatter.kt          [complete]
└── viewmodel/
    ├── GunShotViewModel.kt            [complete]
    ├── GunShotViewModelFactory.kt     [complete]
    ├── RaceViewModel.kt               [complete] ← NEW
    └── RaceViewModelFactory.kt        [complete] ← NEW
```

## Open Questions

- **Firestore security rules**: Currently open-read/write. Should restrict so only devices in a session can write detections. Low priority until wider distribution.
- **Session expiry**: Sessions accumulate indefinitely. Consider a TTL or manual cleanup function.
- **Keystore management**: Release keystore was created locally. User should back it up — without it, future updates cannot be signed with the same key.
