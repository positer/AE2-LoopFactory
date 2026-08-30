# 2026-08-30-1841 - Generic cyclic growth planner fix

- Root cause: global planning subtracted a fixed 16-cycle external reserve from current-order inventory, and compressed scheduling batched an internal decomposition before its growth consumer, falsely requiring extra seeds.
- Fix: reserve metadata remains diagnostic/execution state only; current-order external inputs use full available stock. Scheduling seeds all external inputs from the dependency graph and selects any source-ready/internal route, preserving interleaving across SCC edges without item-specific branches.
- Regression coverage: real loop-crystal two-node growth ring at 1/4/64/4096 order sizes, startup from an intermediate SCC resource, and existing multi-route/SCC tests.
- Verification: focused planner + ring solver tests passed; full builds passed for NeoForge 1.21.1 and 26.1.2. PCL instances refreshed non-destructively.
- Deployed hashes after final guide-resource rebuild: 1.21.1 D635BE8E01F177B0485454C6597BB3EC24A8EB9DF138B129D2BC55917854821D; 26.1.2 CDD4C8EF807B8B8D91675B77B163F2696EEE2D11A01BF61B413A3F238ED74E8B.
