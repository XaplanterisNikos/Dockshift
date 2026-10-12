# Dockshift

Shift tracker for container terminal crane operators: log your shifts, see your
rotating schedule, plan leave and check your stats. Available in Greek and English.

> **Status:** v2.0 is being rewritten from scratch in Kotlin and Jetpack Compose.
> This repository currently holds the project skeleton; features land issue by issue.

## Background

Dockshift started in 2022 as a personal Java app, shared as an APK with colleagues
at the port. Version 2.0 is a full rewrite with a modern Android stack. Users move
their data from the old app with a CSV export/import that keeps the same format.

## Tech stack

- Kotlin, Jetpack Compose, Material 3 (Dynamic Color on Android 12+)
- MVVM with `StateFlow`, Kotlin Coroutines/Flow
- Hilt for dependency injection, Preferences DataStore for settings
- Android Gradle Plugin 9 with built-in Kotlin, KSP, Gradle version catalog
- `minSdk 29` (Android 10), `targetSdk 37`

## Architecture

```
:app      ui/    Compose screens + ViewModels (state in, events out)
          data/  DataStore (and Room) implementations of domain interfaces
          di/    Hilt modules: which implementation backs which interface
:domain          plain Kotlin: models, business rules, repository interfaces
```

- Dependencies point inward: `:app` depends on `:domain`, never the other way.
- `:domain` is a JVM module, so Android APIs cannot even be imported there; its
  rules are tested with plain JUnit, no emulator.
- Screens never talk to storage: they receive a UI state and send events to their
  ViewModel, which only knows the domain interfaces.

Run all unit tests with `./gradlew test`.

## Building

1. Open the project in Android Studio and set **Gradle JDK** to JDK 17 or newer.
2. Let Gradle sync, then run the `app` configuration.

Debug builds install as a separate app (`.debug` suffix), so they never touch the
data of an installed release.

### Release signing

Copy `keystore.properties.example` to `keystore.properties` and fill in your own
keystore path and passwords. The file is git-ignored. Without it, release builds
are produced unsigned.

## License

[MIT](LICENSE)
