package com.example.ae2lightoptimizer.block;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoopCrystalContentContractTest {
    @Test
    void packagesAllFourMaterialsWithNamesModelsRecipesAndDrop() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        for (String path : new String[] {
                "assets/ae2lightoptimizer/models/item/loop_crystal.json",
                "assets/ae2lightoptimizer/models/item/loop_crystal_fragment.json",
                "assets/ae2lightoptimizer/models/item/loop_crystal_powder.json",
                "assets/ae2lightoptimizer/textures/item/loop_crystal.png",
                "assets/ae2lightoptimizer/textures/item/loop_crystal_fragment.png",
                "assets/ae2lightoptimizer/textures/item/loop_crystal_powder.png",
                "assets/ae2lightoptimizer/textures/block/loop_crystal_block.png",
                "data/ae2lightoptimizer/recipe/loop_crystal_from_fragments.json",
                "data/ae2lightoptimizer/recipe/loop_crystal_to_fragments.json",
                "data/ae2/loot_table/blocks/mysterious_cube.json"
        }) {
            assertNotNull(loader.getResource(path), path);
        }
        String lang = Files.readString(Path.of("src/main/resources/assets/ae2lightoptimizer/lang/zh_cn.json"));
        assertTrue(lang.contains("\"item.ae2lightoptimizer.loop_crystal_powder\": \"循环水晶粉\""));
        String crushing = Files.readString(Path.of("src/main/resources/data/ae2lightoptimizer/recipe/loop_crystal_crushing_mekanism.json"));
        assertTrue(crushing.contains("\"modid\": \"mekanism\""));
        assertTrue(crushing.contains("\"type\": \"mekanism:crushing\""));
        assertTrue(crushing.contains("\"tag\": \"c:gems/loop_crystal\""));
        assertTrue(!crushing.contains("\"item\": \"ae2lightoptimizer:loop_crystal\""));
        assertTrue(crushing.contains("ae2lightoptimizer:loop_crystal_powder"));
        String create = Files.readString(Path.of("src/main/resources/data/ae2lightoptimizer/recipe/loop_crystal_milling_create.json"));
        assertTrue(create.contains("\"modid\": \"create\""));
        assertTrue(create.contains("\"type\": \"create:milling\""));
        assertTrue(create.contains("\"tag\": \"c:gems/loop_crystal\""));
        assertTrue(create.contains("ae2lightoptimizer:loop_crystal_powder"));
        String growth = Files.readString(Path.of("src/main/resources/data/ae2lightoptimizer/recipe/loop_crystal_shaped.json"));
        assertTrue(growth.contains("\"pattern\":[\"ABA\",\"BCB\",\"ABA\"]"));
        String drop = Files.readString(Path.of("src/main/resources/data/ae2/loot_table/blocks/mysterious_cube.json"));
        assertTrue(drop.contains("\"name\": \"ae2lightoptimizer:loop_crystal\""));
    }
}

