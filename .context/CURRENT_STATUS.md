# Project Status

**Last updated**: 2026-05-13

## Current Position

**Phase**: Phase 9 in progress — Firestore security hardening (rules deployment pending)
**Progress**: Auth code shipped and verified on device; awaiting `firebase deploy --only firestore:rules` once all old app installs are updated. Lynx branch merged into master.

## Recently Completed (2026-05-13 session)

- **Phase 9: Firebase Auth + Firestore security rules**
  - Added `firebase-auth-ktx` dependency
  - Created `device/AuthManager.kt` — singleton wrapping anonymous Firebase Auth, mutex-deduped sign-in
  - Created `StartingGunApplication.kt` + manifest registration — eager background sign-in on app launch
  - Gutted `device/DeviceIdProvider.kt` — removed SharedPrefs UUID; `auth.uid` is the new device identity
  - Refactored `session/SessionRepository.kt` — no constructor param; each method calls `AuthManager.requireUid()`
  - Updated `viewmodel/GunShotViewModel.kt` + factory — deviceId now nullable, populated post-sign-in
  - Wrote `firestore.rules` + `firebase.json` — open reads (lynx/script keep working), writes locked to session members + own auth.uid
  - **Pending:** distribute APK to other users, then `firebase deploy --only firestore:rules`
- **Lynx branch merged**
  - `chore: move Firestore API key out of source` — `STARTING_GUN_FIRESTORE_API_KEY` env var now required by both the .NET app and the Python export script
  - Brought in detector-to-lynx Windows app + `scripts/export_session_start_times.py`

## Previously Completed (2026-05-02 session, on lynx branch)

- **detector-to-lynx: automated LIF calibration**
  - `LifFileParser.cs` — parses start time from `.lif` header row field 10 (4 decimal places truncated to ms)
  - `CalibrationMatcher.cs` — greedy nearest-neighbour; produces `MatchResult` with `CalibrationRow[]`, `OffsetMs`, residuals
  - `LifDirectoryMonitor.cs` — `FileSystemWatcher` with 300ms debounce; posts to UI thread via `SynchronizationContext`
  - `SavedSettingsManager` — added `LynxResultsDirectory` (string) and `MatchWindowSeconds` (double, default 10.0)
  - `MainForm.Designer.cs` — replaced detection `ListBox` + manual calibration controls with `DataGridView`; added "Lynx Results Directory" group box with Browse + match window field
  - `MainForm.cs` — removed `_calibrationOffsetsMsByClientTimestamp`; added `_lifMonitor`, `_lastMatchResult`, `_rowToDetection`; `RebuildCalibrationGrid()` runs on every detection poll and every `.lif` file change
  - Removed `ComputeCalibrationOffsetMilliseconds()` and its 3 tests (superseded)
  - 10 `LifFileParserTests` + 20 `CalibrationMatcherTests` — all 60 tests pass

## Previously Completed (2026-04-29 session, on lynx branch)

- **Session export utility**
  - Added `scripts/export_session_start_times.py` to export all Firestore detections for a session to a CSV file
  - Uses the existing `sessions/{sessionCode}/detections` schema and prefers `serverCorrectedMillis` when present
  - Writes structured CSV rows with display name, formatted time, raw millis, and Firestore timestamp fallback

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
- Phase 8 (lynx branch, May 2026): detector-to-lynx companion app + session export utility

## Next Up (future sessions)

- Distribute new APK to other users, then deploy `firestore.rules` via `firebase deploy --only firestore:rules`
- Session expiry / cleanup (stale sessions accumulate)
- Data export (CSV/PDF race results)
- Lane/athlete/wind metadata (future enhancement)

## Active Files and Modules

```
app/src/main/java/com/xanderscannell/startinggundetector/
├── MainActivity.kt                    [complete]
├── StartingGunApplication.kt          [complete] ← NEW (Phase 9)
├── audio/
│   └── AudioDetector.kt               [complete]
├── data/
│   ├── RaceModels.kt                  [complete]
│   └── RaceRepository.kt             [complete]
├── device/
│   ├── AuthManager.kt                  [complete] ← NEW (Phase 9)
│   ├── DeviceIdProvider.kt            [complete] ← SLIMMED (Phase 9)
│   └── UserPreferences.kt            [complete]
├── session/
│   └── SessionRepository.kt           [complete]
├── ui/
│   ├── CaptureScreen.kt              [complete]
│   ├── RaceBrowserScreen.kt          [complete]
│   ├── SessionDialog.kt               [complete]
│   ├── StartingGunScreen.kt           [complete]
│   └── theme/Theme.kt                 [complete]
├── utils/
│   └── TimestampFormatter.kt          [complete]
└── viewmodel/
    ├── GunShotViewModel.kt            [complete]
    ├── GunShotViewModelFactory.kt     [complete]
    ├── RaceViewModel.kt               [complete]
    └── RaceViewModelFactory.kt        [complete]

detector-to-lynx/                      [merged from lynx branch]
└── (Windows .NET 8 WinForms app: forwards Firestore detections to FinishLynx)

scripts/
└── export_session_start_times.py      [complete]

firestore.rules                        [complete, awaiting deploy]
firebase.json                          [complete]
```

## Open Questions

- **Session expiry**: Sessions accumulate indefinitely. Consider a TTL or manual cleanup function.
- **Keystore management**: Release keystore was created locally. User should back it up — without it, future updates cannot be signed with the same key.
