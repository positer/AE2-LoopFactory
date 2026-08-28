# Changelog

All notable user-facing changes to AE2-lightoptimizer are recorded here.

## 0.0.1 - 2026-08-28

Initial public release for NeoForge on Minecraft 1.21.1 and 26.1.2.

### Added

- Recipe Ring Solver Terminal for global cyclic crafting-tree takeover, compressed single-ring and irreducible multi-node SCC solving, strict cyclic dispatch, seed locking, and correct CPU completion.
- Supercomputing Crafting Optimizer Interface for independent global acyclic planning and compressed CPU dispatch acceleration.
- Checked `long` arithmetic and compressed tera/peta-scale material handling without quantity expansion.
- Independent per-network activation: disconnected blocks do not alter AE2 calculation or execution behavior.
- Cooperative ownership when both service blocks are active, with isolated per-job schedules and state.
- Distinct connected/offline AE2-style textures, a framed isometric mod icon, shaped AE2-component recipes, JEI discovery, and bilingual English/Chinese GuideME pages.

### Compatibility

- Minecraft 1.21.1: NeoForge 21.1.235, AE2 19.2.17, Java 21.
- Minecraft 26.1.2: NeoForge 26.1.2.94, AE2 26.1.10-beta, Java 25.
- Minecraft 1.20.1 is not supported.

### Verification

- 56 automated tests pass per generation.
- Both pinned NeoForge/AE2 data environments start successfully.
- Transformed-bytecode verification confirms calculation, plan, dispatch, cyclic-output, persistence, and completion hooks are active.
