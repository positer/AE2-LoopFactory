# 2026-08-28 Project Name and UI-Free Block Policy

- Set the public mod name for both maintained generations to `AE2-Better circulation and acceleration`.
- Retained the internal mod ID `ae2lightoptimizer` and existing block registry ID to preserve stable namespaces and world compatibility.
- Reframed every mod block as an AE2 network algorithm service: connecting the block activates its optimization role; blocks do not own menus, screens, or local configuration UI.
- Updated both NeoForge descriptions to state the UI-free network-block architecture.
- Renamed the shared default budget factory from `terminalDefault` to `networkBlockDefault`.
- Extended both version contract tests to reject `MenuProvider`, `openMenu`, `createMenu`, and `AbstractContainerScreen` dependencies in the recipe-ring block and block entity.
- Completed both full builds after the policy change: Minecraft 1.21.1 succeeded in 24 seconds and Minecraft 26.1.2 succeeded in 25 seconds.
- Verified the processed metadata for both generations contains `displayName="AE2-Better circulation and acceleration"`; each generation reports 11 tests, zero failures, and zero errors.
