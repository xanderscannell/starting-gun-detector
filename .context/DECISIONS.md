# Architecture Decision Records

---

## ADR-001: Use VOICE_RECOGNITION Audio Source

**Date**: 2026-04-19
**Status**: Accepted

**Context**:
Android exposes several `MediaRecorder.AudioSource` options. We need the lowest possible latency path from microphone to app.

**Decision**:
Use `MediaRecorder.AudioSource.VOICE_RECOGNITION`.

**Rationale**:
- Bypasses AGC (Automatic Gain Control) and noise suppression processing that `MIC` applies
- Lower processing pipeline = lower latency
- Preserves raw transient amplitude, which is critical for detecting a sharp gunshot spike

**Consequences**:
- (+) Lower latency, more accurate amplitude data
- (-) No noise suppression — detection algorithm must handle ambient noise itself (rolling baseline approach)

**Alternatives considered**:
- `MIC`: Default source, but AGC can compress the gunshot spike and add latency
- `UNPROCESSED`: Even more raw, but inconsistent device support on API 26

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
