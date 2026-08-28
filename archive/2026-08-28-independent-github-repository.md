# 2026-08-28 Independent GitHub repository

## Goal

Publish AE2-lightoptimizer from its isolated workspace as a standalone GitHub repository, with no Git or source relationship to ImmortalStorage.

## Repository boundary

- Canonical repository: `https://github.com/positer/AE2-lightoptimizer`
- Default branch: `main`
- The local workspace is its own Git root and has no ImmortalStorage remote, subtree, submodule, or shared worktree.
- PCL instances, worlds, configs, logs, screenshots, build output, run state, Gradle caches, and IDE metadata remain local and ignored.

## Publication checks

- Scanned the complete candidate file set for GitHub tokens, private keys, API keys, passwords, and secrets; no match was found.
- Confirmed that no candidate source file exceeds 1 MiB and that the workspace contains no filesystem link.
- Added generated sources, compiled classes, logs, common IDE state, and operating-system metadata to `.gitignore` before the initial commit.
- Replaced machine-specific Java, Gradle-toolchain, PCL, and documentation paths with environment- or parameter-driven configuration before publication. Build launchers dynamically expose existing JDK 21/25 homes to Gradle and retain Foojay as a download fallback.
- Both maintained generation builds had already passed immediately before publication, with 54 tests passing per generation.
