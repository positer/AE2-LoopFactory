package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoopStorageCellContractTest {
    private static final List<String> TIERS = List.of(
            "1k", "4k", "16k", "64k", "256k",
            "1m", "4m", "16m", "64m", "256m");

    @Test
    void registersHousingTenCoresTenFiniteCellsAndInfiniteCell() throws IOException {
        String items = read("src/main/java/com/example/ae2lightoptimizer/item/ModItems.java");

        assertTrue(items.contains("LOOP_STORAGE_CELL_HOUSING"));
        for (String tier : TIERS) {
            assertTrue(items.contains("core(\"" + tier + "\")"), tier + " core");
            assertTrue(items.contains("cell(LoopStorageTier.SIZE_" + tier.toUpperCase() + ")"), tier + " cell");
        }
        assertTrue(items.contains("\"infinite_loop_storage_cell\""));
        assertTrue(items.contains("new LoopStorageCellItem"));
        assertTrue(items.contains("stacksTo(1)"));
    }

    @Test
    void installsOfficialUniversalCellHandlerAndDriveModelsAtTheCorrectLifecycleBoundaries()
            throws IOException {
        String entrypoint = read("src/main/java/com/example/ae2lightoptimizer/Ae2LightOptimizer.java");
        String client = read("src/main/java/com/example/ae2lightoptimizer/client/Ae2LightOptimizerClient.java");
        String handler = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellHandler.java");
        String inventory = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellInventory.java");

        assertTrue(entrypoint.contains("FMLCommonSetupEvent"));
        assertTrue(entrypoint.contains("StorageCells.addCellHandler(LoopStorageCellHandler.INSTANCE)"));
        assertTrue(client.contains("InitializeClientRegistriesEvent"));
        assertTrue(client.contains("StorageCellModels.registerModel"));
        assertFalse(client.contains("ModelEvent.RegisterStandalone"));
        assertFalse(client.contains("SimpleUnbakedStandaloneModel"));
        assertTrue(handler.contains("implements ICellHandler"));
        assertTrue(inventory.contains("implements StorageCell"));
        assertTrue(inventory.contains("AEComponents.STORAGE_CELL_INV"));
        assertTrue(inventory.contains("GenericStack"));
        assertFalse(inventory.contains("Botania"));
        assertFalse(inventory.contains("Ars"));
        assertFalse(inventory.contains("ForgeEnergy"));
    }

    @Test
    void acceptsEveryRegisteredAeKeyTypeAndBillsUsingItsNativeAmountPerByte() throws IOException {
        String inventory = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellInventory.java");
        String handler = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellHandler.java");

        assertTrue(inventory.contains("AEKeyTypes.getAll()"));
        assertTrue(inventory.contains("what.getType().getAmountPerByte()"));
        assertTrue(inventory.contains("LoopStorageAccounting"));
        assertTrue(inventory.contains("LoopStorageAccounting.summarize"));
        assertTrue(inventory.contains("LoopStorageAccounting.canAddNewType"));
        assertTrue(inventory.contains("LoopStorageAccounting.canGrowExistingType"));
        assertTrue(inventory.contains("return tier.idleDrain()"));
        assertTrue(inventory.contains("high - (high - low) / 2"));
        assertFalse(inventory.contains("high - low + 1"));
        assertFalse(inventory.contains("orElseThrow"));
        assertTrue(inventory.contains("orElse(tier.capacityBytes())"));
        assertFalse(inventory.contains("orElse(Long.MAX_VALUE)"));
        assertFalse(inventory.contains("AEKeyType.items()"));
        assertFalse(inventory.contains("AEKeyType.fluids()"));
        assertFalse(handler.contains("instanceof IBasicCellItem"));
        assertFalse(handler.contains("loop_storage_cell.types_unlimited"));
        assertTrue(inventory.contains("boolean isPreferredStorageFor"));
        assertTrue(inventory.contains("insert(what, 1, Actionable.SIMULATE, source) == 1"));
    }

    @Test
    void registersPortableUpgradeCardsAndAppliesTheirCellEffects() throws IOException {
        String entry = read("src/main/java/com/example/ae2lightoptimizer/Ae2LightOptimizer.java");
        String inventory = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellInventory.java");

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
        assertTrue(inventory.contains("voidUpgrade ? amount"));
    }

    @Test
    void packagesModelsLanguagesAndRecipesForEveryTier() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/items/loop_storage_cell_housing.json"));
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/models/item/loop_storage_cell_housing.json"));
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/textures/item/loop_storage_cell_housing.png"));

        String zh = read("src/main/resources/assets/ae2lightoptimizer/lang/zh_cn.json");
        String en = read("src/main/resources/assets/ae2lightoptimizer/lang/en_us.json");
        for (String tier : TIERS) {
            String core = tier + "_loop_storage_core";
            String cell = tier + "_loop_storage_cell";
            assertNotNull(loader.getResource("assets/ae2lightoptimizer/textures/item/" + core + ".png"), core);
            assertNotNull(loader.getResource("assets/ae2lightoptimizer/textures/item/" + cell + ".png"), cell);
            assertNotNull(loader.getResource("data/ae2lightoptimizer/recipe/" + core + ".json"), core);
            assertNotNull(loader.getResource("data/ae2lightoptimizer/recipe/" + cell + ".json"), cell);
            assertNotNull(loader.getResource("data/ae2lightoptimizer/recipe/" + cell + "_from_housing.json"), cell);
            assertTrue(zh.contains("item.ae2lightoptimizer." + core));
            assertTrue(zh.contains("item.ae2lightoptimizer." + cell));
            assertTrue(en.contains("item.ae2lightoptimizer." + core));
            assertTrue(en.contains("item.ae2lightoptimizer." + cell));
        }
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/textures/item/infinite_loop_storage_cell.png"));
        assertNotNull(loader.getResource("data/ae2lightoptimizer/recipe/infinite_loop_storage_cell.json"));
    }

    @Test
    void infiniteCellUsesAe2ExplosionTransformAndConsumesExactlySixtyFourCoresAndOneHousing()
            throws IOException {
        JsonObject recipe = JsonParser.parseString(read(
                "src/main/resources/data/ae2lightoptimizer/recipe/infinite_loop_storage_cell.json"))
                .getAsJsonObject();

        assertEquals("ae2:transform", recipe.get("type").getAsString());
        assertEquals("explosion", recipe.getAsJsonObject("circumstance").get("type").getAsString());
        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(65, ingredients.size());
        long coreCount = ingredients.asList().stream()
                .filter(element -> element.getAsString()
                        .equals("ae2lightoptimizer:256m_loop_storage_core"))
                .count();
        long housingCount = ingredients.asList().stream()
                .filter(element -> element.getAsString()
                        .equals("ae2lightoptimizer:loop_storage_cell_housing"))
                .count();
        assertEquals(64, coreCount);
        assertEquals(1, housingCount);
        assertEquals("ae2lightoptimizer:infinite_loop_storage_cell",
                recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void transformDisplayCompressesInfiniteExplosionWithoutChangingTheRecipe() throws IOException {
        String mixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/TransformCategoryDisplayMixin.java");
        String mixins = read("src/main/resources/ae2lightoptimizer.mixins.json");

        assertTrue(mixin.contains("method = \"buildSlots\""));
        assertTrue(mixin.contains("new ItemStack(ModItems.LOOP_STORAGE_CORE_256M.get(), 64)"));
        assertTrue(mixin.contains("new ItemStack(ModItems.LOOP_STORAGE_CELL_HOUSING.get())"));
        assertTrue(mixin.contains("RecipeIngredientRole.CRAFTING_STATION"));
        assertTrue(mixins.contains("TransformCategoryDisplayMixin"));
        assertFalse(mixin.contains("InfiniteLoopStorageJeiPlugin"));
    }

    @Test
    void correctedSixteenKCellRecipeUsesTheSixteenKCore() throws IOException {
        JsonObject recipe = JsonParser.parseString(read(
                "src/main/resources/data/ae2lightoptimizer/recipe/16k_loop_storage_cell.json"))
                .getAsJsonObject();
        assertEquals("ae2lightoptimizer:16k_loop_storage_core",
                recipe.getAsJsonObject("key").get("C").getAsString());
        assertFalse(recipe.toString().contains("13k_loop_storage_core"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
