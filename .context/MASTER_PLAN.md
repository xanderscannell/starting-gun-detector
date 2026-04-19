# Master Implementation Plan

## Project: Starting Gun Detector

## Overview

A simple Android app that listens via microphone for a gunshot (sharp audio transient), captures the exact wall-clock time at the moment of detection, and displays it clearly. Primary use case: track & field race timing from a starter pistol.

## Success Criteria

- [ ] App correctly detects a starter pistol shot via microphone
- [ ] Captured timestamp is accurate to within the hardware latency of the device
- [ ] UI is clear, readable, and usable on a real phone at a race
- [ ] APK builds and installs cleanly on API 26+ devices

---

## Phase 1: Project Scaffold

**Goal**: A compilable Android project with permissions, dependencies, and a blank Compose screen.

### 1.1 Android Project Setup
- [ ] Create Android project (Kotlin, Kotlin DSL, min SDK 26, target SDK 34)
- [ ] Configure `build.gradle.kts` with all required dependencies
- [ ] Add `RECORD_AUDIO` permission to `AndroidManifest.xml`

### 1.2 Basic Compose Skeleton
- [ ] Create `MainActivity.kt` hosting Compose
- [ ] Create `ui/theme/Theme.kt`
- [ ] Create empty `StartingGunScreen.kt` (placeholder UI, no logic)

### Phase 1 Milestones
- [ ] Project compiles and runs on emulator or device
- [ ] Blank screen displays without crashes

---

## Phase 2: Audio Engine

**Goal**: A working `AudioDetector` that reliably detects a gunshot-level transient from the microphone.

### 2.1 AudioRecord Setup
- [ ] Create `AudioDetector.kt` with `AudioRecord` initialization
- [ ] Use `VOICE_RECOGNITION` source, 44100 Hz, mono, PCM_16BIT
- [ ] Buffer size: `getMinBufferSize() * 2`
- [ ] Run audio read loop on `Dispatchers.IO` coroutine

### 2.2 Detection Algorithm
- [ ] Compute RMS for each buffer
- [ ] Maintain rolling average baseline (~1 second window)
- [ ] Trigger when RMS exceeds baseline × configurable multiplier AND exceeds absolute minimum
- [ ] On trigger: capture `SystemClock.elapsedRealtimeNanos()` + `System.currentTimeMillis()`
- [ ] Sub-buffer peak detection: find peak sample index, back-calculate offset for improved precision
- [ ] Enforce ~2-3 second cooldown after trigger

### 2.3 TimestampFormatter
- [ ] Create `TimestampFormatter.kt` utility
- [ ] Convert millis to `HH:mm:ss.SSS` format

### Phase 2 Milestones
- [ ] `AudioDetector` emits a detection event when a sharp loud sound occurs
- [ ] No false triggers from normal speech or ambient noise at default sensitivity

---

## Phase 3: ViewModel + UI

**Goal**: Full working app — ViewModel wiring, state machine, and complete Compose UI.

### 3.1 ViewModel
- [ ] Create `GunShotViewModel.kt`
- [ ] Define states: `IDLE`, `LISTENING`, `DETECTED`
- [ ] Expose `StateFlow<UiState>` to UI
- [ ] Start/stop `AudioDetector` on user action
- [ ] Receive detection events, update state with formatted timestamp

### 3.2 Compose UI
- [ ] App title ("Starting Gun Detector")
- [ ] Large live clock (updates every 100ms while idle)
- [ ] Status indicator with pulse animation while listening
- [ ] Detected timestamp display (large, bold, `HH:mm:ss.SSS`)
- [ ] Sensitivity slider (1–10, only adjustable while not listening)
- [ ] START LISTENING / STOP / RESET buttons per state
- [ ] Runtime permission request flow (`ActivityResultContracts.RequestPermission`)
- [ ] Permission denied dialog with link to settings

### 3.3 Error Handling
- [ ] `AudioRecord` init failure: show error message
- [ ] No detection after 60 seconds: show hint

### Phase 3 Milestones
- [ ] Full state machine works: IDLE → LISTENING → DETECTED → IDLE
- [ ] Detected timestamp renders correctly after a clap/gunshot test
- [ ] Sensitivity slider changes detection behavior

---

## Phase 4: Polish + Release

**Goal**: Tested, refined, ready to use at a real race.

### 4.1 Testing
- [ ] Test on at least one real device
- [ ] Test edge cases: very loud environment, quiet environment, no permission
- [ ] Verify cooldown prevents double-trigger

### 4.2 Latency Calibration (Optional)
- [ ] Add a way to measure and display device-specific audio latency offset
- [ ] Allow user to enter a manual offset correction (ms)

### 4.3 Build
- [ ] Generate signed release APK
- [ ] Verify install on API 26 device

### Phase 4 Milestones
- [ ] App works reliably at a real track & field event
- [ ] Signed APK produced

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
