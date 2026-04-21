# Architecture Decision Records

---

## ADR-001: Use UNPROCESSED Audio Source

**Date**: 2026-04-19 (revised 2026-04-19)
**Status**: Superseded — see revision note

**Context**:
Real-world outdoor testing revealed the app was non-functional. DAW analysis of recorded test audio showed the gunshot and wind gusts had near-identical amplitude envelopes. Root cause: AGC (Automatic Gain Control) on the phone normalises mic gain continuously, compressing the gunshot's true amplitude spike to be indistinguishable from wind.

`VOICE_RECOGNITION` was originally chosen believing it bypassed AGC — this was incorrect. It applies noise suppression and AGC.

**Decision**:
Switch to `MediaRecorder.AudioSource.UNPROCESSED` (API 24+).

**Rationale**:
- `UNPROCESSED` explicitly requests raw microphone data with no AGC, no noise suppression, and no post-processing
- Restores the true amplitude difference between a gunshot (impulsive, extremely loud in raw terms) and wind (loud but compressed by AGC)
- API 24 = minSdk 26, so device support is guaranteed for this project

**Consequences**:
- (+) Gunshot amplitude spike is preserved — RMS-based detection can distinguish it from wind again
- (-) Truly raw audio means more ambient noise reaches the algorithm — rolling baseline becomes more important
- (-) `UNPROCESSED` support quality varies by device/manufacturer even on API 24+; some devices may silently fall back to `MIC`

**Alternatives considered**:
- `VOICE_RECOGNITION`: Previously used; applies AGC — confirmed to fail outdoors
- `MIC`: Default, also applies AGC on most devices
- `CAMCORDER`: Optimised for video, not suitable

---

## ADR-005: Transient Duration Confirmation Window

**Date**: 2026-04-19
**Status**: Accepted — parameters may need field tuning

**Context**:
Even with `UNPROCESSED` audio, wind gusts can produce sharp amplitude spikes that look like gunshots in a single buffer (pop filter / windscreen effect at the mic). A gunshot transient is genuine; a wind gust peak sustains. The difference is duration: a gunshot RMS drops back toward baseline within ~5–10ms; a wind gust stays elevated for tens to hundreds of milliseconds.

**Decision**:
On a candidate detection, do not fire immediately. Instead:
1. Capture the timestamp at the spike (sub-buffer peak index, same as before)
2. Enter a confirmation window — read the next N buffers
3. If RMS falls back below `baseline × CONFIRMATION_DROP_MULTIPLIER` within the window → confirm and emit the pre-captured timestamp
4. If RMS stays elevated → reject as sustained noise (wind)

**Parameters (initial values — expect field tuning)**:
- `CONFIRMATION_BUFFERS = 3` (~5–10ms at 44100 Hz with current buffer size)
- `CONFIRMATION_DROP_MULTIPLIER = 3.0` (must drop to within 3× baseline to confirm)

**Rationale**:
- Gunshot: near-zero rise and fall time (single buffer spike) — will confirm easily
- Wind gust: even sharp peaks sustain for many buffers — will be rejected
- Timestamp is captured at the spike, not after the window, so confirmed latency is not added to the reported time

**Consequences**:
- (+) Rejects sustained-loud sounds (wind, crowd burst) that pass the amplitude check
- (-) Adds ~5–10ms of confirmation delay before the event is emitted (invisible to timing accuracy since timestamp is pre-captured)
- (-) Parameters (`CONFIRMATION_BUFFERS`, `CONFIRMATION_DROP_MULTIPLIER`) are empirical — may need adjustment per environment

**Alternatives considered**:
- Rise-time detection (dRMS/dt): Rejected — sharp wind gusts also have fast rise times, does not discriminate well enough
- FFT frequency analysis: Would work (wind is low-frequency, gunshots are broadband) but adds significant complexity; revisit if duration check proves insufficient

---

## ADR-002: Use AudioRecord (Not MediaRecorder)

**Date**: 2026-04-19
**Status**: Accepted

**Context**:
Android has two primary recording APIs: `AudioRecord` and `MediaRecorder`. We need access to raw PCM samples.

**Decision**:
Use `android.media.AudioRecord`.

**Rationale**:
- Provides raw PCM buffer access — required for RMS calculation and peak sample detection
- `MediaRecorder` only outputs to files; no way to inspect individual samples in real time

**Consequences**:
- (+) Full control over buffer reads, sample-level inspection
- (-) More boilerplate than `MediaRecorder`; must manage buffer size, threading, and teardown manually

**Alternatives considered**:
- `MediaRecorder`: Simpler API but outputs encoded audio to a file — cannot access raw samples
- Google Oboe (NDK): Sub-10ms latency path, but requires NDK + CMake, adds significant build complexity. Deferred to stretch goal.

---

## ADR-003: RMS + Rolling Baseline Detection (Not Raw Peak Amplitude)

**Date**: 2026-04-19
**Status**: Accepted

**Context**:
We need to distinguish a gunshot from ambient noise. A fixed amplitude threshold would fail in both quiet rooms (too sensitive) and loud environments (misses the shot).

**Decision**:
Compute RMS per buffer, maintain a rolling average baseline of recent RMS values, and trigger when current RMS exceeds `baseline × multiplier` AND exceeds an absolute minimum.

**Rationale**:
- Rolling baseline adapts to ambient noise level automatically
- Relative threshold (multiplier) catches the sharp transient even in noisy environments
- Absolute minimum prevents false triggers in near-silence where tiny sounds create high multipliers
- Cooldown (2–3 seconds) prevents double-triggers from reverb or echo

**Consequences**:
- (+) Robust across different environments without manual recalibration
- (-) Requires ~1 second of "warm-up" time for the baseline to settle after starting

**Alternatives considered**:
- Fixed amplitude threshold: Simple but breaks in any environment that doesn't match calibration
- FFT-based detection: More sophisticated but significant added complexity; gunshots have a broad frequency profile anyway

---

## ADR-004: Kotlin DSL for Gradle Build Files

**Date**: 2026-04-19
**Status**: Accepted

**Context**:
Gradle supports both Groovy DSL (`build.gradle`) and Kotlin DSL (`build.gradle.kts`).

**Decision**:
Use Kotlin DSL (`build.gradle.kts`) for all Gradle build files.

**Rationale**:
- Type-safe, IDE-autocomplete in Android Studio
- Consistent with the project language (Kotlin throughout)
- Modern Android standard — new Android Studio projects default to Kotlin DSL

**Consequences**:
- (+) Better IDE support, catches build config errors at edit time
- (-) Slightly different syntax from older Groovy examples found online

**Alternatives considered**:
- Groovy DSL: More online examples exist, but inferior IDE support and mixing Groovy into a Kotlin project feels inconsistent

---

## ADR-006: Time Synchronisation Strategy for Finish Line Capture

**Date**: 2026-04-20
**Status**: Superseded by ADR-007

**Context**:
The finish line capture feature (Phase 5) requires computing a split time as `T_finish − T_gun`. These two timestamps originate on different physical devices, so their clocks must be aligned. The question was whether NTP is sufficient, and whether the start gun hardware latency calibration applies to the finish side too.

**Key insight — start and finish latencies are different in kind**:
- **Start gun**: Audio input latency — time from sound hitting the mic to PCM samples reaching the app (`AudioRecord` pipeline). Typically 20–100ms. Systematic and device-specific → can be calibrated with a fixed offset.
- **Finish video**: Clock overlay is rendered by the CPU directly from `System.currentTimeMillis()`. No equivalent audio pipeline. Camera frame rate is the limiting factor (~16ms @ 60fps, 33ms @ 30fps), not a hardware input delay. The start gun calibration offset does NOT apply to the finish side.

**Decision**:
Rely on NTP (already provided automatically by Android) as the cross-device time reference. Add a Firestore round-trip sync check at session start to detect stale or drifted clocks.

**Error budget (finish side)**:
| Source | Magnitude |
|--------|-----------|
| NTP difference between two devices | ~20–50ms |
| Camera frame rate quantisation | 16ms @ 60fps |
| `recordingStartMillis` capture jitter | ~5–20ms |
| **Total** | ~40–100ms |

This is acceptable for amateur timing where margins are typically >100ms.

**Sync check**:
Before the race, each device writes a doc with `clientTimestamp` to Firestore and reads back `serverTimestamp`. The difference gives a rough "clock offset from server" estimate. Warn the user if any device is >100ms off.

**Consequences**:
- (+) Zero additional infrastructure — NTP is free and already active
- (+) Sync check is simple to implement using existing Firestore connection
- (-) Cannot fully correct for NTP drift — can only warn, not fix
- (-) If a device is offline or recently restored from airplane mode, its clock may be stale

**Alternatives considered**:
- GPS time: Extremely accurate (~100ns) but requires GPS lock — not reliable indoors or near finish chutes
- PTP (Precision Time Protocol): Microsecond accuracy but requires network infrastructure; impractical for a consumer app
- Audio sync pulse: Play a tone on one phone, record on the other to compute offset — complex, rejected as over-engineering for this use case

---

## ADR-007: Split Time Calculation — Firestore Server Clock as Common Reference

**Date**: 2026-04-20
**Status**: Accepted

**Context**:
Computing a split time requires `T_finish − T_gun` where those timestamps originate on two different physical devices. Two problems needed solving independently:

1. **False positive starts**: the system may detect a sound before the real gun fires. Committing to T_gun live (e.g. to drive a live elapsed timer on the finish phone) means a false positive corrupts the calculation mid-race with no clean recovery. Using the star/favourite feature to mark the official detection requires a human to act before the race ends — reintroducing human error.

2. **Cross-device clock sync (NTP problem)**: if T_gun uses the start phone's `System.currentTimeMillis()` and T_finish uses the finish phone's `System.currentTimeMillis()`, any NTP drift between the two devices (typically 20–50ms) directly corrupts the split. Post-race calculation doesn't avoid this — two independent client timestamps have the same problem.

These are independent problems and have independent solutions.

**Decision**:

**False positives → post-race selection**
Do not commit to T_gun during the race. All gun detections are written to Firestore as normal. After the race, the user selects the official detection. Its Firestore `serverTimestamp` becomes the authoritative T_gun. No live human acknowledgement required during the race.

**Clock sync → Firestore server timestamp as common reference**
Both sides reference the same clock — the Firestore server — rather than their own independent NTP-synced clocks.

- Every gun detection is stored with a Firestore `serverTimestamp` (already the case)
- At session start, the finish phone performs a round-trip calibration: `serverOffset = serverTime − clientTime`
- The finish video overlay displays `System.currentTimeMillis() + serverOffset` (server-relative time)
- Post-race split = `server_relative_frame_time − T_gun_serverTimestamp`

The NTP difference between the two phones becomes irrelevant — both sides are on the Firestore server clock. The remaining error is the quality of each device's server offset calibration.

**Error budget**:
| Source | Magnitude |
|--------|-----------|
| Server offset calibration per device | ~10–20ms |
| Camera frame rate quantisation (60fps) | ~16ms |
| **Total** | ~25–35ms |

**Consequences**:
- (+) False positive problem fully resolved — post-race selection, no time pressure
- (+) Cross-device NTP problem eliminated — single shared reference clock
- (+) Star/favourite feature remains useful for marking the official detection post-race
- (+) Live elapsed display on the finish phone is still possible (and still useful as an approximate display for coaches/spectators), but is not the authoritative calculation
- (-) Finish phone must perform a server offset calibration — add a warning if it hasn't done so
- (-) If network is lost during recording, the server offset cannot be refreshed

**Calibration timing (revised 2026-04-20)**:
Calibration runs automatically after recording completes, not at session start. Rationale: a post-recording calibration gives a fresher `serverOffset` than one taken at session join (which could be many minutes stale by race time). The scrubber always uses the most recent calibration result. A warning is shown on the camera preview if calibration has never run; the scrubber currently silently falls back to 0ms offset if `serverOffsetMs` is null — this is a known gap to be fixed.

**Alternatives considered and rejected**:
- Live T_gun commitment with false-positive handling: requires human acknowledgement mid-race — reintroduces human error
- Two independent NTP-synced client timestamps: ~20–50ms cross-device error, uncontrolled
- GPS time: accurate but requires GPS lock, not reliable in all venues
- FAT-style single hardware clock: requires dedicated hardware; out of scope for a phone-based system

---

<!-- Copy the template above for each new decision.
     Number sequentially: ADR-005, ADR-006, etc.
     When a decision is reversed, set Status to "Superseded by ADR-XXX" -->
