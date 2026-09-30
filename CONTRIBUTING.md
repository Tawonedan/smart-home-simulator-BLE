# Contributing

Thanks for your interest in the Smart Home Simulator. Bug reports, ideas and pull requests
are welcome.

## Building

You only need a JDK 21 or newer; the Gradle Wrapper downloads everything else.

```bash
./gradlew run          # start the application
./gradlew test         # unit, regression, architecture and headless UI tests
./gradlew build        # compile, test and package build/distributions/*.zip
./gradlew javadoc      # API documentation in build/docs/javadoc
./gradlew jpackage     # native application image for the current OS in build/jpackage
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Code layout

The code is organised in layers that only depend downwards:
`ui` → `app` → `simulation` → `model` → `physics` (plus `util`, shared by all).
The rule, and the rule that only `ui` may use JavaFX, are checked by `ArchitectureTest`.
Put new logic in the lowest layer that can hold it, so it stays testable without a display.

## Pull requests

1. Create a branch from `main`.
2. Keep the change focused and add tests for new behaviour. Physics changes should be
   checked against a reference value, not just against the current output.
3. Run `./gradlew build javadoc` and make sure it passes with no warnings.
4. Write commit messages in the imperative mood, prefixed with the kind of change
   (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, `ci:`).
5. Open the pull request; the CI builds and tests it on Linux, Windows and macOS.

## Reporting a bug

Open an issue with the steps to reproduce it and, if possible, the project file
(*File → Save as*) that shows the problem.
