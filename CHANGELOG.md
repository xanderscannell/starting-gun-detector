# Changelog

## [Unreleased]

### Fixed
- Buffer offset calculation was inverted — timestamp was computed relative to the start of the buffer rather than the end, causing timestamps to be reported up to one full buffer duration too early (~40ms systematic error)

### Added
- Manual latency offset control (±500ms, 10ms steps) to compensate for device hardware input latency
- Persistent username setting — shown as the session badge label instead of the short device ID
- Latency offset and username are both persisted across app restarts via SharedPreferences

### Changed
- Session detection badges now show the sender's username instead of their short device ID; falls back to short ID if no name is set (also backwards-compatible with old Firestore documents that lack a display name)

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
