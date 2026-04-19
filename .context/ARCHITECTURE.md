# System Architecture

## High-Level Overview

Single-screen Android app. Audio is read on a background thread, processed for transient detection, and the result is pushed to the UI via StateFlow. An optional shared session syncs detections across multiple devices via Firebase Firestore.

```
Microphone
    │
    ▼
AudioDetector (Dispatchers.IO)
    │  RMS + baseline detection
    │  emits: DetectionEvent(nanos, millis)
    ▼
GunShotViewModel
    │  state machine: IDLE / LISTENING
    │  solo mode: updates history locally
    │  session mode: writes to Firestore, stream is source of truth
    ├── SessionRepository ──► Firestore
    │       │  streamDetections() → Flow<List<FirestoreDetection>>
    │       ▼
    │   (stream merged back into detectionHistory)
    ▼ StateFlow<UiState>
StartingGunScreen (Compose, Main thread)
    │  renders state, handles user actions
    ▼
User
```

---

## Components

### MainActivity

**Purpose**: Entry point. Hosts Compose UI, wires DI manually.
**Key file**: `MainActivity.kt`

```kotlin
val deviceId = remember { DeviceIdProvider.getDeviceId(context) }
val sessionRepository = remember { SessionRepository(deviceId) }
val vm = viewModel(factory = GunShotViewModelFactory(deviceId, sessionRepository))
```

---

### StartingGunScreen (Compose UI)

**Purpose**: Renders full single-screen UI from `UiState`. Sends user actions to ViewModel.
**Key file**: `ui/StartingGunScreen.kt`

**UI elements**:
- App title
- `SessionBar` — shows session code + Leave when in session; "Join / Create Session" when not
- `AutoSizeText` live clock — updates every 100ms, shrinks font to prevent overflow
- `StatusLabel` — "TAP TO LISTEN" / pulsing "LISTENING..." / "Last: HH:mm:ss.SSS"
- History `LazyColumn` — rows show `#N | [DeviceBadge] | timestamp | ⭐`
- `DeviceBadge` — coloured pill with last 4 chars of deviceId (primary tint = mine, secondary = others)
- Sensitivity slider (1–10, disabled while LISTENING)
- START LISTENING / STOP buttons

---

### SessionDialog

**Purpose**: Modal dialog for joining or creating a shared session.
**Key file**: `ui/SessionDialog.kt`

- `OutlinedTextField` — 4-char alphanumeric code, auto-uppercase, max 4 chars
- Join button (enabled when `joinCode.length == 4`)
- Create Session `OutlinedButton`
- Loading indicator + error text

---

### GunShotViewModel

**Purpose**: State machine and coordinator.
**Key file**: `viewmodel/GunShotViewModel.kt`

**States**: `IDLE` ↔ `LISTENING`

**Key state**:
- `detectionJob: Job?` — running audio loop
- `streamJob: Job?` — Firestore snapshot listener
- `starredKeys: MutableSet<String>` — local star state (survives Firestore re-emissions); key = `"$deviceId:$timestamp"`

**Detection routing**:
- Solo mode: `onDetected` → append `DetectionEntry` to history locally
- Session mode: `onDetected` → `sessionRepository.writeDetection()` → Firestore stream updates history

**Actions**: `startListening`, `stopListening`, `createSession`, `joinSession`, `leaveSession`, `toggleStar`, `clearHistory`, `setSensitivity`, `showSessionDialog`, `dismissSessionDialog`

---

### GunShotViewModelFactory

**Purpose**: Manual `ViewModelProvider.Factory` — passes `deviceId` and `sessionRepository` into `GunShotViewModel`.
**Key file**: `viewmodel/GunShotViewModelFactory.kt`

---

### AudioDetector

**Purpose**: Encapsulates all audio recording and gunshot detection logic. Runs on `Dispatchers.IO`. Emits via callback.
**Key file**: `audio/AudioDetector.kt`

**Configuration**:
- Source: `MediaRecorder.AudioSource.VOICE_RECOGNITION`
- Sample rate: 44100 Hz, mono, PCM_16BIT
- Buffer: `getMinBufferSize() * 2`

**Detection algorithm**:
1. Read buffer in tight loop
2. Compute RMS of buffer
3. Maintain rolling average baseline (~44100 samples ≈ 1 second)
4. Trigger: `currentRMS > baseline × multiplier` AND `currentRMS > ABSOLUTE_MIN_RMS (500.0)`
5. Sub-buffer peak detection: find peak sample index, back-calculate ms offset
6. 2500ms cooldown after trigger

**Sensitivity slider → multiplier**: `multiplier = 20f - (normalized * 16f)` → slider 1 = 20× (harder to trigger), slider 10 = 4× (easier)

---

### SessionRepository

**Purpose**: Firestore CRUD and real-time streaming for shared sessions.
**Key file**: `session/SessionRepository.kt`

**Firestore path**: `sessions/{sessionCode}/detections/{autoId}`

**Document fields**:
- `timestamp: String` — `HH:mm:ss.SSS`
- `deviceId: String` — UUID
- `clientTimestamp: Long` — millis for ordering before server timestamp resolves
- `createdAt: FieldValue.serverTimestamp()` — for server-side ordering

**Operations**:
- `createSession()` — generates random 4-char alphanumeric code, writes session doc, retries up to 5× on collision
- `joinSession(code)` — checks if session doc exists, returns `Boolean`
- `writeDetection(sessionCode, timestamp)` — adds detection doc
- `streamDetections(sessionCode)` — `callbackFlow` wrapping Firestore snapshot listener, ordered by `clientTimestamp desc`

---

### DeviceIdProvider

**Purpose**: Stable anonymous device identity.
**Key file**: `device/DeviceIdProvider.kt`

- `getDeviceId(context)` — reads UUID from `SharedPreferences("gun_detector_prefs")`; generates and persists on first call
- `shortId(deviceId)` — `deviceId.takeLast(4).uppercase()` — used for `DeviceBadge` display

---

### TimestampFormatter

**Purpose**: Formats wall-clock millis to `HH:mm:ss.SSS` (local time).
**Key file**: `utils/TimestampFormatter.kt`

---

## Data Flow — Solo Mode

1. User taps START → ViewModel: IDLE → LISTENING, starts `AudioDetector`
2. `AudioDetector` reads PCM on IO dispatcher
3. Transient detected → `DetectionEvent(elapsedNanos, wallMillis)` emitted
4. ViewModel formats timestamp → prepends `DetectionEntry` to history
5. Compose recomposes with updated `detectionHistory`

## Data Flow — Session Mode

1. User joins/creates session → `startStream(code)` subscribes to Firestore
2. `AudioDetector` fires → ViewModel writes detection to Firestore (does NOT touch local history)
3. Firestore snapshot listener emits updated list → ViewModel maps to `DetectionEntry` list, re-applies `starredKeys`
4. `StateFlow` updates → Compose recomposes
5. All devices in the session see the same ordered history in real time

---

## External Dependencies

| Dependency | Purpose |
|-----------|---------|
| `androidx.activity:activity-compose` | Compose in Activity |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel in Compose |
| `androidx.compose.material3` | Material 3 components |
| `androidx.compose.material.icons.extended` | Star icons |
| `kotlinx-coroutines-android` | Coroutines + Android dispatcher |
| Firebase BOM 33.1.1 | Bill of materials for Firebase |
| `firebase-firestore-ktx` | Firestore SDK with Kotlin extensions |

## Key Design Patterns

- **MVVM**: `AudioDetector` → `GunShotViewModel` → Compose UI; no business logic in Activity or Compose
- **StateFlow**: single source of truth for UI state, observed via `collectAsState()`
- **Coroutines**: audio loop on `Dispatchers.IO`, UI updates on `Dispatchers.Main`
- **Immutable UI state**: `UiState` is a data class; ViewModel emits new instances, never mutates
- **Firestore as source of truth**: in session mode, the stream owns the history list — local detection writes go through Firestore, not directly to state
- **Local star overlay**: `starredKeys` set in ViewModel re-applied on each stream emission so star state survives Firestore re-emissions
- **Manual DI**: no Hilt — `ViewModelFactory` passes `deviceId` and `SessionRepository` explicitly

## Technology Decisions

See [DECISIONS.md](DECISIONS.md) for rationale behind key choices.
