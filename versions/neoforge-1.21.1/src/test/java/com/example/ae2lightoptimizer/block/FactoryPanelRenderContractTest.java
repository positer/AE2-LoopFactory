package com.example.ae2lightoptimizer.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FactoryPanelRenderContractTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/ae2lightoptimizer");

    @Test
    void panelUsesNativeAe2CompositionWithOnlyCoreMasks() throws IOException {
        JsonObject off = read(ASSETS.resolve("models/part/loop_factory_panel_off.json"));
        assertEquals(Set.of("parent", "textures"), off.keySet());
        assertEquals("ae2:part/display_off", off.get("parent").getAsString());
        assertMaskTextures(off.getAsJsonObject("textures"));

        JsonObject on = read(ASSETS.resolve("models/part/loop_factory_panel_on.json"));
        assertEquals(Set.of("textures", "elements"), on.keySet());
        assertMaskTextures(on.getAsJsonObject("textures"));
        assertNativeElements(on);

        JsonObject item = read(ASSETS.resolve("models/item/loop_factory_pattern_encoding_panel.json"));
        assertEquals(Set.of("parent", "textures"), item.keySet());
        assertEquals("ae2:item/display_base", item.get("parent").getAsString());
        JsonObject itemTextures = item.getAsJsonObject("textures");
        assertEquals(
                Set.of("front", "front_bright", "front_medium", "front_dark"),
                itemTextures.keySet());
        assertEquals("ae2:part/pattern_encoding_terminal", itemTextures.get("front").getAsString());
        assertEquals(
                "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_bright",
                itemTextures.get("front_bright").getAsString());
        assertFalse(item.has("gui_light"));
        assertFalse(itemTextures.has("front_base"));
        assertFalse(itemTextures.has("front_medium_bright"));

        ClassLoader loader = getClass().getClassLoader();
        for (String layer : new String[] {"bright", "medium", "dark"}) {
            assertNotNull(loader.getResource(
                    "assets/ae2lightoptimizer/textures/part/loop_factory_pattern_encoding_panel_"
                            + layer + ".png"));
        }
        assertFalse(loader.getResource(
                "assets/ae2lightoptimizer/textures/part/loop_factory_pattern_encoding_panel_item_base.png")
                != null);
        assertFalse(loader.getResource(
                "assets/ae2lightoptimizer/textures/part/loop_factory_pattern_encoding_panel_empty.png")
                != null);
    }

    private static void assertMaskTextures(JsonObject textures) {
        assertEquals(
                Set.of("lightsBright", "lightsMedium", "lightsDark"),
                textures.keySet());
        assertEquals(
                "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_bright",
                textures.get("lightsBright").getAsString());
        assertEquals(
                "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_medium",
                textures.get("lightsMedium").getAsString());
        assertEquals(
                "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_dark",
                textures.get("lightsDark").getAsString());
    }

    private static void assertNativeElements(JsonObject on) {
        var elements = on.getAsJsonArray("elements");
        assertEquals(3, elements.size());
        int[] tints = {3, 2, 1};
        String[] refs = {"#lightsBright", "#lightsMedium", "#lightsDark"};
        for (int index = 0; index < tints.length; index++) {
            JsonObject face = elements.get(index).getAsJsonObject()
                    .getAsJsonObject("faces").getAsJsonObject("north");
            assertEquals(refs[index], face.get("texture").getAsString());
            assertEquals(tints[index], face.get("tintindex").getAsInt());
            assertTrue(face.has("neoforge_data"));
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
