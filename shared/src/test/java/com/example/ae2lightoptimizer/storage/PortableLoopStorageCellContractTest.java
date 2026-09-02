package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortableLoopStorageCellContractTest {
    private static final List<String> TIERS = List.of(
            "1k", "4k", "16k", "64k", "256k",
            "1m", "4m", "16m", "64m", "256m");

    @Test
    void delegatesPortableUiPowerAndUpgradeBehaviorToAe2() throws IOException {
        String portable = read("src/main/java/com/example/ae2lightoptimizer/storage/PortableLoopStorageCellItem.java");
        String handler = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellHandler.java");
        String inventory = read("src/main/java/com/example/ae2lightoptimizer/storage/LoopStorageCellInventory.java");

        assertTrue(portable.contains("extends AbstractPortableCell"));
        assertTrue(portable.contains("MEStorageMenu.PORTABLE_ITEM_CELL_TYPE"));
        assertTrue(portable.contains("Upgrades.getEnergyCardMultiplier"));
        assertTrue(portable.contains("UpgradeInventories.forItem(stack, 4"));
        assertTrue(portable.contains("CellConfig.create(AEKeyTypes.getAll(), stack)"));
        assertTrue(portable.contains("getAEMaxPower(full)"));
        assertTrue(portable.contains("injectAEPower(full"));
        assertTrue(handler.contains("instanceof LoopStorageTierProvider"));
        assertTrue(inventory.contains("instanceof LoopStorageTierProvider"));
        assertFalse(portable.contains("poweredInsert"));
        assertFalse(portable.contains("poweredExtraction"));
    }

    @Test
    void registersEveryPortableTierWithEmptyAndFullCreativeVariants() throws IOException {
        String items = read("src/main/java/com/example/ae2lightoptimizer/item/ModItems.java");
        String entrypoint = read("src/main/java/com/example/ae2lightoptimizer/Ae2LightOptimizer.java");

        for (String tier : TIERS) {
            String suffix = "PortableCell(LoopStorageTier.SIZE_" + tier.toUpperCase() + ")";
            assertTrue(items.contains("register" + suffix)
                    || items.contains("p" + suffix.substring(1)), tier);
        }
        assertTrue(items.contains("registerPortableCell(LoopStorageTier.INFINITE)")
                || items.contains("portableCell(LoopStorageTier.INFINITE)"));
        assertTrue(entrypoint.contains("PORTABLE_LOOP_STORAGE_CELLS"));
        assertEquals(2, count(entrypoint, "emptyAndFullStacks()"));
    }

    @Test
    void shipsBothFiniteShapelessRecipesAndTheInfiniteCellRecipe() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        String firstChest = null;
        for (String tier : TIERS) {
            String id = "portable_" + tier + "_loop_storage_cell";
            assertNotNull(loader.getResource("assets/ae2lightoptimizer/models/item/" + id + ".json"), id);
            assertNotNull(loader.getResource("assets/ae2lightoptimizer/textures/item/" + id + "_side.png"), id);

            JsonObject fromCell = recipe(id);
            JsonArray cellIngredients = fromCell.getAsJsonArray("ingredients");
            assertEquals(3, cellIngredients.size(), id);
            String chest = ingredientId(cellIngredients.get(0));
            assertTrue(Set.of("ae2:chest", "ae2:me_chest").contains(chest), chest);
            firstChest = firstChest == null ? chest : firstChest;
            assertEquals(firstChest, chest);
            assertEquals("ae2:energy_cell", ingredientId(cellIngredients.get(1)));
            assertEquals("ae2lightoptimizer:" + tier + "_loop_storage_cell",
                    ingredientId(cellIngredients.get(2)));

            JsonObject fromParts = recipe(id + "_from_parts");
            JsonArray partIngredients = fromParts.getAsJsonArray("ingredients");
            assertEquals(4, partIngredients.size(), id);
            assertEquals(firstChest, ingredientId(partIngredients.get(0)));
            assertEquals("ae2:energy_cell", ingredientId(partIngredients.get(1)));
            assertEquals("ae2lightoptimizer:loop_storage_cell_housing",
                    ingredientId(partIngredients.get(2)));
            assertEquals("ae2lightoptimizer:" + tier + "_loop_storage_core",
                    ingredientId(partIngredients.get(3)));

            JsonObject disassembly = recipe(id + "_disassembly");
            assertEquals("ae2:storage_cell_disassembly", disassembly.get("type").getAsString());
            assertEquals("ae2lightoptimizer:" + id, disassembly.get("cell").getAsString());
            JsonArray returned = disassembly.getAsJsonArray("cell_disassembly_items");
            assertEquals(4, returned.size());
            assertEquals(firstChest, returned.get(0).getAsJsonObject().get("id").getAsString());
            assertEquals("ae2:energy_cell", returned.get(1).getAsJsonObject().get("id").getAsString());
            assertEquals("ae2lightoptimizer:loop_storage_cell_housing",
                    returned.get(2).getAsJsonObject().get("id").getAsString());
            assertEquals("ae2lightoptimizer:" + tier + "_loop_storage_core",
                    returned.get(3).getAsJsonObject().get("id").getAsString());
        }

        JsonArray infinite = recipe("portable_infinite_loop_storage_cell")
                .getAsJsonArray("ingredients");
        assertEquals(3, infinite.size());
        assertEquals(firstChest, ingredientId(infinite.get(0)));
        assertEquals("ae2:energy_cell", ingredientId(infinite.get(1)));
        assertEquals("ae2lightoptimizer:infinite_loop_storage_cell", ingredientId(infinite.get(2)));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/ae2lightoptimizer/recipe/portable_infinite_loop_storage_cell_from_parts.json")));

        JsonObject infiniteDisassembly = recipe("portable_infinite_loop_storage_cell_disassembly");
        assertEquals("ae2:storage_cell_disassembly",
                infiniteDisassembly.get("type").getAsString());
        JsonArray infiniteReturned = infiniteDisassembly.getAsJsonArray("cell_disassembly_items");
        assertEquals(3, infiniteReturned.size());
        assertEquals("ae2lightoptimizer:infinite_loop_storage_cell",
                infiniteReturned.get(2).getAsJsonObject().get("id").getAsString());
        assertFalse(infiniteDisassembly.toString().contains("loop_storage_core"));
    }

    private static JsonObject recipe(String id) throws IOException {
        return JsonParser.parseString(read(
                "src/main/resources/data/ae2lightoptimizer/recipe/" + id + ".json"))
                .getAsJsonObject();
    }

    private static String ingredientId(JsonElement ingredient) {
        return ingredient.isJsonPrimitive()
                ? ingredient.getAsString()
                : ingredient.getAsJsonObject().get("item").getAsString();
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
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
