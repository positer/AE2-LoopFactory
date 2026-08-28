# 2026-08-27 Workspace Bootstrap

- Created a sibling repository isolated from ImmortalStorage.
- Final target matrix is Minecraft 1.21.1 / NeoForge 21.1.235 and Minecraft 26.1.2 / NeoForge 26.1.2.94.
- The briefly downloaded Forge 1.20.1 MDK was removed from this workspace after the target was cancelled and retained as recoverable cold data in the ImmortalStorage archive.
- Reused the local Gradle wrapper and locally available JDK 21/JDK 25 installations.
- Added independent build launchers and per-version Gradle user homes.
- No ImmortalStorage source, artifacts, or runtime data were copied.
- Defined the new project as `ae2lightoptimizer` / `AE2 Light Optimizer`.
- Added required AE2 dependencies: 19.2.17 for Minecraft 1.21.1 and 26.1.10-beta for Minecraft 26.1.2.
