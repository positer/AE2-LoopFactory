package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level guard for the version-local inventory invariants. */
class LoopStorageCellInventoryTest {
    @Test
    void keepsSimulationPersistenceCapacityAndNestedCellGuardsExplicit() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellInventory.java"));
        assertTrue(source.contains("mode == Actionable.MODULATE"));
        assertTrue(source.contains("AEComponents.STORAGE_CELL_INV"));
        assertTrue(source.contains("host.saveChanges()"));
        assertTrue(source.contains("LoopStorageAccounting.summarize"));
        assertTrue(source.contains("AEKeyTypes.getAll()"));
        assertTrue(source.contains("StorageCells.getCellInventory"));
        assertTrue(source.contains("canFitInsideCell"));
        assertTrue(source.contains("Math.addExact"));
        assertTrue(source.contains("high - (high - low) / 2"));
        assertTrue(!source.contains("high - low + 1"));
        assertTrue(source.contains("boolean isPreferredStorageFor"));
        assertTrue(source.contains("insert(what, 1, Actionable.SIMULATE, source) == 1"));
    }
}
