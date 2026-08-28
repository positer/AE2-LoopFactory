package com.example.ae2lightoptimizer.block;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GuideMeDocumentationContractTest {
    private static final String GUIDE_ROOT = "assets/ae2lightoptimizer/ae2guide/";

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

    private static void assertGuidePage(String page, String itemId) {
        assertTrue(page.contains("parent: ae2:items-blocks-machines/items-blocks-machines-index.md"));
        assertTrue(page.contains("item_ids:\n- ae2lightoptimizer:" + itemId));
        assertTrue(page.contains("<BlockImage id=\"ae2lightoptimizer:" + itemId + "\""));
    }

    private String read(String relativePath) throws IOException {
        URL resource = getClass().getClassLoader().getResource(GUIDE_ROOT + relativePath);
        assertNotNull(resource, relativePath);
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
