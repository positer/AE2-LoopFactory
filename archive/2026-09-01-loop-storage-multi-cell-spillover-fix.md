# Loop Storage multi-cell spillover fix

Date: 2026-09-01

## Reported behavior

Once one Loop Storage Cell filled for a storage key class, insertion did not reliably continue into another Loop Storage Cell of the same class. The practical capacity of one item, fluid, FE, mana, source, soul, or addon key could therefore be limited to one disk when the insertion path relied on preferred-storage discovery.

## Cause and correction

The cell inventories implemented partial insertion correctly but inherited the default `MEStorage.isPreferredStorageFor`, which always returned false. AE2's native drive wrapper has a fallback pass, but key-type-aware addon paths may use the preferred-storage contract to select candidate mounts.

Both version adapters now override `isPreferredStorageFor`. The method rejects unregistered key types and forbidden nested cells, then performs a one-unit `Actionable.SIMULATE` insertion. Full cells return false; the next compatible Loop Storage Cell with remaining capacity returns true. Simulation does not mutate or persist cell contents.

## Verification and deployment

- Minecraft 1.21.1 complete test/build passed.
- Minecraft 26.1.2 complete test/build passed.
- Source contracts require the capacity-aware preferred-storage implementation in both adapters.
- Both PCL compatibility instances were refreshed through the non-destructive provisioner; save manifests were unchanged.
- 1.21.1 JAR SHA-256: `F3EE06C5A46BBB8785D610B73F35994722BF658E9BE480D7E5295622B1CB9008`.
- 26.1.2 JAR SHA-256: `836C349DB96074B2095CD8AD9CF16F7686797B0A626640F429AE3C04EB8514D5`.

AE2 remains the only storage-mod prerequisite. Applied Flux and other storage addons remain optional compatibility targets.
