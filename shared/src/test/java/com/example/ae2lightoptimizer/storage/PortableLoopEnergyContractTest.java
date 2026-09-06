package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Wiring guards complement the executable immutable-component and long-conservation tests. */
class PortableLoopEnergyContractTest {
    private static final String JAVA = "src/main/java/com/example/ae2lightoptimizer/";

    @Test
    void capabilityAndUpgradeAreRegisteredForEveryPortableTier() throws IOException {
        String entry = Files.readString(Path.of(JAVA + "Ae2LightOptimizer.java"));
        String item = Files.readString(Path.of(JAVA + "item/ModItems.java"));
        String energy = Files.readString(Path.of(JAVA + "storage/PortableLoopEnergy.java"));
        String portable = Files.readString(Path.of(JAVA + "storage/PortableLoopStorageCellItem.java"));
        assertTrue(entry.contains("PortableLoopEnergy.registerCapabilities(event)"));
        assertTrue(entry.contains("Upgrades.add(ModItems.LOOP_CARD, item, 1)")
                || entry.contains("Upgrades.add(ModItems.LOOP_CARD.get(), item, 1)"));
        assertTrue(item.contains("Upgrades.createUpgradeCardItem") || item.contains("Upgrades::createUpgradeCardItem"));
        assertTrue(energy.contains("ModItems.PORTABLE_LOOP_STORAGE_CELLS"));
        assertTrue(portable.contains("PortableLoopEnergy.recharge(stack, this)"));
        assertTrue(energy.contains("getUpgrades(stack).isInstalled(ModItems.LOOP_CARD)"));
        assertTrue(energy.contains("PowerUnit.FE.convertTo(PowerUnit.AE, 1)"));
        assertFalse(energy.contains("import com.glodblock"));
        assertTrue(energy.contains("if (!simulate && debit.extracted() > 0)"));
    }

    @Test
    void cachedPortableInventoriesRefreshBeforeEveryStorageBoundary() throws IOException {
        String inventory = Files.readString(Path.of(JAVA + "storage/LoopStorageCellInventory.java")).replace("\r\n", "\n");
        assertTrue(inventory.contains("loadedContents != cellStack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of())"));
        for (String signature : new String[] {
                "public long storedTypeCount() {", "public long usedBytes() {",
                "public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {",
                "public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {",
                "public void getAvailableStacks(KeyCounter out) {", "public CellState getStatus() {",
                "public boolean canFitInsideCell() {"}) {
            assertTrue(inventory.contains(signature + "\n        refreshPortableContents();"), signature);
        }
        assertTrue(inventory.contains("loadedContents = cellStack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());\n        dirty = false;"));
    }
}
