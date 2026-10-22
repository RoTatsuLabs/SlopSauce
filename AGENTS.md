# AGENTS.md

Persistent guidance for coding agents working on SlopSauce. Keep it short and current. Update it only when a lasting rule or practice changes, not to log tasks or releases.

## Project

SlopSauce is a fork of [DropSauce](https://github.com/HuzaifaKhalid1311/DropSauce), a comic and novel reader for Android that descends from Kotatsu. It reads manga, webtoons and novels from external sources and extensions (Mihon APK extensions and LNReader JS plugins). The app ships no content.

- Single module `:app`. The app name is DropSauce and the application ID is `org.haziffe.dropsauce`. Code lives in the package `org.koitharu.kotatsu`. Do not rename either.
- Kotlin with a mix of View/XML screens (view binding) and Jetpack Compose with Material 3 Expressive. Hilt for injection, Room for the database, KSP for code generation, core library desugaring on.
- minSdk 26, targetSdk 37, compileSdk 37. GPL-3.0.
- Gradle uses Groovy files (`build.gradle`, `settings.gradle`) and the version catalog `gradle/libs.versions.toml`.

## Build and test

The repository has a Gradle wrapper (Gradle 9.8.1). The daemon needs JDK 25, which `gradle/gradle-daemon-jvm.properties` sets. Run builds with the wrapper.

```sh
./gradlew :app:assembleDebug      # debug APK, application ID suffix .debug
./gradlew :app:testDebugUnitTest  # unit tests
./gradlew :app:lintDebug          # lint
./gradlew :app:assemblePreview    # unminified preview build, suffix .preview
```

- Build types: `debug`, `preview` (unminified and debuggable) and `release` (R8 and resource shrinking).
- Lint runs with `abortOnError` and `warningsAsErrors`, so new lint warnings fail the build. A fixed list of checks is disabled in `app/build.gradle`. Fix a warning instead of extending that list.
- Release signing comes from Gradle properties (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Without them the release build is unsigned. Never commit keystores or passwords.
- Release automation edits `versionName` and `versionCode` in `app/build.gradle` with `sed`. Keep both as plain literals in the form `versionName = '1.0.1'` and `versionCode = 77`.
- Release and nightly APKs are also built by workflows in the separate `RoTatsuLabs/Forge` repository.

## Layout

All source is under `app/src/main/kotlin/org/koitharu/kotatsu/`:

```
core/          shared database, network, parsers, preferences, UI and utilities
main/          entry points and app-level screens
reader/        manga and novel reader
details/       details, chapters and metadata
explore/       browse and discovery
search/        search flows
favourites/    favourites and library
history/       reading history and progress
download/      offline downloads
extensions/    extension management
lnreader/      LNReader JS plugin integration
mihon/         Mihon and Tsundoku APK extension integration
backup/        local backup and restore
sync/          Google Drive sync
tracker/       tracking and reverse tracking
widget/        home screen widgets
settings/      settings screens
```

Other source sets are `app/src/test`, `app/src/androidTest`, `app/src/debug`, `app/src/preview` and `app/src/release`. Room schemas are exported to `app/schemas`.

## Rules

- Match the style of the file you edit. The code uses tabs for indentation, and Kotlin follows `kotlin.code.style=official`.
- The Compose Material 3 Expressive APIs are opted in for the whole module in `app/build.gradle`. Do not add per-file opt-ins for them.
- When you change a Room entity or DAO, update the database version and add a migration. Commit the exported schema.
- Strings go in resources, not in code. Keep the default `values` file complete, and do not remove or rename keys that translations use.
- Keep the app free of bundled content and of hardcoded content sources. Sources come from extensions and repositories the user adds.
- Do not add a dependency unless the change needs it. Declare versions only in `gradle/libs.versions.toml`.
- Keep `app/proguard-rules.pro` in step with reflection, serialization and extension-loading code. Release builds are minified.
- Do not edit vendored files under `app/lnreader-libs` or the bundled JavaScript assets except to update them.
- SlopSauce is GPL-3.0. Do not add a dependency whose licence is incompatible.

## Working style

- Avoid unrelated refactors, new dependencies and speculative changes.
- Verify with the commands above and report anything you could not run.
- Commit messages follow Conventional Commits, are written in English and stay short and to the point.
- Do not add `Co-Authored-By` trailers, session URLs or other AI attribution to commits or pull request descriptions.
