# Project Status

**Last updated**: 2026-04-19

## Current Position

**Phase**: Phase 1 — Project Scaffold
**Subphase**: Not started
**Progress**: 0% complete

## Recently Completed

- Wrote design plan (`gunshot_timer_design_plan.txt`)
- Initialized context framework

## In Progress

- Nothing yet — project is pre-code

## Next Up

1. Create Android project in Android Studio (Kotlin DSL, min SDK 26, target SDK 34)
2. Configure `build.gradle.kts` with required dependencies
3. Add `RECORD_AUDIO` permission to `AndroidManifest.xml`
4. Create basic Compose skeleton (single screen, empty state)

## Active Files and Modules

```
app/src/main/java/com/example/startinggundetector/
├── MainActivity.kt              [not started]
├── ui/
│   ├── StartingGunScreen.kt     [not started]
│   └── theme/Theme.kt           [not started]
├── viewmodel/
│   └── GunShotViewModel.kt      [not started]
├── audio/
│   └── AudioDetector.kt         [not started]
└── utils/
    └── TimestampFormatter.kt    [not started]
```

## Open Questions

- **Q**: Should the sensitivity slider range map linearly or inversely to the detection multiplier?
  - Leaning toward: inverse (slider=10 → multiplier=5x, slider=1 → multiplier=20x), since "high sensitivity" means triggers easier (lower threshold)
  - Blocked by: nothing, decide when implementing `AudioDetector`

- **Q**: What rolling window length for the noise baseline?
  - Leaning toward: ~1 second (~44100 samples at 44100 Hz)

## Notes for Claude

- This is a greenfield project — no existing code yet
- Design doc lives at `gunshot_timer_design_plan.txt` in the project root
- The key timing insight: timestamp when the buffer is *received*, then back-calculate using peak sample index within the buffer for sub-buffer precision
- Cooldown after detection: ~2-3 seconds to prevent duplicate triggers
