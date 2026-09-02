package com.example.ae2lightoptimizer.block;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideMeDocumentationContractTest {
    private static final String GUIDE_ROOT = "assets/ae2lightoptimizer/ae2guide/";
    private static final List<String> LOOP_STORAGE_TIERS = List.of(
            "1k", "4k", "16k", "64k", "256k", "1m", "4m", "16m", "64m", "256m");

    @Test
    void indexesBothBlocksInTheExistingAe2Guide() throws IOException {
        String ring = read("items-blocks-machines/recipe_ring_solver_terminal.md");
        String optimizer = read("items-blocks-machines/supercomputing_crafting_optimizer_interface.md");

        assertGuidePage(ring, "recipe_ring_solver_terminal");
        assertGuidePage(optimizer, "supercomputing_crafting_optimizer_interface");
        assertTrue(ring.contains("supercomputing_crafting_optimizer_interface.md"));
        assertTrue(optimizer.contains("recipe_ring_solver_terminal.md"));
        assertNull(getClass().getClassLoader().getResource(
                "assets/ae2lightoptimizer/guideme_guides/guide.json"));
    }

    @Test
    void shipsCompleteChineseMirrors() throws IOException {
        String ring = read("_zh_cn/items-blocks-machines/recipe_ring_solver_terminal.md");
        String optimizer = read("_zh_cn/items-blocks-machines/supercomputing_crafting_optimizer_interface.md");

        assertGuidePage(ring, "recipe_ring_solver_terminal");
        assertGuidePage(optimizer, "supercomputing_crafting_optimizer_interface");
        assertTrue(ring.contains("# \u914d\u65b9\u73af\u89e3\u7b97\u7ec8\u7aef"));
        assertTrue(ring.contains("16 \u6b21\u5faa\u73af"));
        assertTrue(optimizer.contains("# \u8d85\u7b97\u5408\u6210\u4f18\u5316\u63a5\u53e3"));
        assertTrue(optimizer.contains("\u5b89\u5168\u56de\u9000"));
    }

    @Test
    void shipsTheCompleteBilingualLoopStorageGuide() throws IOException {
        String english = read("items-blocks-machines/loop_storage_cells.md");
        String chinese = read("_zh_cn/items-blocks-machines/loop_storage_cells.md");

        assertLoopStorageGuide(english);
        assertLoopStorageGuide(chinese);
        assertTrue(english.contains("every storage key type registered with AE2"));
        assertTrue(english.contains("AE2 is the only required mod"));
        assertTrue(english.contains("exactly 64"));
        assertTrue(english.contains("TNT explosion"));
        assertTrue(chinese.contains("# \u5faa\u73af\u5b58\u50a8\u78c1\u76d8"));
        assertTrue(chinese.contains("\u53ea\u8981\u6c42 AE2 \u4f5c\u4e3a\u524d\u7f6e"));
        assertTrue(chinese.contains("\u6b63\u597d 64 \u4e2a"));
        assertTrue(chinese.contains("TNT \u7206\u70b8"));
    }

    private static void assertGuidePage(String page, String itemId) {
        assertTrue(page.contains("parent: ae2:items-blocks-machines/items-blocks-machines-index.md"));
        assertTrue(page.contains("item_ids:\n- ae2lightoptimizer:" + itemId));
        assertTrue(page.contains("<BlockImage id=\"ae2lightoptimizer:" + itemId + "\""));
        assertTrue(page.contains("<RecipeFor id=\"ae2lightoptimizer:" + itemId + "\" />"));
    }

    private static void assertLoopStorageGuide(String page) {
        assertTrue(page.contains("parent: ae2:items-blocks-machines/items-blocks-machines-index.md"));
        assertTrue(page.contains("- ae2lightoptimizer:loop_storage_cell_housing"));
        assertTrue(page.contains("<RecipeFor id=\"ae2lightoptimizer:loop_storage_cell_housing\" />"));
        for (String tier : LOOP_STORAGE_TIERS) {
            assertTrue(page.contains("- ae2lightoptimizer:" + tier + "_loop_storage_core"), tier);
            assertTrue(page.contains("- ae2lightoptimizer:" + tier + "_loop_storage_cell"), tier);
            assertTrue(page.contains("<RecipeFor id=\"ae2lightoptimizer:" + tier
                    + "_loop_storage_core\" />"), tier);
            assertTrue(page.contains("<RecipeFor id=\"ae2lightoptimizer:" + tier
                    + "_loop_storage_cell\" />"), tier);
        }
        assertTrue(page.contains("- ae2lightoptimizer:infinite_loop_storage_cell"));
        assertTrue(page.contains("<ItemImage id=\"ae2lightoptimizer:infinite_loop_storage_cell\""));
        assertTrue(page.contains("<RecipeFor id=\"ae2lightoptimizer:infinite_loop_storage_cell\" />"));
    }

    private String read(String relativePath) throws IOException {
        URL resource = getClass().getClassLoader().getResource(GUIDE_ROOT + relativePath);
        assertNotNull(resource, relativePath);
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
