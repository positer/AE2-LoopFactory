# 2026-08-28 Non-destructive PCL deployment

## Incident

The former `-Recreate` path recursively deleted each complete addon instance before copying launch files. That operation also permanently removed `saves`, configs, screenshots, resource packs, logs, options, and other runtime state. It was the direct cause of the reported test-world loss.

At investigation time, the 1.21.1 addon instance had no `saves` directory. The 26.1.2 instance still contained `saves/Test`. No matching addon-world backup was found under the PCL tree; the only detected PCL world-backup directory belonged to the isolated ImmortalStorage instance and was not touched.

## Correction

- Removed recursive instance deletion from provisioning.
- Added `-RefreshManagedFiles` as the canonical operation.
- Retained `-Recreate` only as a warning-emitting compatibility alias with identical safe semantics.
- Limited updates to target version JSON/JAR, PCL launch metadata, and pinned AE2, GuideME, JEI, and addon JAR families.
- Made unknown user-added mod JARs a pre-mutation error instead of deleting them.
- Preserved `saves`, `config`, `defaultconfigs`, `screenshots`, `resourcepacks`, `logs`, `options.txt`, and all other unmanaged instance state.
- Added a save-integrity manifest containing relative path, byte length, UTC timestamp ticks, and SHA-256 before and after every deployment.
- Locked save files now stop deployment before any managed file changes, requiring Minecraft to exit cleanly first.

Only strictly validated `ae2lightoptimizer-pcl-stage-*` and `ae2lightoptimizer-pcl-preservation-test-*` directories under the system temporary directory may still be recursively removed.

## Regression evidence

`tools/test_pcl_provision_preserves_runtime_state.ps1` provisions both generations into disposable fixtures and confirms representative worlds, configs, default configs, screenshots, resource packs, logs, and options remain byte-for-byte and timestamp-identical. Both fixture generations passed.

A live refresh was intentionally blocked before mutation because the 26.1.2 `saves/Test/session.lock` file was held by the running Minecraft process. This demonstrates that deployment cannot race an open test world; the live before/after verification remains to be rerun after the game exits.
