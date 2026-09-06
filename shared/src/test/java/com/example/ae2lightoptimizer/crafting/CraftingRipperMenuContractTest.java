package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CraftingRipperMenuContractTest {
    @Test
    void fourPatternRowsAndPlayerInventoryFitWithoutExposingReturnSlots() throws Exception {
        var style = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/ae2lightoptimizer_crafting_ripper.json"))).getAsJsonObject();
        var pattern = style.getAsJsonObject("slots").getAsJsonObject("ENCODED_PATTERN");
        assertEquals("BREAK_AFTER_9COLS", pattern.get("grid").getAsString());
        int width = style.getAsJsonObject("generatedBackground").get("width").getAsInt();
        int height = style.getAsJsonObject("generatedBackground").get("height").getAsInt();
        var rectangles = new ArrayList<Rect>();
        for (int i = 0; i < 36; i++) rectangles.add(new Rect(
                pattern.get("left").getAsInt() + 18 * (i % 9),
                pattern.get("top").getAsInt() + 18 * (i / 9)));
        assertTrue(height <= 214, "Four pattern rows and player inventory should fit compactly at normal GUI scale");
        int screenTopAt720AndScale3 = (240 - height) / 2;
        assertTrue(screenTopAt720AndScale3 + style.getAsJsonObject("widgets")
                .getAsJsonObject("openPriority").get("top").getAsInt() >= 0,
                "Priority control must not extend beyond the top of a 720p GUI-scale-3 viewport");
        var returns = style.getAsJsonObject("slots").getAsJsonObject("STORAGE");
        assertTrue(returns.get("hidden").getAsBoolean(), "Native return inventory must have no visible slots");
        assertFalse(style.getAsJsonObject("text").has("interface_stored_items"));
        var player = style.getAsJsonObject("slots").getAsJsonObject("PLAYER_INVENTORY");
        var hotbar = style.getAsJsonObject("slots").getAsJsonObject("PLAYER_HOTBAR");
        for (int i = 0; i < 27; i++) rectangles.add(new Rect(player.get("left").getAsInt() + 18 * (i % 9),
                player.get("top").getAsInt() + 18 * (i / 9)));
        for (int i = 0; i < 9; i++) rectangles.add(new Rect(hotbar.get("left").getAsInt() + 18 * i,
                hotbar.get("top").getAsInt()));
        for (int i = 0; i < rectangles.size(); i++) {
            var a = rectangles.get(i);
            assertTrue(a.x - 1 >= 0 && a.y - 1 >= 0 && a.x + 17 <= width && a.y + 17 <= height, "Slot outside screen");
            for (int j = i + 1; j < rectangles.size(); j++) {
                var b = rectangles.get(j);
                assertFalse(a.x - 1 < b.x + 17 && a.x + 17 > b.x - 1 && a.y - 1 < b.y + 17 && a.y + 17 > b.y - 1,
                        "Slot groups overlap: " + i + " and " + j);
            }
        }
        assertTrue(style.getAsJsonObject("widgets").has("openPriority"));
        assertTrue(style.getAsJsonObject("widgets").has("lockReason"));
    }

    @Test
    void nativeProviderControlsAreInheritedAndServerInventoryPreservesWithdrawal() throws Exception {
        var source = Path.of("src/main/java/com/example/ae2lightoptimizer");
        var menu = Files.readString(source.resolve("menu/CraftingRipperMenu.java"));
        assertTrue(menu.contains("extends PatternProviderMenu"));
        assertTrue(menu.contains("getSlots(SlotSemantics.STORAGE)"));
        assertTrue(menu.contains("aeSlot.setSlotEnabled(false)"), "Return slots must reject interaction on both menu sides");
        var screen = Files.readString(source.resolve("client/CraftingRipperScreen.java"));
        assertTrue(screen.contains("extends PatternProviderScreen<CraftingRipperMenu>"));
        assertTrue(screen.contains("SLOT_BACKGROUND"), "Generated backgrounds need explicit native slot frames");
        assertFalse(screen.contains("SlotSemantics.STORAGE"), "Hidden return slots must not draw frames");
        assertTrue(screen.contains("SlotSemantics.PLAYER_INVENTORY"));
        assertTrue(screen.contains("SlotSemantics.PLAYER_HOTBAR"));
        var logic = Files.readString(source.resolve("block/CraftingRipperLogic.java"));
        assertTrue(logic.contains("!host.isLoopCardInstalled() && acceptsPattern(stack)"));
        assertTrue(logic.contains("delegate.setItemDirect(slot, stack)"));
        assertFalse(logic.contains("if (stack.isEmpty()"), "Rollback must restore retained patterns while insertion is locked");
        assertTrue(logic.contains("ICraftingProvider.requestUpdate(host.getMainNode())"));
        var entity = Files.readString(source.resolve("block/CraftingRipperBlockEntity.java"));
        assertTrue(entity.contains("logic.addDrops(drops)"));
        assertTrue(entity.contains("drops.add(stack.copy())"));
    }

    @Test
    void upgradeSlotsAndPanelsAreReusedAndOptionalJeiReceivesTheirBounds() throws Exception {
        var source = Path.of("src/main/java/com/example/ae2lightoptimizer");
        var menu = Files.readString(source.resolve("menu/CraftingRipperMenu.java"));
        assertTrue(menu.contains("if (getSlots(SlotSemantics.UPGRADE).isEmpty())"),
                "An AE2 extension may have already added the host's upgrade inventory");
        var screen = Files.readString(source.resolve("client/CraftingRipperScreen.java"));
        assertTrue(screen.contains("ae2lightoptimizer$getWidgets().containsKey(\"upgrades\")"));
        assertTrue(screen.contains("ae2lightoptimizer$getCompositeWidgets().containsKey(\"upgrades\")"));
        assertTrue(screen.contains("widgets.add(\"upgrades\", new appeng.client.gui.widgets.UpgradesPanel("),
                "The Loop Card must remain usable without an extension supplying the native panel");
        assertFalse(screen.contains("com.glodblock"));
        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/ae2lightoptimizer.mixins.json")))
                .getAsJsonObject();
        assertTrue(mixins.getAsJsonArray("client").toString().contains("WidgetContainerAccessor"));
        assertFalse(mixins.getAsJsonArray("mixins").toString().contains("WidgetContainerAccessor"),
                "Client widget access must not be loaded by a dedicated server");
        var jei = Files.readString(source.resolve("client/CraftingRipperJeiPlugin.java"));
        assertTrue(jei.contains("@JeiPlugin"));
        assertTrue(jei.contains("registration.addGuiContainerHandler(CraftingRipperScreen.class"));
        assertTrue(jei.contains("return screen.getExclusionZones();"),
                "JEI needs the actual native toolbar/panel exclusion rectangles, beyond the main screen bounds");
        assertTrue(Files.readString(Path.of("build.gradle")).contains("compileOnly 'mezz.jei:"));
    }

    private record Rect(int x, int y) {}
}
