# Master Implementation Plan

## Project: Starting Gun Detector

## Overview

A simple Android app that listens via microphone for a gunshot (sharp audio transient), captures the exact wall-clock time at the moment of detection, and displays it clearly. Primary use case: track & field race timing from a starter pistol.

## Success Criteria

- [x] App correctly detects a starter pistol shot via microphone
- [x] Captured timestamp is accurate to within the hardware latency of the device
- [x] UI is clear, readable, and usable on a real phone at a race
- [x] APK builds and installs cleanly on API 26+ devices

---

## Phase 1: Project Scaffold

**Goal**: A compilable Android project with permissions, dependencies, and a blank Compose screen.

### 1.1 Android Project Setup
- [x] Create Android project (Kotlin, Kotlin DSL, min SDK 26, target SDK 34)
- [x] Configure `build.gradle.kts` with all required dependencies
- [x] Add `RECORD_AUDIO` permission to `AndroidManifest.xml`

### 1.2 Basic Compose Skeleton
- [x] Create `MainActivity.kt` hosting Compose
- [x] Create `ui/theme/Theme.kt`
- [x] Create empty `StartingGunScreen.kt` (placeholder UI, no logic)

### Phase 1 Milestones
- [x] Project compiles and runs on emulator or device
- [x] Blank screen displays without crashes

---

## Phase 2: Audio Engine

**Goal**: A working `AudioDetector` that reliably detects a gunshot-level transient from the microphone.

### 2.1 AudioRecord Setup
- [x] Create `AudioDetector.kt` with `AudioRecord` initialization
- [x] Use `VOICE_RECOGNITION` source, 44100 Hz, mono, PCM_16BIT
- [x] Buffer size: `getMinBufferSize() * 2`
- [x] Run audio read loop on `Dispatchers.IO` coroutine

### 2.2 Detection Algorithm
- [x] Compute RMS for each buffer
- [x] Maintain rolling average baseline (~1 second window)
- [x] Trigger when RMS exceeds baseline × configurable multiplier AND exceeds absolute minimum
- [x] On trigger: capture `SystemClock.elapsedRealtimeNanos()` + `System.currentTimeMillis()`
- [x] Sub-buffer peak detection: find peak sample index, back-calculate offset for improved precision
- [x] Enforce ~2-3 second cooldown after trigger

### 2.3 TimestampFormatter
- [x] Create `TimestampFormatter.kt` utility
- [x] Convert millis to `HH:mm:ss.SSS` format

### Phase 2 Milestones
- [x] `AudioDetector` emits a detection event when a sharp loud sound occurs
- [x] No false triggers from normal speech or ambient noise at default sensitivity

---

## Phase 3: ViewModel + UI

**Goal**: Full working app — ViewModel wiring, state machine, and complete Compose UI.

### 3.1 ViewModel
- [x] Create `GunShotViewModel.kt`
- [x] Define states: `IDLE`, `LISTENING`
- [x] Expose `StateFlow<UiState>` to UI
- [x] Start/stop `AudioDetector` on user action
- [x] Receive detection events, update state with formatted timestamp

### 3.2 Compose UI
- [x] App title ("Starting Gun Detector")
- [x] Large live clock (updates every 100ms, auto-sizing)
- [x] Status indicator with pulse animation while listening
- [x] Detection history list with star toggle
- [x] Sensitivity slider (1–10, only adjustable while not listening)
- [x] START LISTENING / STOP buttons per state
- [x] Runtime permission request flow (`ActivityResultContracts.RequestPermission`)
- [x] Permission denied dialog with link to settings

### 3.3 Error Handling
- [x] `AudioRecord` init failure: show error message

### Phase 3 Milestones
- [x] Full state machine works: IDLE → LISTENING → (detection) → IDLE
- [x] Detected timestamp renders correctly after a clap/gunshot test
- [x] Sensitivity slider changes detection behavior

---

## Phase 4: Polish + Release

**Goal**: Tested, refined, ready to use at a real race.

### 4.1 Testing
- [x] Test on at least one real device
- [x] Test edge cases: no permission
- [x] Verify cooldown prevents double-trigger

### 4.2 Latency Calibration (Optional)
- [ ] Add a way to measure and display device-specific audio latency offset
- [ ] Allow user to enter a manual offset correction (ms)

### 4.3 Build
- [x] Generate signed release APK
- [x] Verify install on API 26+ device

### 4.4 Shared Session (Firebase Firestore) — Added
- [x] `DeviceIdProvider.kt` — UUID in SharedPreferences, `shortId()` helper
- [x] `SessionRepository.kt` — create/join/write/stream via Firestore
- [x] `GunShotViewModelFactory.kt` — manual DI
- [x] `SessionDialog.kt` — join/create UI
- [x] `SessionBar` + `DeviceBadge` composables in `StartingGunScreen`
- [x] Solo mode preserved unchanged

### Phase 4 Milestones
- [x] App works reliably on a real device
- [x] Signed APK produced
- [x] Shared session feature ships and syncs across devices in real time

---

---

## Phase 5: Finish Line Capture (Video-Based)

**Goal**: Record video at the finish line with a live clock overlay. After the race, scrub frame-by-frame to find the exact crossing moment and compute the split time against the session's gun timestamp.

**Concept**: Professional photo finish approach adapted for consumer hardware. Clock overlay burned into / overlaid on video means the timestamp is readable directly from the frame — no PTS math required. Precision bounded by frame rate (60fps = ~16ms) and NTP clock accuracy (~10–50ms).

### 5.1 Camera preview in Capture tab
- [x] Add CameraX dependency (`camera-camera2`, `camera-lifecycle`, `camera-view`)
- [x] Add `CAMERA` permission to `AndroidManifest.xml`
- [x] Wire up Capture tab with a live `PreviewView` via Compose interop
- [x] Handle camera permission request flow

### 5.2 Record video to local storage
- [x] Add Record / Stop button to Capture tab
- [x] Capture `recordingStartMillis = System.currentTimeMillis()` at recording start
- [x] Save MP4 to app-private storage via `VideoCapture<Recorder>`
- [x] Store `recordingStartMillis` alongside the file reference

### 5.3 Server offset calibration
- [x] At session start, finish phone performs a Firestore round-trip: `serverOffset = serverTime − clientTime`
- [x] Store `serverOffset` in ViewModel for the duration of the session
- [x] Warn user if calibration hasn't been performed before recording starts

### 5.4 Clock overlay on preview
- [x] Render live clock composable over the `PreviewView`
- [x] Clock displays server-relative time: `System.currentTimeMillis() + serverOffset`
- [x] Updates every 16ms (aligned to 60fps)

### 5.5 Frame scrubber playback
- [x] Load recorded video with ExoPlayer
- [x] Custom scrubber UI — slider seeks by frame (step = `1000ms / fps`)
- [x] Display current frame's server-relative timestamp: `recordingStartMillis + serverOffset + seekPositionMs`
- [x] Smooth single-frame stepping (forward/back buttons)

### 5.6 Post-race split calculation
- [ ] Session screen lists all gun detections with their Firestore `serverTimestamp`
- [ ] User selects the official detection (existing star feature covers this)
- [ ] User scrubs video to crossing frame and taps "Set Finish"
- [ ] Split = `server_relative_frame_time − T_gun_serverTimestamp`
- [ ] Display and allow saving the result

### Phase 5 Milestones
- [ ] Camera preview renders in Capture tab
- [ ] Video records and saves to device
- [ ] Clock overlay visible in preview and readable in playback
- [ ] Frame scrubber steps frame-by-frame accurately
- [ ] Split time correctly computed from gun timestamp

---

## Phase Dependencies

```
Phase 1 (Scaffold) ──► Phase 2 (Audio Engine) ──► Phase 3 (ViewModel + UI) ──► Phase 4 (Polish)
```

## Risk Areas

| Risk | Impact | Mitigation |
|------|--------|------------|
| Device audio latency varies widely (10ms–50ms+) | High | Document limitation; offer manual offset in Phase 4 |
| False triggers from ambient noise | High | Rolling baseline + absolute minimum threshold + cooldown |
| `AudioRecord` init failure on some devices | Medium | Handle gracefully with error state in ViewModel |
| Oboe NDK path complexity | Low | Deferred to stretch goal; not in scope for Phases 1–4 |
