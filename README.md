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
- MVVM with `StateFlow`, Kotlin Coroutines/Flow, Navigation 3 (one ViewModel per screen entry)
- Hilt for dependency injection, Preferences DataStore for settings
- Room 3 with bundled SQLite for shifts and machines, exported schemas for safe migrations
- Android Gradle Plugin 9 with built-in Kotlin, KSP, Gradle version catalog
- `minSdk 29` (Android 10), `targetSdk 37`

## Architecture

```
:app      ui/    Compose screens + ViewModels (state in, events out)
          data/  Room and DataStore implementations of domain interfaces
          di/    Hilt modules: which implementation backs which interface
:domain          plain Kotlin: models, business rules, repository interfaces
```

- Dependencies point inward: `:app` depends on `:domain`, never the other way.
- `:domain` is a JVM module, so Android APIs cannot even be imported there; its
  rules are tested with plain JUnit, no emulator.
- Screens never talk to storage: they receive a UI state and send events to their
  ViewModel, which only knows the domain interfaces.

Run the unit tests with `./gradlew test`, and the database tests on a running
emulator or device with `./gradlew connectedAndroidTest`.

### Data model

- One shift per calendar day (the date is the key), with its type, optional
  4-hour overtime, and the machines driven in order (each marked regular or overtime).
- Machines are user-managed. One used by any shift is only ever soft-deleted (deactivated),
  so past shifts keep their history.
  Each machine has one location (e.g. a pier block), seeded from v1 and editable.
- Every database version's schema is committed under `app/schemas/`; schema changes
  ship with migrations, never with destructive resets.

### Screens

- **Today**: the current shift with its machines, locations and overtime, and the next
  7 days. A night shift stays "current" until 07:00 the next morning.
- **Shifts**: every stored shift, filtered by year (only years with data) and month.
- **Add / edit**: date, type, overtime, machines in order (each regular or overtime),
  comment. The editing rules live in `ShiftDraft` (`:domain`), so every combination the
  screen allows can be saved. Moving a shift onto a day that has one asks first.
- **Calendar**: a month per page, swiped sideways, Monday first. Every day in its shift
  color with a short name and "+4" for overtime; predicted days in gold with sparkles;
  public holidays with a red number. Next to the grid, the month summary or the selected
  day (one button switches, remembered separately upright and sideways).
- **Rotating schedule**: the 49-day cycle of the terminal (shift types stepping afternoon →
  morning → night, the whole pattern repeating every 147 days) in `RotationSchedule`
  (`:domain`). Two questions set your place (when your last four-day rest started, and the
  shift after it), or the app finds it from your recent shifts and asks once. From today
  on, empty days show the prediction; a recorded day always wins. A month's predictions
  can be recorded in one go. Other work schedules (mornings only, afternoons and nights,
  …) predict nothing and offer only their shifts.
- **New shift suggestion**: the prediction of the day, or the shift running now
  (07–15 morning, 15–23 afternoon, 23–07 night); outside your schedule, nothing is
  suggested and the screen says so.
- **Public holidays**: the 13 official holidays of Greece, computed for any year (Orthodox
  Easter with Meeus' algorithm), shown in the calendar, on Today and in the lists.
- **Menu**: the last tab of the bottom bar. The app's sections on top (Machines,
  Statistics; leave and vessels join as they are built), Settings below them.
- **Statistics**: per year or month: working shifts and hours, overtime, shifts on
  holidays, days per type, month-by-month bars, and the machines driven.
- **Machines**: per category, searchable by number or location, with how many shifts used
  each one. Add a machine, change its number or location (past shifts follow, since they
  refer to the machine, not its number), deactivate it, or delete it for good if no shift
  ever used it. The rules (unique numbers, reactivating a retired number) live in
  `MachineForm` (`:domain`). A number missing from the shift form's picker can be added there.
- **Settings**: appearance, work schedule and place in the rotation, public holidays,
  v1 import, delete all (two confirmations).

### Design system

- Material 3. App colors come from the wallpaper on Android 12+ (Dynamic Color), or
  from Dockshift's own harbour-blue schemes (seed `#1B6A80`) on Android 10–11 or when
  turned off in Settings. Light/dark follows the phone or a choice in Settings.
- Every shift type has a fixed color (`ShiftPalette`), the same in the calendar, lists,
  charts and widget, never taken from the wallpaper. The palette is designed with equal
  lightness per role, and a unit test checks the text contrast of every color (WCAG AA).
- The phone's system font, which covers Greek fully and follows the user's font size.
- Original vector launcher icon, with a monochrome layer for themed icons (Android 13+).

### Moving from the first version

Users of v1 bring their data over once, from its backup file (`DataShift_foxnks/dataShift_foxnks.csv`):
the app asks on first start, or later from the menu. The system file picker opens straight in
that folder, so no storage permission is needed. The file is fully checked first: any unreadable
value is reported with its line and nothing is saved; otherwise a report is shown and everything
is saved in one transaction, never overwriting days already in the app. All v1-specific code
lives in `domain/legacy`, apart from the rest of the app.

To check the importer against your own v1 file (it is never committed), run:

```
DOCKSHIFT_V1_CSV=/path/to/dataShift_foxnks.csv ./gradlew :domain:test --tests "*RealV1FileTest*"
```

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
