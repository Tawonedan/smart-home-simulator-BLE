# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-09-30

First stable release.

### Added
- Zoom (toolbar, Ctrl + mouse wheel around the pointer, Ctrl+= / Ctrl+- / Ctrl+0) and
  middle-button panning of the floor plan.
- Headless UI tests that drive the real application on the Monocle platform.
- Native packages built by GitHub Actions for Windows, macOS and Linux, with an
  application icon; no Java installation needed to run them.
- Javadoc for the public API, package overviews, `CONTRIBUTING.md` and Dependabot.

### Changed
- The simulation summary refreshes itself while it is open.
- Sensors added by the user transmit at 0 dBm, like battery-powered smart-home devices.

## [0.4.0] - 2026-09-30

### Added
- Redesigned interface: AtlantaFX dark and light themes, Material icons, menu bar with
  keyboard shortcuts, tool palette and tabbed side panel.
- Drag sensors and the hub on the plan with live recomputation of the heatmap and links.
- Automatic hub placement that maximises the weakest sensor link.
- Coverage statistics, Turbo colour map and a floating legend.
- Sensor-hub link overlay and link table.
- JSON project files, three example projects and a self-contained HTML coverage report.

### Changed
- The default heatmap shows average (incoherent) power without fading.

## [0.3.0] - 2026-09-30

### Changed
- Rewritten in English and split into layered packages (`physics`, `model`, `simulation`,
  `app`, `ui`), enforced with ArchUnit. The engine output is bit-identical to the original.
- Heatmaps are computed in the background with progress and cancellation.
- Ray tracing is a pure, iterative function animated on a single canvas.

### Fixed
- Undo did not restore deleted walls.
- Every wall was drawn grey because materials were looked up by the wrong name.
- The directional antenna gain dropped to -108 dB at the beam edge, below the side lobes.

### Added
- 144 unit, regression and architecture tests and a CI pipeline on three operating systems.

## [0.2.0] - 2026-09-30

### Changed
- Gradle build with the wrapper and JavaFX from Maven Central, replacing the Eclipse
  project that pointed to a local JavaFX installation.
- About 1,100 lines of unreachable code removed.

### Fixed
- The window opened taller than the screen.
- Broken accented characters in the interface.

### Security
- Removed a committed access token.

## [0.1.0] - 2025-09-10

Original learning project: JavaFX grid with sensors, a hub, walls, ray animation and a
first propagation heatmap.

[1.0.0]: https://github.com/Phlekies/smart-home-simulator/releases/tag/v1.0.0
[0.4.0]: https://github.com/Phlekies/smart-home-simulator/compare/8266d5e...fb67fbc
[0.3.0]: https://github.com/Phlekies/smart-home-simulator/compare/8266d5e...fe1f333
[0.2.0]: https://github.com/Phlekies/smart-home-simulator/compare/ed1f739...8266d5e
[0.1.0]: https://github.com/Phlekies/smart-home-simulator/tree/ffa7c5c
