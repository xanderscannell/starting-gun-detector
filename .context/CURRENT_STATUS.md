# Project Status

**Last updated**: 2026-05-13

## Current Position

**Phase**: Phase 9 in progress — Firestore security hardening (rules deployment pending)
**Progress**: Auth code shipped and verified on device; awaiting `firebase deploy --only firestore:rules` once all old app installs are updated. Lynx branch merged into master.

## Recently Completed (2026-05-13 session, later)

- **Issue #1: Show session code in the top bar**
  - Added optional `sessionCode: String?` parameter to `AppTopBar` in `StartingGunScreen.kt`
  - Code renders next to the hamburger icon in `AppMonoFont`, bold, 20sp, brand red (`MaterialTheme.colorScheme.primary`)
  - Hidden when not in a session; drawer footer kept as a secondary indicator (it's covered by the top bar code whenever the drawer is closed)
- **Issue #2: Readable session codes (font + alphabet)**
  - Bundled JetBrains Mono (Light/Regular/Medium/Bold) into `app/src/main/res/font/`
  - New `ui/theme/Type.kt` exporting `AppMonoFont: FontFamily`
  - Replaced every `FontFamily.Monospace` with `AppMonoFont` across 5 UI files (CalibrationDialog, CaptureScreen, RaceBrowserScreen, SessionDialog, StartingGunScreen)
  - Added explicit mono+bold to two display sites that had no fontFamily override: the navigation drawer "Session: XXXX" footer and the join-session input placeholder
  - **Tightened session-code alphabet** in `SessionRepository.codeChars` to `ACDEFGHJKMNPQRTUVWXY34679` (25 chars, 390K combos). Excludes `O, 0, I, 1, L, B, 8, S, 5, Z, 2` — fixes both visual and aural ambiguity (see ADR-010)
- **Issue #3: Persistent sensitivity setting**
  - Added `sensitivity: Float` property to `device/UserPreferences.kt` plus a public `DEFAULT_SENSITIVITY = 7f` constant
  - `GunShotViewModel` now reads `userPreferences.sensitivity` into the initial `UiState` and writes through on every `setSensitivity()` call — same pattern as `latencyOffsetMs` and `username`
  - The "no sensitivity changes while listening" guard is preserved (no prefs write when locked)
- **Issue #4: Listening service no longer survives swipe-away**
  - Added `onTaskRemoved()` override to `audio/ListeningService.kt` that calls `stopSelf()`
  - Behavior: home button / screen off / app switch still keep listening; swiping the app away from recents now stops cleanly (notification dismissed, mic released). Force-stop from Settings unchanged.
  - **Known follow-up:** the session-member's Firestore `isListening` flag stays `true` (stale) after swipe-away, because the ViewModel's `viewModelScope` is cancelled inside `onCleared` and can't reliably emit the false-write before process death. Tracked as issue #5 (heartbeat-based presence).

## Recently Completed (2026-05-13 session, earlier)

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
- **Heartbeat-based presence** (issue #5 — proper fix for the stale `isListening` flag noted in issue #4). Each listening member writes `lastSeen = serverTimestamp` every N seconds; readers (lynx desktop, other phones) treat anything older than ~3× the heartbeat interval as offline. Requires: heartbeat coroutine in `ListeningService` or `GunShotViewModel`, rules update to allow heartbeat writes on own member doc, lynx-side read change to use `lastSeen` rather than `isListening`. Estimate ~50-100 lines + rules + desktop change.
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
│   └── theme/
│       ├── Theme.kt                    [complete]
│       └── Type.kt                     [complete] ← NEW (issue #2)
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
