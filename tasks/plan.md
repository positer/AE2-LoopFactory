# Implementation Plan: AE2LO 0.0.3 Loop Storage Cells

## Overview

Add the complete Loop Storage Cell family to both supported NeoForge generations. The feature includes ten provided, byte-identical core textures; one recolored cell housing; ten finite universal cells; one infinite universal cell; all requested shaped/shapeless/explosion recipes; dynamic compatibility with every AE2 key type registered by addons; bilingual resources; and release documentation for version 0.0.3.

## Architecture Decisions

- Treat `Minecraft Forge Workspace/design/process_core/{1k,4k,16k,64k,256k,1M,4M,16M,64M,256M}.png` as immutable source assets. Copy bytes unchanged and lock SHA-256 hashes in tests.
- Implement finite capacity through AE2's storage-cell interfaces in each version adapter, while keeping capacity arithmetic and per-key-type unit conversion in API-free shared code.
- Discover supported resources from AE2's registered key-type registry at runtime. Do not hard-code addon mod IDs or Java classes.
- Normalize capacity to item-equivalent units: one item unit equals 1,000 units for fluid, FE, mana, source, soul, and any addon key type whose AE2 key-space unit is 1,000 per item-equivalent. Delegate native key-type unit semantics to AE2 whenever its registry exposes them.
- Preserve the 1k–256k type limits of their corresponding AE2 item/fluid cells. M-tier and infinite cells have no type-count limit.
- Implement the infinite-cell TNT transformation as a data-driven `ae2:transform` recipe with the `explosion` circumstance and an exact input requirement of 64 `256m_loop_storage_core` plus one housing.
- Generate only the explicitly requested recolors of AE2 cell textures. Preserve source dimensions, alpha, silhouette, pixel positions, and non-color detail.

## Task List

### Phase 1: Contracts and API probes

- [x] Record exact AE2 versions and inspect storage-cell/key-type registration APIs in both adapters.
- [x] Add shared tests for tier capacities, type limits, conversion units, overflow safety, and infinite semantics.
- [x] Add version contract tests for registrations, recipes, immutable core hashes, models, languages, and dynamic key-type support.

### Checkpoint: Contracts

- [x] Storage behavior and resource contracts cover the complete 0.0.3 surface.
- [x] Existing 0.0.2 behavior remains covered and runnable alongside the new contracts.

### Phase 2: Shared domain model and assets

- [x] Implement shared tier metadata and checked capacity accounting.
- [x] Copy the ten provided core PNGs byte-for-byte into both version resource trees.
- [x] Add deterministic recolor tooling and generate the housing, ten disk textures, and infinite disk texture only.

### Checkpoint: Foundation

- [x] Shared tests pass.
- [x] Texture dimensions/alpha/geometry and immutable core hashes pass.

### Phase 3: Version adapters

- [x] Register housing, ten cores, ten finite cells, and the infinite cell in NeoForge 1.21.1.
- [x] Register the same surface in NeoForge 26.1.2 using its native registration/item descriptor form.
- [x] Register universal cell handlers/models and dynamic AE2 key-type support in both generations.
- [x] Add the data-driven AE2 explosion transform in both generations.

### Checkpoint: Runtime surface

- [x] Each version's directed contract tests pass.
- [x] Each version compiles against its pinned AE2 API.

### Phase 4: Data, docs, and release gate

- [x] Add all shaped and shapeless recipes, including the corrected 16k recipe using the 16k core.
- [x] Add bilingual names/tooltips and AE2 GuideME documentation.
- [x] Bump both adapters to semantic version 0.0.3 and update README, OVERVIEW, taste, and CHANGELOG.
- [x] Run both full builds and global takeover verification; inspect packaged JAR contents and hashes.
- [x] Add a dated archive record with implementation and verification evidence.

### Checkpoint: Complete

- [x] Both generation builds pass with no skipped tests.
- [x] Handler registration, native drive models, and dynamic key-type exposure pass automated contracts; persistent in-game drive interaction remains a release acceptance check.
- [x] Core textures are byte-identical to the user-provided files.
- [x] No unrequested visual detail was created.

## Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| AE2 basic cells bind one key type per item | High | Probe exact APIs first; implement a custom handler/inventory if required rather than subclassing a single-type cell. |
| Addon key types use different native units | High | Read AE2 key-type unit metadata dynamically and keep checked conversion arithmetic; decline unsupported/invalid units safely. |
| Infinite capacities overflow `long` | High | Represent infinity as an explicit tier flag and saturate only at the AE2 boundary; never encode infinity as arithmetic multiplication. |
| Explosion recipes differ by generation | Medium | Use each generation's native ingredient form in data-driven `ae2:transform` JSON and test exact input consumption/output count. |
| Texture recolor changes geometry | High | Compare alpha masks and per-pixel source-to-output coordinate occupancy, and hash-lock immutable core copies. |

## Open Questions Resolved by Specification

- The `13k` text in the 16k shaped disk recipe is treated as an input typo because the same paragraph's shapeless recipe and the defined core tier both say `16k`.
- Tier names use lowercase registry IDs (`1k_loop_storage_core`, `1k_loop_storage_cell`, etc.) and bilingual display names preserve the requested capitalization.
