package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactorySelectorTest {
    @Test void recipeParametersMatchAssignedKeysAndCanBeExcluded() {
        var selector = FactorySelector.parse("minecraft::item!(P1,P2)");
        assertFalse(selector.matches("minecraft::item", "minecraft:iron_ingot", index -> index == 1));
        assertTrue(selector.matches("minecraft::item", "minecraft:iron_ingot", index -> false));
        assertTrue(FactorySelector.parse("P2").matches("minecraft::fluid", "minecraft:water", index -> index == 2));
        assertEquals(java.util.Set.of(1, 2), selector.parameters());
    }
    @Test void parametersRequireRecipeAndValidMaterialIndex() {
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("get P1 from source", false));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("get P3 from source", true, 2));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("if source has P3 do\n    done", true, 2));
        assertThrows(IllegalArgumentException.class, () -> FactorySelector.parse("P0"));
        assertThrows(IllegalArgumentException.class, () -> FactorySelector.parse("P01"));
        assertThrows(IllegalArgumentException.class, () -> FactorySelector.parse("P999999999999999999999"));
        assertDoesNotThrow(() -> FactoryCompiler.compile("get P2 from source\nput *!(P1) into storage", true, 2));
    }
    @Test void broadItemTypeExcludesTwoExactIds() {
        var selector = FactorySelector.parse("minecraft::item!(minecraft:stone,minecraft:dirt)");
        assertTrue(selector.matches("minecraft::item", "minecraft:iron_ingot"));
        assertFalse(selector.matches("minecraft::item", "minecraft:stone"));
        assertFalse(selector.matches("minecraft::item", "minecraft:dirt"));
        assertFalse(selector.matches("minecraft::fluid", "minecraft:water"));
    }
    @Test void wildcardExclusionsAndNestedDifferences() {
        var selector = FactorySelector.parse("*!(minecraft::fluid, minecraft:*!(minecraft:iron_ingot))");
        assertTrue(selector.matches("minecraft::item", "minecraft:iron_ingot"));
        assertTrue(selector.matches("minecraft::item", "ae2:logic_processor"));
        assertFalse(selector.matches("minecraft::item", "minecraft:stone"));
        assertFalse(selector.matches("minecraft::fluid", "someaddon:oil"));
    }
    @Test void selectorWorksInTransferAndCountExpressions() {
        var program = FactoryCompiler.compile("""
                import A
                if A has minecraft::item!(minecraft:stone, minecraft:dirt) > 0 do
                    get 64 minecraft::item!(minecraft:stone, minecraft:dirt) from A
                    put minecraft::item!(minecraft:stone,minecraft:dirt) into source
                """, true);
        assertTrue(program.instructions().stream().anyMatch(i -> i.op() == FactoryProgram.Op.GET && i.amount() == 64
                && i.argument().startsWith("minecraft::item!(minecraft:stone,minecraft:dirt) ")));
    }
    @Test void rejectsMalformedExclusions() {
        for (String source : new String[]{"", "a!", "a!()", "a!(b,)", "a!(,b)", "a!(b", "a!(b))", "a!(b)c", "a:::b", "a b"})
            assertThrows(IllegalArgumentException.class, () -> FactorySelector.parse(source), source);
    }
    @Test void feCanBeExcludedFromAllRegisteredTypes() {
        var selector = FactorySelector.parse("*!(neoforge::fe)");
        assertFalse(selector.matches("neoforge::fe", "appflux:energy"));
        assertTrue(selector.matches("minecraft::fluid", "minecraft:water"));
    }
    @Test void ampersandAggregatesOperandsIntoOneSet() {
        var recipe = FactorySelector.parse("P1&O1");
        assertEquals(java.util.Set.of(1, -1), recipe.parameters());
        assertTrue(recipe.matches("minecraft::item", "minecraft:iron_ingot", index -> index == 1));
        assertTrue(recipe.matches("minecraft::item", "minecraft:iron_ingot", index -> index == -1));
        assertFalse(recipe.matches("minecraft::item", "minecraft:iron_ingot", index -> index == 2));
        assertEquals("P1&O1", recipe.canonical());

        var types = FactorySelector.parse("minecraft::item&minecraft::fluid");
        assertTrue(types.matches("minecraft::item", "minecraft:iron_ingot"));
        assertTrue(types.matches("minecraft::fluid", "minecraft:water"));
        assertFalse(types.matches("neoforge::fe", "appflux:energy"));

        var three = FactorySelector.parse("minecraft::item&minecraft::fluid&neoforge::fe");
        assertTrue(three.matches("neoforge::fe", "appflux:energy"));

        var excluded = FactorySelector.parse("minecraft::item&minecraft::fluid!(minecraft:water)");
        assertFalse(excluded.matches("minecraft::fluid", "minecraft:water"));
        assertTrue(excluded.matches("minecraft::fluid", "minecraft:lava"));
        assertTrue(excluded.matches("minecraft::item", "minecraft:iron_ingot"));
        assertEquals("minecraft::item&minecraft::fluid!minecraft:water", excluded.canonical());
    }
    @Test void ampersandWorksInProgramsAndExpressions() {
        var program = FactoryCompiler.compile("""
                import A
                if A has P1&O1 > 0 do
                    get P1&O1 from A
                    put must 2 P1&O1 into storage
                """, true, 3, 2);
        assertTrue(program.instructions().stream().anyMatch(i -> i.op() == FactoryProgram.Op.GET
                && i.argument().startsWith("P1&O1 ")));
        assertTrue(program.instructions().stream().anyMatch(i -> i.op() == FactoryProgram.Op.PUT
                && i.must() && i.amount() == 2 && i.argument().startsWith("P1&O1 ")));

        assertThrows(FactoryProgram.CompileException.class,
                () -> FactoryCompiler.compile("get P1&P4 from source", true, 3));
        assertThrows(FactoryProgram.CompileException.class,
                () -> FactoryCompiler.compile("get P1&O1 from source", false));
    }
    @Test void rejectsMalformedAmpersandAggregations() {
        for (String source : new String[]{"&P1", "P1&", "P1&&P2", "P1& !(P2)", "(P1", "P1)", "P1!(P2"})
            assertThrows(IllegalArgumentException.class, () -> FactorySelector.parse(source), source);
    }
    @Test void parenthesesGiveExplicitPrecedenceAndOperatorsFoldLeftToRight() {
        var grouped = FactorySelector.parse("(minecraft::item&minecraft::fluid)!(minecraft:water&minecraft:lava)");
        assertTrue(grouped.matches("minecraft::item", "minecraft:iron_ingot"));
        assertTrue(grouped.matches("minecraft::fluid", "minecraft:oil"));
        assertFalse(grouped.matches("minecraft::fluid", "minecraft:water"));
        assertFalse(grouped.matches("minecraft::fluid", "minecraft:lava"));

        var chained = FactorySelector.parse("minecraft::item&minecraft::fluid!minecraft:water!minecraft:lava");
        assertTrue(chained.matches("minecraft::item", "minecraft:iron_ingot"));
        assertFalse(chained.matches("minecraft::fluid", "minecraft:water"));
        assertFalse(chained.matches("minecraft::fluid", "minecraft:lava"));
        assertTrue(chained.matches("minecraft::fluid", "minecraft:oil"));

        // (A \ B) ∪ C keeps iron; flattening first would have removed it permanently.
        var leftToRight = FactorySelector.parse("minecraft::item!minecraft:iron_ingot&minecraft:iron_ingot");
        assertTrue(leftToRight.matches("minecraft::item", "minecraft:iron_ingot"));
        assertTrue(leftToRight.matches("minecraft::item", "minecraft:gold_ingot"));

        assertEquals("minecraft::item&minecraft::fluid!(minecraft:water&minecraft:lava)",
                grouped.canonical());
        assertEquals("minecraft::item&minecraft::fluid!minecraft:water!minecraft:lava", chained.canonical());
    }
    @Test void questionMarkMatchesExactlyOneCharacterAndStarMatchesAnyRun() {
        var single = FactorySelector.parse("minecraft:iron_?ngot");
        assertTrue(single.matches("minecraft::item", "minecraft:iron_ingot"));
        assertFalse(single.matches("minecraft::item", "minecraft:iron_ngot"));
        assertFalse(single.matches("minecraft::item", "minecraft:iron_iiingot"));

        assertFalse(FactorySelector.parse("minecraft:iron?").matches("minecraft::item", "minecraft:iron_ingot"));
        assertTrue(FactorySelector.parse("minecraft:iron?").matches("minecraft::item", "minecraft:ironx"));
        assertTrue(FactorySelector.parse("minecraft:iron_*").matches("minecraft::item", "minecraft:iron_ingot"));
        assertTrue(FactorySelector.parse("minecraft:*_ingot").matches("minecraft::item", "minecraft:iron_ingot"));
        assertFalse(FactorySelector.parse("minecraft:*_ingot").matches("minecraft::item", "minecraft:iron"));

        var mixed = FactorySelector.parse("minecraft:?ron_*&minecraft:gold_ingot");
        assertTrue(mixed.matches("minecraft::item", "minecraft:iron_block"));
        assertTrue(mixed.matches("minecraft::item", "minecraft:gold_ingot"));
        assertFalse(mixed.matches("minecraft::item", "minecraft:copper_ingot"));
    }
    @Test void itemTagOperandsResolveThroughTheGenerationLookup() {
        java.util.Set<String> smeltable = java.util.Set.of("minecraft:raw_iron", "minecraft:raw_gold", "minecraft:sand");
        FactorySelector.TagLookup lookup = (tag, keyType, id) ->
                keyType.equals("minecraft::item") && tag.equals("minecraft:smeltable") && smeltable.contains(id);

        var tag = FactorySelector.parse("#minecraft:smeltable");
        assertTrue(tag.matches("minecraft::item", "minecraft:raw_iron", index -> false, lookup));
        assertFalse(tag.matches("minecraft::item", "minecraft:iron_ingot", index -> false, lookup));
        assertFalse(tag.matches("minecraft::fluid", "minecraft:water", index -> false, lookup));
        assertEquals("#minecraft:smeltable", tag.canonical());
        // Without a registry backed lookup a tag operand matches nothing instead of guessing.
        assertFalse(tag.matches("minecraft::item", "minecraft:raw_iron"));

        var composed = FactorySelector.parse("#minecraft:smeltable&minecraft:diamond!(minecraft:raw_gold)");
        assertTrue(composed.matches("minecraft::item", "minecraft:raw_iron", index -> false, lookup));
        assertTrue(composed.matches("minecraft::item", "minecraft:diamond", index -> false, lookup));
        assertFalse(composed.matches("minecraft::item", "minecraft:raw_gold", index -> false, lookup));
        assertEquals("#minecraft:smeltable&minecraft:diamond!minecraft:raw_gold", composed.canonical());
    }
    @Test void tagOperandsCompileAndRejectMalformedNames() {
        var program = FactoryCompiler.compile("""
                import A
                get #minecraft:planks from A
                put #minecraft:planks into A
                """, false);
        assertTrue(program.instructions().stream().anyMatch(i -> i.op() == FactoryProgram.Op.GET
                && i.argument().startsWith("#minecraft:planks ")));
        for (String source : new String[]{"#smeltable", "#", "#minecraft:", "#:"})
            assertThrows(IllegalArgumentException.class, () -> FactorySelector.parse(source), source);
    }
}
