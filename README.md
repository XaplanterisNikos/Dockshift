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
- Android Gradle Plugin 9 with built-in Kotlin, Gradle version catalog
- `minSdk 29` (Android 10), `targetSdk 37`

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
