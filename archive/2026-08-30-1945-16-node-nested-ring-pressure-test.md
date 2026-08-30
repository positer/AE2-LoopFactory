# 2026-08-30-1945 - 16-node nested-ring pressure test

- Added a generic shared-planner pressure test covering 16 cyclic nodes arranged as four four-node growth rings with three nested one-way bridge dependencies.
- The test uses a 10,000-unit request, independent catalyst supply, entry seeds, intermediate ring stock, SCC classification, compressed pattern counts, bounded scheduling, and missing-stock verification.
- Focused pressure test passed. Full NeoForge 1.21.1 and 26.1.2 builds/tests passed afterward.
- The scenario is intentionally generated from graph topology and resource names in the test; production code contains no Loop Crystal-specific branch.
