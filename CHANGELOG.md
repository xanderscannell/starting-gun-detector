# Changelog

## v1.5.0 — 2026-04-21

### Added
- **Background listening** — app continues detecting while the screen is locked via a foreground service with a persistent notification
- **Latency calibration** — fire the gun and enter the real time from a reference clock for one or more samples; the app computes and applies the average offset automatically
- Screen stays on while the app is in the foreground

### Changed
- Sidebar subtitle shows device short ID (e.g. `ID: A3F9`) when no username is set, instead of the generic "Detector"
- Placeholder text in Session tab is now italicised and dimmed to make it more obviously a hint
- Latency offset is now a directly editable text field in addition to the +/− buttons
- Removed the 500ms cap on the latency offset

---

## v1.4.0 — 2026-04-21

### Added
- **Race file persistence** — save a race (start times, video, finish splits) as a single file that can be reopened and re-edited at any time
- **Race browser** — three-level drill-down: Meet → Event → Race, with delete capability
- **Multiple candidate start times per race** — audio detections, session detections, and manual entry all appear as selectable start times; pick the correct one post-race
- **Manual start time entry** — enter a precise HH:mm:ss.SSS time for solo use without a session
- **Save Race dialog** — prompts for meet name (with autocomplete from previous races), event name, and date
- Orphaned video cleanup on app startup (captures older than 24h with no associated race are deleted)
- Previous capture files deleted when starting a new recording to prevent storage accumulation

### Changed
- Star/favourite system replaced by official start time selection — splits are computed against whichever start time is selected
- CaptureScreen split into two modes: new recording scrubber (with save button) and saved race scrubber (with manual start time entry)
- RACES page added to sidebar navigation

### Technical
- Race data stored as JSON + video per race directory (`races/<uuid>/race.json` + `video.mp4`)
- `kotlinx-serialization-json` added for persistence (no Room/KSP overhead)
- New files: `RaceModels.kt`, `RaceRepository.kt`, `RaceViewModel.kt`, `RaceViewModelFactory.kt`, `RaceBrowserScreen.kt`

---

## v1.3.0 — 2026-04-20

### Added
- **Finish line capture** — CaptureScreen with live camera preview, video recording, and ExoPlayer-based frame scrubber for identifying exact finish times
- Frame scrubber seeks directly during drag (CLOSEST_SYNC for speed, EXACT on release for precision); frame timestamp computed from `recordingStartMillis + serverOffset + seekPositionMs`
- Server clock sync via Firestore round-trip — calibrates automatically on session join/create and after each recording; warns if clock is not yet calibrated
- Live listening status indicator — green dot next to a device's name while actively listening; grey when stopped
- Listening status written to Firestore on start/stop/leave so all connected devices see it in real time
- Your own device always sorted to the top of the members list and shown in primary colour

### Changed
- Session page members list replaces raw detection history — one row per device with name and last detected time
- Devices appear in the session list as soon as they join, even before making a detection
- "Devices: N" header shows live count of connected devices

### Fixed (Timing Accuracy)
- **Detection millis now server-corrected before Firestore write** — eliminates network write latency from reported gun time (TIMING-004 + TIMING-001)
- `recordingStartMillis` captured on `VideoRecordEvent.Start` instead of before `.start()` — removes variable startup delay from split calculations (TIMING-002)
- Multi-sample server offset calibration (5 samples, shortest RTT wins) for tighter clock sync (TIMING-005)
- Server offset re-calibrated on every RECORD press to catch drift (TIMING-007)
- Dynamic frame step derived from actual video fps instead of hardcoded value (TIMING-006)
- Thread-safe `TimestampFormatter` rewritten with `java.time` — fixes potential race condition on concurrent format calls (TIMING-008)
- ExoPlayer PTS readback after seek for accurate frame position (TIMING-009)
- **Overall worst-case split error reduced from ~550ms to ~130ms**

---

## v1.2.0 — 2026-04-20

### Fixed
- Buffer offset calculation was inverted — timestamp was computed relative to the start of the buffer rather than the end, causing timestamps to be reported up to one full buffer duration too early (~40ms systematic error)

### Added
- Scrolling loudness visualizer above the Start/Stop button — bars driven by live microphone RMS, detection events highlighted in red, tiny idle bars scroll when not listening
- Manual latency offset control (±500ms, 10ms steps) to compensate for device hardware input latency
- Persistent username setting — shown as the session badge label instead of the short device ID
- Latency offset and username are both persisted across app restarts via SharedPreferences
- Sidebar navigation (hamburger icon) with Listen, Capture, and Session pages
- Gear icon opens a settings sheet with sensitivity and latency offset controls
- Capture page placeholder for future finish-line camera sync feature
- Session page — join/create session and username setting now live on a dedicated page instead of a dialog

### Changed
- Live clock refreshes at ~60fps (16ms) instead of 100ms for smoother millisecond display
- Session detection badges now show the sender's username instead of their short device ID; falls back to short ID if no name is set (also backwards-compatible with old Firestore documents that lack a display name)
- Sensitivity control moved to a collapsible quick strip at the bottom of the Listen screen; full control also available in the settings sheet
- Latency offset control moved from the Listen screen to the settings sheet
- Username field moved from the Listen screen to the Session page
- Status label changed from "TAP TO LISTEN" to "READY"
- Clock is centred on the Listen screen until the first detection, then moves to the top above the history list

---

## v1.1.0 — 2026-04-19

### Fixed
- App failed to detect gunshots in outdoor environments due to phone AGC (Automatic Gain Control) compressing the gunshot amplitude to be indistinguishable from wind noise

### Changed
- Audio source switched from `VOICE_RECOGNITION` to `UNPROCESSED` — bypasses AGC and all phone post-processing, restoring true amplitude difference between gunshots and ambient noise

### Added
- Transient duration confirmation window: candidate detections must show RMS drop back toward baseline within ~3 buffers (~5–10ms) to be confirmed; sustained-loud sounds (wind gusts) are rejected silently
- Timestamp is still captured at the moment of the spike — confirmation window adds no latency to reported times

---

## v1.0.0 — 2026-04-19

### Added
- Gunshot detection via microphone using RMS + rolling baseline algorithm
- Sub-buffer peak detection for millisecond-accurate timestamps
- Detection history with star toggle to mark correct times
- Sensitivity slider (1–10)
- Shared session via Firebase Firestore — multiple devices collect a collective detection history in real time
- Device badges in history (last 4 chars of device UUID, colour-coded by owner)
- Dark theme, Material 3
