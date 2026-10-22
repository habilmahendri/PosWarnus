# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

POS WarNus is a fresh Android Studio project (default "Empty Activity" Compose template) — the app module contains only `MainActivity.kt` and a generated `ui/theme` package. No domain logic, architecture, or dependencies beyond the template defaults exist yet. There is no git repository initialized in this directory.

- Package / namespace / applicationId: `com.example.poswarnus`
- Language: Kotlin, UI: Jetpack Compose (Material 3)
- minSdk 24, targetSdk/compileSdk 36
- Single Gradle module: `:app`

## Build and run

Standard Gradle wrapper commands from the project root:

```bash
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # install on connected device/emulator
./gradlew test                 # run JVM unit tests (app/src/test)
./gradlew connectedAndroidTest # run instrumented tests (app/src/androidTest), needs a device/emulator
./gradlew clean
```

Run a single test class/method with Gradle's `--tests` filter, e.g.:

```bash
./gradlew test --tests "com.example.poswarnus.ExampleUnitTest"
```

There is no linter configured beyond Android Lint (`./gradlew lint`).

## Dependency management

Dependencies and plugin versions are centralized in [gradle/libs.versions.toml](gradle/libs.versions.toml) (the Gradle version catalog) and referenced from [app/build.gradle.kts](app/build.gradle.kts) via `libs.*` aliases. Add new dependencies there rather than hardcoding coordinates in the module build file.

## Architecture notes

Everything currently lives under `app/src/main/java/com/example/poswarnus/`:
- `MainActivity.kt` — single activity, hosts Compose content via `setContent`.
- `ui/theme/` — generated Compose theme (`Color.kt`, `Theme.kt`, `Type.kt`).

As POS (point-of-sale) features are added, expect this structure to grow significantly — there is no established package-by-feature/layer convention yet, so early architectural choices here (e.g. where to put data models, repositories, ViewModels, navigation) will set the pattern for the rest of the app.
