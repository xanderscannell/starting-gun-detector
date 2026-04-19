# System Architecture

## High-Level Overview

Single-screen Android app. Audio is read on a background thread, processed for transient detection, and the result is pushed to the UI via StateFlow.

```
Microphone
    │
    ▼
AudioDetector (Dispatchers.IO)
    │  RMS + baseline detection
    │  emits: DetectionEvent(nanos, millis)
    ▼
GunShotViewModel
    │  state machine: IDLE / LISTENING / DETECTED
    │  formats timestamp via TimestampFormatter
    ▼ StateFlow<UiState>
StartingGunScreen (Compose, Main thread)
    │  renders state, handles user actions
    ▼
User
```

---

## Components

### MainActivity

**Purpose**: Entry point. Hosts the Compose UI, passes the ViewModel.
**Key files**: `app/src/main/java/com/example/startinggundetector/MainActivity.kt`
**Notes**: Minimal — no business logic here.

---

### StartingGunScreen (Compose UI)

**Purpose**: Renders the full single-screen UI based on `UiState`. Sends user actions (start, stop, reset, sensitivity change) back to the ViewModel.
**Key files**: `ui/StartingGunScreen.kt`

**UI elements**:
- App title
- Live clock (updates every 100ms, stops when DETECTED)
- Status indicator with pulse animation (LISTENING state)
- Detected timestamp (HH:mm:ss.SSS, large bold, visible only in DETECTED state)
- Sensitivity slider (1–10, disabled while LISTENING)
- Contextual buttons: START / STOP / RESET

---

### GunShotViewModel

**Purpose**: State machine and coordinator. Starts/stops `AudioDetector`, receives detection events, exposes `StateFlow<UiState>` to UI.
**Key files**: `viewmodel/GunShotViewModel.kt`

**States**:
```
IDLE ──[start]──► LISTENING ──[detected]──► DETECTED
  ▲                   │                        │
  └────[stop]─────────┘         [reset]────────┘
```

**Interfaces**:
- Input: user actions from UI, `DetectionEvent` from `AudioDetector`
- Output: `StateFlow<UiState>` consumed by Compose

---

### AudioDetector

**Purpose**: Encapsulates all audio recording and gunshot detection logic. Runs on `Dispatchers.IO`. Emits detection events via `SharedFlow` or callback.
**Key files**: `audio/AudioDetector.kt`

**Configuration**:
- Source: `MediaRecorder.AudioSource.VOICE_RECOGNITION`
- Sample rate: 44100 Hz
- Channels: `CHANNEL_IN_MONO`
- Format: `ENCODING_PCM_16BIT`
- Buffer: `getMinBufferSize() * 2`

**Detection algorithm**:
1. Read buffer in a tight loop
2. Compute RMS of buffer
3. Maintain rolling average baseline (~1 second window, ~44100 samples)
4. Trigger when: `currentRMS > baseline × multiplier` AND `currentRMS > absoluteMinThreshold`
5. On trigger: record `SystemClock.elapsedRealtimeNanos()` + `System.currentTimeMillis()`; find peak sample index within buffer and back-calculate sub-buffer offset for improved precision
6. Enforce ~2–3 second cooldown

**Interfaces**:
- Input: sensitivity multiplier (Float, from ViewModel)
- Output: `DetectionEvent(elapsedNanos: Long, wallMillis: Long)`

---

### TimestampFormatter

**Purpose**: Utility — converts a wall-clock millis value to `HH:mm:ss.SSS` string.
**Key files**: `utils/TimestampFormatter.kt`
**Notes**: Pure function, no dependencies.

---

## Data Flow

1. User taps START → ViewModel transitions to LISTENING, calls `AudioDetector.start()`
2. `AudioDetector` reads PCM buffers on IO dispatcher in a loop
3. Each buffer: compute RMS → compare to rolling baseline → check threshold
4. Transient detected → `DetectionEvent` emitted
5. ViewModel receives event → formats timestamp via `TimestampFormatter` → transitions to DETECTED, updates `StateFlow`
6. Compose recomposes with DETECTED state, renders timestamp
7. User taps RESET → ViewModel transitions to IDLE, calls `AudioDetector.stop()`

## External Dependencies

| Dependency | Purpose |
|-----------|---------|
| `androidx.activity:activity-compose` | Compose in Activity |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel in Compose |
| `androidx.compose.ui:ui` | Compose UI toolkit |
| `androidx.compose.material3:material3` | Material 3 components |
| `kotlinx-coroutines-android` | Coroutines + Android Main dispatcher |

## Key Design Patterns

- **MVVM**: `AudioDetector` → `GunShotViewModel` → Compose UI; no business logic in Activity or Compose
- **StateFlow**: single source of truth for UI state, observed via `collectAsState()`
- **Coroutines**: audio loop on `Dispatchers.IO`, UI updates posted to `Dispatchers.Main`
- **Immutable UI state**: `UiState` is a sealed class/data class; ViewModel emits new instances, never mutates

## Technology Decisions

See [DECISIONS.md](DECISIONS.md) for rationale behind key choices.
