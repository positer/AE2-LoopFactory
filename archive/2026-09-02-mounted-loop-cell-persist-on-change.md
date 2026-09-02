# 2026-09-02 Mounted Loop Storage Cell persist-on-change fix

## Symptom

In a `ImmortalStorage-1.21.1` private dimension, FE imported via an Applied Flux Flux Accessor cable into a mounted 256M / infinite Loop Storage Cell stopped at roughly 47 used bytes and only continued after a drive remount or world reload.

## Root cause

The loop cell hosted in an AE drive only called the host `ISaveProvider.saveChanges()` on content changes and did not immediately write the updated amounts back into the item-stack `STORAGE_CELL_INV` component. Drives rebuild the cell inventory from that stacked component whenever the storage provider refreshes or the drive remounts, so the rebuilt inventory read the last world-save amount and reset the in-memory total. The cell appeared to stop at a small fixed byte count until the drive rebuilt again, and each remount granted only another small burst.

## Change

Both adapters now call `persist()` (writing the current `GenericStack` contents into the item component) on every change before notifying the host. Mounted reads, tooltips, and drive rebuilds therefore always see the live amount.

## Verification

- The 1.21.1 and 26.1.2 `gradlew build --no-daemon` runs succeeded, with test suites passing.
- The temporary `ae2lo-probe` diagnostic logging added during debugging was removed; neither source nor packaged class retains it.
- The generation-matched JARs replaced AE2LO again in `AE2-lightoptimizer-1.21.1`, `AE2-lightoptimizer-26.1.2`, `ImmortalStorage-1.21.1`, and `ImmortalStorage-26.1.2`.
- JAR SHA-256: 1.21.1 `518C1531E59985ABC1CCA00877CFA67FE42B417AE0155AF0AA704204DB50E07E`; 26.1.2 `777C99B1CB2ABDFC39D09B8A3F9CC8FC3D976E29CA1974B5AC6FF9F2D17D3BC1`.
