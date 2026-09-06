package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CraftingRipperResourceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String ASSETS = "assets/ae2lightoptimizer/";

    @Test
    void preservesTheExactRequestedMachineAndAdvancedCardRecipes() throws Exception {
        JsonObject ripper = json("data/ae2lightoptimizer/recipe/crafting_ripper.json");
        assertEquals("minecraft:crafting_shaped", ripper.get("type").getAsString());
        assertEquals(List.of("ISI", "FCD", "ISI"), ripper.getAsJsonArray("pattern")
                .asList().stream().map(JsonElement::getAsString).toList());
        JsonObject key = ripper.getAsJsonObject("key");
        assertEquals("minecraft:iron_ingot", ingredient(key.get("I")));
        assertEquals("ae2:quantum_entangled_singularity", ingredient(key.get("S")));
        assertEquals("ae2:formation_core", ingredient(key.get("F")));
        assertEquals("ae2lightoptimizer:4m_loop_storage_core", ingredient(key.get("C")));
        assertEquals("ae2:annihilation_core", ingredient(key.get("D")));
        assertEquals("ae2lightoptimizer:crafting_ripper", ripper.getAsJsonObject("result").get("id").getAsString());
        JsonObject card = json("data/ae2lightoptimizer/recipe/loop_card.json");
        assertEquals("minecraft:crafting_shapeless", card.get("type").getAsString());
        assertEquals(2, card.getAsJsonArray("ingredients").size());
        assertEquals(Set.of("ae2:advanced_card", "ae2lightoptimizer:loop_crystal"),
                card.getAsJsonArray("ingredients").asList().stream()
                        .map(CraftingRipperResourceTest::ingredient).collect(Collectors.toSet()));
    }

    @Test
    void loopCardChangesOnlyTheNativeAccelerationCardEmblem() throws Exception {
        BufferedImage original;
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/ae2/textures/item/card_speed.png")) {
            assertNotNull(stream);
            original = ImageIO.read(stream);
        }
        BufferedImage card = image("item/loop_card.png");
        assertEquals(original.getWidth(), card.getWidth());
        assertEquals(original.getHeight(), card.getHeight());
        int changes = 0;
        for (int y = 0; y < card.getHeight(); y++) {
            for (int x = 0; x < card.getWidth(); x++) {
                int before = original.getRGB(x, y);
                int after = card.getRGB(x, y);
                assertEquals(before >>> 24, after >>> 24, "alpha at " + x + "," + y);
                if (x < 4 || x > 10 || y < 3 || y > 9) {
                    assertEquals(before, after, "native shell/contact at " + x + "," + y);
                } else if (before != after) {
                    changes++;
                }
            }
        }
        assertTrue(changes > 0, "the acceleration emblem must actually be replaced");
    }

    @Test
    void machineStatesShareTheFrameAndNineCellGeometry() throws Exception {
        BufferedImage offline = image("block/crafting_ripper.png");
        BufferedImage online = image("block/crafting_ripper_connected.png");
        BufferedImage reference = image("block/supercomputing_crafting_optimizer_interface.png");
        assertEquals(16, offline.getWidth());
        assertEquals(16, offline.getHeight());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(255, offline.getRGB(x, y) >>> 24);
                assertEquals(255, online.getRGB(x, y) >>> 24);
                if (x < 3 || x > 12 || y < 3 || y > 12) {
                    assertEquals(reference.getRGB(x, y), offline.getRGB(x, y));
                    assertEquals(offline.getRGB(x, y), online.getRGB(x, y));
                } else {
                    assertEquals(offline.getRGB(x, y) == 0xff151b21, online.getRGB(x, y) == 0xff151b21);
                }
            }
        }
        for (int y : new int[] {4, 7, 10}) {
            for (int x : new int[] {4, 7, 10}) {
                assertNotEquals(offline.getRGB(x, y), online.getRGB(x, y));
            }
        }
        JsonObject states = json(ASSETS + "blockstates/crafting_ripper.json").getAsJsonObject("variants");
        assertEquals("ae2lightoptimizer:block/crafting_ripper", states.getAsJsonObject("connected=false").get("model").getAsString());
        assertEquals("ae2lightoptimizer:block/crafting_ripper_connected", states.getAsJsonObject("connected=true").get("model").getAsString());
    }

    private BufferedImage image(String path) throws Exception {
        return ImageIO.read(RESOURCES.resolve(ASSETS + "textures/" + path).toFile());
    }

    private JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(path))).getAsJsonObject();
    }

    private static String ingredient(JsonElement value) {
        return value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("item").getAsString();
    }
}
