# AGENTS.md

## Session protocol

1. Read `README.md`, `OVERVIEW.md`, `taste.md`, and the latest `archive/` entry before work.
2. Keep both target generations isolated and update each explicitly when behavior is shared.
3. After work, add a dated archive entry and update project documentation when structure or status changes.

## Hard boundaries

- Do not reference or copy ImmortalStorage source code, generated code, run state, or build output.
- Do not share Minecraft or NeoForge API classes between version projects.
- Build with the root PowerShell launchers to retain separate Gradle user homes.
- Gradle runs on Java 21. Minecraft 1.21.1 compiles with Java 21 and Minecraft 26.1.2 compiles with the Java 25 toolchain.
