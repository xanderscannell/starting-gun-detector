# Project Conventions

## Language and Runtime

- **Language**: Kotlin
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 34
- **Build system**: Gradle with Kotlin DSL (`build.gradle.kts`)

## Tooling

- **Formatter**: Android Studio built-in (Ctrl+Alt+L / Cmd+Option+L) — no external formatter configured
- **Linter**: Android Studio inspections + Kotlin compiler warnings — no ktlint
- **Type checker**: Kotlin compiler (strict by default)

## Testing

- **Framework**: JUnit 4 (Android default) + Compose UI testing library
- **Run tests**: `./gradlew test` (unit) / `./gradlew connectedAndroidTest` (instrumented)

## Build and Run

- **Debug build**: `./gradlew assembleDebug`
- **Release build**: `./gradlew assembleRelease`
- **Install on device**: `./gradlew installDebug`

## Coroutine Dispatcher Conventions

- Audio read loop: `Dispatchers.IO` — blocking I/O, must never run on Main
- ViewModel state updates: `Dispatchers.Main` — StateFlow emissions to UI
- Pure computation (RMS, baseline): runs inline on whichever dispatcher calls it (IO is fine)

## Audio Configuration Constants

Keep audio config values as named constants in `AudioDetector.kt`, not magic numbers:

```kotlin
private const val SAMPLE_RATE = 44100
private const val COOLDOWN_MS = 2500L
private const val BASELINE_WINDOW_SAMPLES = 44100  // ~1 second
```

## Project Guidelines Reference

`PROJECT_GUIDELINES.md` does not yet exist. Run `/guidelines-init` to create code quality
rules (naming conventions, architecture patterns, error handling, testing standards,
security, performance, git workflow).
