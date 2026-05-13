# Project Status

**Last updated**: 2026-05-13

## Current Position

**Phase**: Phase 8 in progress — Firestore security hardening
**Progress**: Code written, awaiting build verification + rules deployment

## Recently Completed (2026-05-13 session)

- **Phase 8: Firebase Auth + Firestore security rules**
  - Added `firebase-auth-ktx` dependency
  - Created `device/AuthManager.kt` — singleton wrapping anonymous Firebase Auth, mutex-deduped sign-in
  - Created `StartingGunApplication.kt` + manifest registration — eager background sign-in on app launch
  - Gutted `device/DeviceIdProvider.kt` — removed SharedPrefs UUID; `auth.uid` is the new device identity
  - Refactored `session/SessionRepository.kt` — no constructor param; each method calls `AuthManager.requireUid()`
  - Updated `viewmodel/GunShotViewModel.kt` + factory — deviceId now nullable, populated post-sign-in
  - Wrote `firestore.rules` + `firebase.json` — open reads (lynx/script keep working), writes locked to session members + own auth.uid
  - **Pending:** build verification in Android Studio + `firebase deploy --only firestore:rules`
  - **Deployment ordering:** ship the app update first, wait for adoption, then deploy rules — old app versions will fail all writes once rules are live

## Previously Completed (2026-04-21 session)

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

- Verify Phase 8 build in Android Studio, then deploy `firestore.rules` via Firebase CLI
- Merge `lynx` branch (still has the leaked Firebase API key in `detector-to-lynx/FirestoreService.cs:12` and `scripts/export_session_start_times.py:17` — needs cleanup commit on the branch before merge; rotation in GCP not strictly needed since Firebase Web API keys are designed to be public)
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
│   ├── AuthManager.kt                  [complete] ← NEW (Phase 8)
│   ├── DeviceIdProvider.kt            [complete] ← SLIMMED (Phase 8)
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

- **Session expiry**: Sessions accumulate indefinitely. Consider a TTL or manual cleanup function.
- **Keystore management**: Release keystore was created locally. User should back it up — without it, future updates cannot be signed with the same key.
