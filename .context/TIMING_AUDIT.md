# Timing System Audit

**Date**: 2026-04-20
**Scope**: Full signal chain from gunshot to displayed split time
**Goal**: Identify every source of latency, desynchronization, or error that could affect race timing accuracy

---

## How the Split is Computed

```
split = (recordingStartMillis + serverOffset + sliderPosition) − gunServerTimestampMillis
```

| Term | Source | Meaning |
|------|--------|---------|
| `recordingStartMillis` | `System.currentTimeMillis()` captured before `.start()` | Finish phone's local clock at recording start |
| `serverOffset` | `measureServerOffset()` round-trip calibration | Converts finish phone clock to Firestore server time |
| `sliderPosition` | ExoPlayer seek position in ms | Time into the video of the selected frame |
| `gunServerTimestampMillis` | Firestore `createdAt` field on detection doc | Firestore server time when it **received** the gun detection write |

Both sides are expressed in Firestore server time. The problem is that neither side's timestamp accurately represents the real-world moment it claims to.

---

## Signal Chain — Gun Side

```
1. Gun fires                                              ← real-world T_gun
2. Sound propagates to phone mic                          ← +distance/343 m/s
3. Sound hits mic diaphragm
4. ADC + AudioRecord pipeline fills buffer                ← +20-100ms (audio input latency)
5. AudioRecord.read() returns
6. RMS computed, threshold check passes
7. Sub-buffer peak index found, wallMillis back-calculated ← corrects ~5-20ms of step 4
8. Confirmation window (3 buffers read)                   ← timestamp pre-captured, no timing impact
9. onDetected callback fires with DetectionEvent
10. ViewModel formats timestamp string, launches coroutine
11. sessionRepository.writeDetection() called              ← clientTimestamp captured HERE (not at step 7)
12. Firestore network write in flight                      ← +50-200ms network latency
13. Firestore server assigns createdAt serverTimestamp     ← THIS is gunServerTimestampMillis
```

**Result**: `gunServerTimestampMillis` is late by steps 2+4+10+11+12 minus the sub-buffer correction from step 7. Net: **~70-300ms late**, making splits too short.

## Signal Chain — Finish Side

```
1. RECORD button pressed
2. recordingStartMillis = System.currentTimeMillis()      ← captured BEFORE .start()
3. prepareRecording().start() called
4. Camera pipeline warms up                                ← 50-200ms gap
5. First video frame captured                              ← actual recording start
6. ... race happens ...
7. Runner crosses line, frame captured                     ← real-world T_finish
8. Recording stopped, calibration runs
9. User scrubs to crossing frame
10. frameTime = recordingStartMillis + serverOffset + sliderPosition
```

**Result**: `recordingStartMillis` is captured 50-200ms before the first frame, making every frame's computed timestamp **systematically early**, and splits too long.

---

## Issues — Ordered by Impact

### TIMING-001: T_gun uses Firestore write receipt time, not detection time

| | |
|---|---|
| **Severity** | CRITICAL |
| **Impact** | Splits systematically 50-200ms too short |
| **Files** | `SessionRepository.kt:57-68`, `GunShotViewModel.kt:101-109` |
| **Root cause** | `gunServerTimestampMillis` comes from Firestore `createdAt` — the time the server processed the write, not the time the gun was detected. The actual detection time (`event.wallMillis`) is formatted into a string and the raw millis discarded. |
| **Fix** | Gun phone already calibrates `serverOffsetMs`. Write `serverCorrectedMillis = event.wallMillis + latencyOffset + serverOffsetMs` as a numeric field on the detection document. Use this for split calculations instead of `createdAt`. |
| **Complexity** | Low — add a field to the write, read it back in the stream, pass it through to the split calculation. |

---

### TIMING-002: `recordingStartMillis` captured before recording actually starts

| | |
|---|---|
| **Severity** | HIGH |
| **Impact** | Splits systematically 50-200ms too long |
| **Files** | `CaptureScreen.kt:275-278` |
| **Root cause** | `System.currentTimeMillis()` is called before `prepareRecording().start()`. Camera pipeline warmup means the first frame arrives 50-200ms later. Every frame timestamp is early by this gap. |
| **Fix** | Capture `recordingStartMillis` inside the `VideoRecordEvent.Start` callback (or first `VideoRecordEvent.Status`), not before `.start()`. |
| **Complexity** | Low — move the timestamp capture into the event listener. |

---

### TIMING-003: Audio input latency is uncorrected and offset has no effect on splits

| | |
|---|---|
| **Severity** | HIGH |
| **Impact** | 20-100ms systematic error on T_gun (device-dependent) |
| **Files** | `AudioDetector.kt:116-118`, `GunShotViewModel.kt:90-91` |
| **Root cause** | Sub-buffer peak correction only accounts for position within the buffer, not the fundamental audio pipeline latency (mic → ADC → AudioRecord → app). The `latencyOffsetMs` user setting adjusts the display string but is NOT applied to `serverTimestampMillis` in session mode, so it has zero effect on split calculations. |
| **Fix (immediate)** | Apply `latencyOffsetMs` when computing the server-corrected detection time for Firestore (i.e., as part of TIMING-001 fix). |
| **Fix (future)** | Use `AudioRecord.getTimestamp()` (API 24+) or the AAudio latency API to auto-measure device-specific input latency. See Phase 4.2 in MASTER_PLAN. |
| **Complexity** | Immediate fix: trivial (included in TIMING-001). Auto-measure: moderate. |

---

### TIMING-004: `writeDetection` loses raw detection millis

| | |
|---|---|
| **Severity** | HIGH |
| **Impact** | Prevents accurate split calculation; blocks TIMING-001 fix |
| **Files** | `SessionRepository.kt:57-68`, `GunShotViewModel.kt:101-109` |
| **Root cause** | `writeDetection()` receives only a formatted string (`timestamp: String`). The raw `event.wallMillis` is discarded after formatting. `clientTimestamp` is a fresh `System.currentTimeMillis()` at write dispatch time, not the detection time. |
| **Fix** | Pass raw detection millis (and server-corrected millis) through to `writeDetection()` and store as numeric Firestore fields. |
| **Complexity** | Low — change method signature, add fields to the Firestore document. |

---

### TIMING-005: Server offset calibration is single-sample

| | |
|---|---|
| **Severity** | MEDIUM |
| **Impact** | 10-50ms random error on server offset |
| **Files** | `SessionRepository.kt:113-124` |
| **Root cause** | One round-trip measurement. Network jitter on a single sample can be 10-50ms. Asymmetric latency (upload vs download) biases the midpoint formula. |
| **Fix** | Take 3-5 round-trip samples. Use the sample with the shortest round-trip time (`t2-t1`), since shorter RTT implies less asymmetry and more accurate midpoint. |
| **Complexity** | Low — loop with min-RTT selection. |

---

### TIMING-006: Frame step hardcoded to 33ms (assumes 30fps)

| | |
|---|---|
| **Severity** | MEDIUM |
| **Impact** | At 60fps, skip every other frame (16ms wasted precision). At 24fps, misaligned steps. |
| **Files** | `CaptureScreen.kt:328` |
| **Root cause** | `val frameStepMs = 33L` is a constant. `Quality.HIGHEST` commonly selects 60fps on modern phones. |
| **Fix** | Query the video's actual frame rate from `MediaMetadataRetriever` or ExoPlayer's `Format.frameRate` and compute step as `1000 / fps`. |
| **Complexity** | Low. |

---

### TIMING-007: Server offset can go stale

| | |
|---|---|
| **Severity** | MEDIUM |
| **Impact** | 5-30ms drift if significant time passes between calibration and recording |
| **Files** | `GunShotViewModel.kt:264-272`, `CaptureScreen.kt:119` |
| **Root cause** | Calibration runs at session join and after recording completes. If the user joins a session, waits 20+ minutes, then records, the offset may have drifted due to NTP adjustments on the phone. |
| **Fix** | Re-calibrate automatically when RECORD is pressed (immediately before recording starts). Keep the post-recording calibration as a refresh. |
| **Complexity** | Low. |

---

### TIMING-008: `TimestampFormatter` is not thread-safe

| | |
|---|---|
| **Severity** | LOW |
| **Impact** | Potential garbled timestamp strings under concurrent access |
| **Files** | `TimestampFormatter.kt:8-11` |
| **Root cause** | `SimpleDateFormat` is not thread-safe. Called from `Dispatchers.IO` (detection callback) and `Dispatchers.Main` (clock overlay) concurrently. |
| **Fix** | Replace with `java.time.format.DateTimeFormatter` (thread-safe, available on API 26+) or use a `ThreadLocal<SimpleDateFormat>`. |
| **Complexity** | Trivial. |

---

### TIMING-009: ExoPlayer seek position vs actual frame PTS

| | |
|---|---|
| **Severity** | LOW |
| **Impact** | Up to 1 frame duration (~16ms @ 60fps) discrepancy |
| **Files** | `CaptureScreen.kt:393-407` |
| **Root cause** | `CLOSEST_SYNC` during drag snaps to keyframes. `EXACT` on release is accurate but the displayed frame's actual PTS may still differ from `sliderPosition` by up to one frame. |
| **Fix** | After seeking, read back the player's actual position via `player.currentPosition` and use that for the timestamp calculation instead of `sliderPosition`. |
| **Complexity** | Low, but requires a short delay for the seek to settle before reading back the position. |

---

### TIMING-010: Speed of sound (operational, not code)

| | |
|---|---|
| **Severity** | INFORMATIONAL |
| **Impact** | ~3ms per metre of distance from gun to mic phone |
| **Root cause** | Sound at 343 m/s. Phone 100m away adds ~290ms. |
| **Mitigation** | Operational: detection phone must be placed at/near the starting gun, not at the finish line. Document this for users. |

---

## Error Budget — Current vs After Fixes

### Current (session mode)

| Source | Magnitude | Direction | Issue |
|--------|-----------|-----------|-------|
| Firestore write latency on T_gun | 50-200ms | Split too short | TIMING-001 |
| Audio input pipeline latency | 20-100ms | Split too short | TIMING-003 |
| recordingStartMillis jitter | 50-200ms | Split too long | TIMING-002 |
| Server offset calibration noise | 10-50ms | Either | TIMING-005 |
| Camera frame quantization (60fps) | ~16ms | Either | Hardware |
| Frame step misalignment | up to 16ms | Wrong frame selected | TIMING-006 |
| **Current worst case** | **~150-550ms** | **Unpredictable** | |

### After fixes

| Source | Magnitude | Direction | Status |
|--------|-----------|-----------|--------|
| Audio input pipeline latency | 20-100ms | Split too short | Partially fixable (manual offset works; auto-detect is future work) |
| Server offset calibration noise | 5-15ms | Either | Improved by multi-sample (TIMING-005) |
| Camera frame quantization (60fps) | ~16ms | Either | Hardware limit |
| ExoPlayer seek precision | ~16ms | Either | Improved by PTS readback (TIMING-009) |
| **Post-fix worst case** | **~40-130ms** | **Dominated by audio latency** | |

---

## Recommended Fix Order

Fixes are ordered by impact and dependency. TIMING-004 is a prerequisite for TIMING-001.

| Priority | Issue | Reason |
|----------|-------|--------|
| 1 | TIMING-004 | Prerequisite — pass raw millis through to Firestore write |
| 2 | TIMING-001 | Largest single error source — eliminates Firestore write latency from T_gun |
| 3 | TIMING-002 | Second largest — fixes recording start timestamp |
| 4 | TIMING-003 | Makes latency offset actually work for splits |
| 5 | TIMING-005 | Improves calibration accuracy |
| 6 | TIMING-006 | Correct frame stepping for actual fps |
| 7 | TIMING-008 | Thread safety fix (trivial) |
| 8 | TIMING-007 | Stale offset prevention |
| 9 | TIMING-009 | PTS readback for precision |

Items 1-4 are the high-impact fixes. Items 5-9 are incremental improvements.

---

## Solo Mode Notes

In solo mode, the gun and finish are on the **same device**. The split uses `serverTimestampMillis = adjusted` (the latency-offset-corrected detection time, in local clock). Since both sides use the same phone's clock, there is no cross-device sync issue. The remaining errors are:
- Audio input latency (correctable via `latencyOffsetMs`)
- `recordingStartMillis` jitter (TIMING-002 still applies)
- Frame quantization (hardware)

Solo mode is inherently more accurate than session mode.
