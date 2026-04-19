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
