# 2026-08-28 Isolated PCL instances

## Requirement

Create separate PCL instances for both maintained AE2-lightoptimizer generations and isolate them from the existing ImmortalStorage instances.

## Provisioned instances

- `<PCL_ROOT>\.minecraft\versions\AE2-lightoptimizer-1.21.1`
  - Minecraft 1.21.1 / NeoForge 21.1.235
  - AE2 19.2.17
  - GuideME 21.1.17
  - `ae2lightoptimizer-neoforge-mc1.21.1-0.1.0-SNAPSHOT.jar`
- `<PCL_ROOT>\.minecraft\versions\AE2-lightoptimizer-26.1.2`
  - Minecraft 26.1.2 / NeoForge 26.1.2.94
  - AE2 26.1.10-beta
  - GuideME 26.1.12-beta
  - `ae2lightoptimizer-neoforge-mc26.1.2-0.1.0-SNAPSHOT.jar`

## Isolation design

- Reused only the matching local NeoForge launch JSON/JAR and PCL version metadata.
- Did not copy the source instances' `mods`, `config`, `defaultconfigs`, `saves`, `logs`, `options.txt`, screenshots, resource packs, or any world state.
- Deployed dependencies through an exact three-JAR whitelist per generation.
- Both instances set `VersionArgumentIndieV2:True`, keeping all future game state within their own version directory.
- Neither instance contains a filesystem link or any file named for ImmortalStorage/仙藏.
- Existing ImmortalStorage PCL instances and their mod JARs were not changed.

## Reproducibility

Added `tools/provision_pcl_instances.ps1`. It refuses to merge into an existing directory by default. `-Recreate` resolves and checks the parent and exact target name before replacing only the two managed addon instance directories.

## Verification

- Each new instance contains exactly three mod JARs.
- 1.21.1 deployed addon SHA-256: `2AF1F43F3F04ACDC9C8954E0E65954C6ECF136BA1DF81C4B52DE28A1013D6545`.
- 26.1.2 deployed addon SHA-256: `388D02D953616A618F7586294FF542B9785F160271B51F15323CE9EA74D4D272`.
- Both hashes exactly match the corresponding workspace build artifact.
- Forbidden artifact/link count: zero in both instances.
- Inherited mutable-directory count: zero in both instances.

The clients were not launched during provisioning; this task establishes clean launcher configuration and deployment state.
