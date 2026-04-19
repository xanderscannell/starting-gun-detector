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

<!-- Copy the template above for each new decision.
     Number sequentially: ADR-005, ADR-006, etc.
     When a decision is reversed, set Status to "Superseded by ADR-XXX" -->
