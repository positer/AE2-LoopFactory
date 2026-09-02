package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoopStorageCellContractTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/example/ae2lightoptimizer");

    @Test
    void registersTheHousingTenCoresTenFiniteCellsAndInfiniteCell() throws IOException {
        String items = read(MAIN_JAVA.resolve("item/ModItems.java"));

        assertTrue(items.contains("LOOP_STORAGE_CELL_HOUSING"));
        for (String tier : tiers()) {
            String normalized = tier.equals("1m") ? "1M" : tier.equals("4m") ? "4M"
                    : tier.equals("16m") ? "16M" : tier.equals("64m") ? "64M"
                    : tier.equals("256m") ? "256M" : tier.toUpperCase();
            assertTrue(items.contains("LOOP_STORAGE_CORE_" + normalized), tier + " core");
            assertTrue(items.contains("LOOP_STORAGE_CELL_" + normalized), tier + " cell");
        }
        assertTrue(items.contains("INFINITE_LOOP_STORAGE_CELL"));
    }

    @Test
    void registersAUniversalHandlerAfterAe2AndEveryDriveModelOnTheClient() throws IOException {
        String entrypoint = read(MAIN_JAVA.resolve("Ae2LightOptimizer.java"));
        String client = read(MAIN_JAVA.resolve("client/Ae2LightOptimizerClient.java"));
        String handler = read(MAIN_JAVA.resolve("storage/LoopStorageCellHandler.java"));

        assertTrue(entrypoint.contains("FMLCommonSetupEvent"));
        assertTrue(entrypoint.contains("StorageCells.addCellHandler"));
        assertTrue(entrypoint.contains("LoopStorageCellHandler.INSTANCE"));
        assertFalse(entrypoint.contains("net.minecraft.client"));
        assertFalse(entrypoint.contains("neoforge.client"));
        assertTrue(client.contains("dist = Dist.CLIENT"));
        assertTrue(client.contains("FMLClientSetupEvent"));
        assertTrue(client.contains("StorageCellModels.registerModel"));
        assertTrue(client.contains("ModelEvent.RegisterAdditional"));
        assertTrue(client.contains("RegisterColorHandlersEvent.Item"));
        assertTrue(client.contains("event.enqueueWork"));
        assertTrue(handler.contains("implements ICellHandler"));
        assertTrue(handler.contains("LoopStorageCellInventory"));
    }

    @Test
    void rendersOpaqueCellLayersAndShowsCapacityTypeAndCompatibilityTooltips() throws IOException {
        String client = read(MAIN_JAVA.resolve("client/Ae2LightOptimizerClient.java"));
        String item = read(MAIN_JAVA.resolve("storage/LoopStorageCellItem.java"));
        String handler = read(MAIN_JAVA.resolve("storage/LoopStorageCellHandler.java"));

        assertTrue(client.contains("0xFFFFFFFF"));
        assertTrue(client.contains("0xFF000000"));
        assertTrue(client.contains("FastColor.ARGB32.opaque"));
        assertTrue(item.contains("appendHoverText"));
        assertTrue(item.contains("loop_storage_cell.universal"));
        assertTrue(handler.contains("loop_storage_cell.bytes"));
        assertTrue(handler.contains("loop_storage_cell.types"));
        assertFalse(handler.contains("loop_storage_cell.types_unlimited"));
        assertTrue(handler.contains("loop_storage_cell.capacity_infinite"));
    }

    @Test
    void scansAllRegisteredAeKeyTypesWithoutOptionalModClassReferences() throws IOException {
        String item = read(MAIN_JAVA.resolve("storage/LoopStorageCellItem.java"));
        String inventory = read(MAIN_JAVA.resolve("storage/LoopStorageCellInventory.java"));
        String allStorageSource = item + inventory;

        assertTrue(allStorageSource.contains("AEKeyTypes.getAll()"));
        assertTrue(inventory.contains("what.getType()"));
        assertTrue(inventory.contains("getAmountPerByte()"));
        assertFalse(allStorageSource.contains("botania."));
        assertFalse(allStorageSource.contains("ars_nouveau."));
        assertFalse(allStorageSource.contains("mekanism."));
        assertFalse(allStorageSource.contains("Class.forName"));
        assertFalse(allStorageSource.contains("what.getType() == AEKeyType.items()"));
        assertFalse(allStorageSource.contains("what.getType() == AEKeyType.fluids()"));
        assertFalse(allStorageSource.contains("instanceof AEFluidKey"));

        String metadata = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        assertEquals(1, count(metadata, "modId=\"ae2\""));
        assertFalse(metadata.contains("modId=\"botania\""));
        assertFalse(metadata.contains("modId=\"ars_nouveau\""));
        assertFalse(metadata.contains("modId=\"mekanism\""));
    }

    @Test
    void usesAe2ByteCapacitiesAndTheSpecifiedTypeRules() throws IOException {
        String tier = read(sharedStorageSource("LoopStorageTier.java"));

        assertTrue(tier.contains("SIZE_1K(\"1k\", 1_024L, 1, 8, 63, false)"));
        assertTrue(tier.contains("SIZE_4K(\"4k\", 4_096L, 1, 32, 63, false)"));
        assertTrue(tier.contains("SIZE_16K(\"16k\", 16_384L, 1, 128, 63, false)"));
        assertTrue(tier.contains("SIZE_64K(\"64k\", 65_536L, 1, 512, 63, false)"));
        assertTrue(tier.contains("SIZE_256K(\"256k\", 262_144L, 1, 2_048, 63, false)"));
        assertTrue(tier.contains("SIZE_1M(\"1m\", 1_048_576L, 63, 0, Integer.MAX_VALUE, false)"));
        assertTrue(tier.contains("SIZE_4M(\"4m\", 4_194_304L, 63, 0, Integer.MAX_VALUE, false)"));
        assertTrue(tier.contains("SIZE_16M(\"16m\", 16_777_216L, 63, 0, Integer.MAX_VALUE, false)"));
        assertTrue(tier.contains("SIZE_64M(\"64m\", 67_108_864L, 63, 0, Integer.MAX_VALUE, false)"));
        assertTrue(tier.contains("SIZE_256M(\"256m\", 268_435_456L, 63, 0, Integer.MAX_VALUE, false)"));
        assertTrue(tier.contains("INFINITE(\"infinite\", Long.MAX_VALUE, 1, 0, Integer.MAX_VALUE, true)"));
        assertTrue(tier.contains("boolean infinite"));
    }

    @Test
    void persistsGenericStacksAndUsesCheckedSaturatingCapacityArithmetic() throws IOException {
        String inventory = read(MAIN_JAVA.resolve("storage/LoopStorageCellInventory.java"));

        assertTrue(inventory.contains("AEComponents.STORAGE_CELL_INV"));
        assertTrue(inventory.contains("List<GenericStack>"));
        assertTrue(inventory.contains("Object2LongOpenHashMap<AEKey>")
                || inventory.contains("Map<AEKey, Long>"));
        assertTrue(inventory.contains("Math.addExact"));
        assertTrue(inventory.contains("Math.multiplyExact")
                || inventory.contains("Math.addExact"));
        assertTrue(inventory.contains("Long.MAX_VALUE"));
        assertTrue(inventory.contains("LoopStorageAccounting.summarize"));
        assertTrue(inventory.contains("LoopStorageAccounting.canAddNewType"));
        assertTrue(inventory.contains("LoopStorageAccounting.canGrowExistingType"));
        assertTrue(inventory.contains("return tier.idleDrain()"));
        assertTrue(inventory.contains("CellState.EMPTY"));
        assertTrue(inventory.contains("CellState.TYPES_FULL"));
        assertTrue(inventory.contains("CellState.FULL"));
        assertTrue(inventory.contains("canFitInsideCell"));
    }

    @Test
    void usesAe2ExplosionTransformForTheExactInfiniteRecipe() throws IOException {
        String json = Files.readString(Path.of(
                "src/main/resources/data/ae2lightoptimizer/recipe/infinite_loop_storage_cell.json"));

        assertTrue(json.contains("\"type\":  \"ae2:transform\"")
                || json.contains("\"type\": \"ae2:transform\""));
        assertTrue(json.contains("\"type\":  \"explosion\"")
                || json.contains("\"type\": \"explosion\""));
        assertEquals(64, count(json, "ae2lightoptimizer:256m_loop_storage_core"));
        assertEquals(1, count(json, "ae2lightoptimizer:loop_storage_cell_housing"));
        assertEquals(1, count(json, "ae2lightoptimizer:infinite_loop_storage_cell"));
        assertFalse(json.contains("minecraft:tnt"));
    }

    @Test
    void registersPortableUpgradeCardsAndAppliesTheirCellEffects() throws IOException {
        String entry = read(MAIN_JAVA.resolve("Ae2LightOptimizer.java"));
        String inventory = read(MAIN_JAVA.resolve("storage/LoopStorageCellInventory.java"));

        assertTrue(entry.contains("Upgrades.add(AEItems.FUZZY_CARD, item, 1)"));
        assertTrue(entry.contains("Upgrades.add(AEItems.INVERTER_CARD, item, 1)"));
        assertTrue(entry.contains("Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, item, 1)"));
        assertTrue(entry.contains("Upgrades.add(AEItems.VOID_CARD, item, 1)"));
        assertTrue(entry.contains("Upgrades.add(AEItems.ENERGY_CARD, item, 2)"));
        assertTrue(entry.contains("ModItems.PORTABLE_LOOP_STORAGE_CELLS"));
        assertTrue(inventory.contains("ICellWorkbenchItem"));
        assertTrue(inventory.contains("matchesFilter(what, partitionMode)"));
        assertTrue(inventory.contains("IncludeExclude.BLACKLIST"));
        assertTrue(inventory.contains("equalDistributionSlots"));
        assertTrue(inventory.contains("voidUpgrade ? amount : inserted"));
    }

    @Test
    void transformDisplayCompressesInfiniteExplosionWithoutChangingTheRecipe() throws IOException {
        String mixin = read(MAIN_JAVA.resolve("mixin/TransformRecipeDisplayMixin.java"));
        String mixins = read(Path.of("src/main/resources/ae2lightoptimizer.mixins.json"));

        assertTrue(mixin.contains("method = \"getIngredients\""));
        assertTrue(mixin.contains("new ItemStack(ModItems.LOOP_STORAGE_CORE_256M.get(), 64)"));
        assertTrue(mixin.contains("new ItemStack(ModItems.LOOP_STORAGE_CELL_HOUSING.get())"));
        assertTrue(mixin.contains("ingredients.size() != 65"));
        assertTrue(mixins.contains("TransformRecipeDisplayMixin"));
    }

    private static List<String> tiers() {
        return List.of("1k", "4k", "16k", "64k", "256k", "1m", "4m", "16m", "64m", "256m");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static Path sharedStorageSource(String fileName) {
        return Path.of("../../shared/src/main/java/com/example/ae2lightoptimizer/storage").resolve(fileName);
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = haystack.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
