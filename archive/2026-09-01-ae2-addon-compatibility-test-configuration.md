# AE addon compatibility test configuration

Date: 2026-09-01

## Scope

Configured both isolated PCL test instances with a pinned AE storage addon that registers a third-party FE storage key type. This is test-runtime configuration only and does not change AE2LO's dependency boundary.

## Managed additions

- Minecraft 1.21.1: `AppliedFlux-1.21-2.1.5-neoforge.jar` and required `Glodium-1.21-2.2-neoforge.jar`.
- Minecraft 26.1.2: `AppliedFlux-26.1-1.0.1-neoforge.jar` and required `Glodium-26.1-1.2-neoforge.jar`.
- Existing pinned AE2 and GuideME satisfy Applied Flux's other declared requirements.

`tools/provision_pcl_instances.ps1` now downloads, refreshes, and validates these exact files as compatibility-test dependencies. Unknown mod files remain a hard stop. The preservation regression fixtures were expanded to the same six-JAR whitelist.

## Verification

- The provisioning preservation regression passed twice per disposable generation fixture, including the legacy `-Recreate` alias.
- Both real PCL instances refreshed successfully with unchanged save manifests.
- Each real instance contains exactly AE2LO, AE2, GuideME, JEI, Applied Flux, and Glodium.
- Both AE2LO `neoforge.mods.toml` sources continue to declare only NeoForge, AE2, and Minecraft as required dependencies. Applied Flux and Glodium are not packaged or declared.

Interactive insertion of Applied Flux FE into a Loop Storage Cell remains an in-game acceptance check.

## Infinite transform JEI presentation

Minecraft 26.1.2 uses AE2's JEI transform category, which expands every ingredient entry into its own slot. AE2LO now registers an optional JEI-only category for the infinite cell, hides only the original infinite transform display at JEI runtime, and shows `256M Loop Storage Core x64` plus one housing as two input slots. The underlying `ae2:transform` JSON still contains 64 core ingredients and one housing, so explosion matching and consumption are unchanged. JEI remains compile-only and is not a metadata dependency. AE2 19.2.17 does not ship the corresponding JEI transform category.

The 26.1.2 suite passes 94 tests with no failures, and the rebuilt deployed JAR has SHA-256 `58159F67C9DB211A07AB128CC27BAFD3135CC2BA55DD3D8530BC459412DC5E52`.
