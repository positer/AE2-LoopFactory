package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Locks which recipes are ordered and which are shapeless, and rejects a recipe whose JSON shape
 * disagrees with its declared type. Machines follow AE2's shaped convention; the documented
 * two-part conversions and the connector/card stay shapeless.
 */
class LoopFactoryRecipeShapeTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/ae2lightoptimizer/recipe");
    private static final String SHAPED = "minecraft:crafting_shaped";
    private static final String SHAPELESS = "minecraft:crafting_shapeless";

    @Test
    void machineRecipesAreOrderedAndKeepTheirExactMaterials() throws Exception {
        assertShaped("loop_factory_pattern_provider", List.of("E E", "LPL", " C "),
                Map.of("E", "ae2:engineering_processor", "L", "ae2lightoptimizer:loop_crystal",
                        "P", "ae2:pattern_provider", "C", "ae2lightoptimizer:4m_loop_storage_core"));
        assertShaped("loop_factory_network_terminal", List.of("L L", "CIC", " Q "),
                Map.of("L", "ae2:logic_processor", "C", "ae2lightoptimizer:loop_crystal",
                        "I", "ae2:interface", "Q", "ae2:quartz_glass"));
        assertShaped("loop_factory_pattern_encoding_panel", List.of(" K ", " T ", " C "),
                Map.of("K", "ae2:calculation_processor", "T", "ae2:pattern_encoding_terminal",
                        "C", "ae2lightoptimizer:loop_crystal"));
        assertShaped("handheld_loop_factory_encoder", List.of("I I", "KCQ", " R "),
                Map.of("I", "minecraft:iron_ingot", "K", "ae2:calculation_processor",
                        "C", "ae2lightoptimizer:loop_crystal", "Q", "ae2:quartz_glass",
                        "R", "minecraft:redstone"));
    }

    @Test
    void documentedTwoPartAndToolRecipesStayShapeless() throws Exception {
        for (String id : List.of("omnidirectional_connector", "loop_card", "loop_factory_interface_cable",
                "loop_factory_pattern", "1k_loop_storage_cell_from_housing", "portable_1k_loop_storage_cell",
                "portable_1k_loop_storage_cell_from_parts", "loop_crystal_from_fragments",
                "deconstruction_loop_crystal_block")) {
            JsonObject recipe = json(id);
            assertEquals(SHAPELESS, recipe.get("type").getAsString(), id);
            assertNull(recipe.get("pattern"), id + " must not carry a pattern");
            assertTrue(recipe.getAsJsonArray("ingredients").size() > 0, id);
        }
    }

    @Test
    void everyCraftingRecipeMatchesItsDeclaredShape() throws Exception {
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String type = recipe.get("type").getAsString();
                if (SHAPED.equals(type)) {
                    assertTrue(recipe.has("pattern"), file + " shaped without pattern");
                    assertTrue(recipe.has("key"), file + " shaped without key");
                    assertFalse(recipe.has("ingredients"), file + " shaped with ingredients");
                    List<String> rows = strings(recipe.getAsJsonArray("pattern"));
                    assertTrue(rows.size() >= 1 && rows.size() <= 3, file + " must use 1-3 rows");
                    int width = rows.get(0).length();
                    assertTrue(width >= 1 && width <= 3, file + " must use 1-3 columns");
                    rows.forEach(row -> assertEquals(width, row.length(), file + " rows must share one width"));
                } else if (SHAPELESS.equals(type)) {
                    assertFalse(recipe.has("pattern"), file + " shapeless with pattern");
                    assertFalse(recipe.has("key"), file + " shapeless with key");
                    assertTrue(recipe.has("ingredients"), file + " shapeless without ingredients");
                }
            }
        }
    }

    private void assertShaped(String id, List<String> pattern, Map<String, String> key) throws Exception {
        JsonObject recipe = json(id);
        assertEquals(SHAPED, recipe.get("type").getAsString(), id);
        assertEquals(pattern, strings(recipe.getAsJsonArray("pattern")), id);
        JsonObject declared = recipe.getAsJsonObject("key");
        assertEquals(key.keySet(), declared.keySet(), id);
        for (var entry : key.entrySet()) {
            assertEquals(entry.getValue(), ingredient(declared.get(entry.getKey())), id + ":" + entry.getKey());
        }
        assertEquals("ae2lightoptimizer:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    private static List<String> strings(com.google.gson.JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    private JsonObject json(String id) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(id + ".json"))).getAsJsonObject();
    }

    private static String ingredient(JsonElement value) {
        return value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("item").getAsString();
    }
}
