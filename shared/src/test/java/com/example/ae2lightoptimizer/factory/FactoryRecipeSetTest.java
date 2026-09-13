package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code P} is the complete expected input set of the bound recipe and {@code O} its complete expected
 * output set. Both must compose with the ordinary operands, exclusions and parentheses.
 */
public class FactoryRecipeSetTest {
    private static final Set<String> INPUTS = Set.of("minecraft:iron_ingot", "minecraft:gold_ingot");
    private static final Set<String> OUTPUTS = Set.of("minecraft:iron_block");

    /** Single operands resolve through their own material index, exactly like the runtime binding. */
    private static boolean matches(String selector, String id, Set<String> inputs, Set<String> outputs) {
        var byIndex = java.util.Map.of(1, "minecraft:iron_ingot", 2, "minecraft:gold_ingot");
        return FactorySelector.parse(selector).matches("minecraft::item", id,
                index -> id.equals(byIndex.get(index)),
                FactorySelector.TagLookup.NONE,
                (kind, type, resource) -> (kind == FactorySelector.ALL_INPUTS ? inputs : outputs).contains(resource));
    }

    @Test
    void parsesTheCompleteRecipeSets() {
        var both = FactorySelector.parse("P&O");
        assertTrue(both.usesRecipeSets());
        assertTrue(both.parameters().isEmpty());
        assertEquals("P&O", both.canonical());
        assertFalse(FactorySelector.parse("P1&O2").usesRecipeSets());
        assertEquals(Set.of(1, -2), FactorySelector.parse("P1&O2").parameters());
    }

    @Test
    void completeSetsRequireARecipeBoundPattern() {
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("get P from source", false));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("put O into storage", false));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("import A\nif A has P > 0 do\n    get O from A\ndone", false));
        assertDoesNotThrow(() -> FactoryCompiler.compile(
                "import A\nfunc feed\n    get P from A\n    put O into A\nend\nfeed\ndone", true, 2, 2));
        assertDoesNotThrow(() -> FactoryCompiler.compile(
                "import A\nif A has P > 0 do\n    get must 2 P!(P1) from A\n    put O!(O1) into source\ndone", true, 2, 2));
    }

    @Test
    void completeSetsNeedAReachableInputAndOutput() {
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("get P from source", true, 0, 1));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("get O from source", true, 1, 0));
    }

    @Test
    void completeSetsMatchEveryMemberOfTheirOwnSide() {
        assertTrue(matches("P", "minecraft:iron_ingot", INPUTS, OUTPUTS));
        assertTrue(matches("P", "minecraft:gold_ingot", INPUTS, OUTPUTS));
        assertFalse(matches("P", "minecraft:iron_block", INPUTS, OUTPUTS));
        assertTrue(matches("O", "minecraft:iron_block", INPUTS, OUTPUTS));
        assertFalse(matches("O", "minecraft:iron_ingot", INPUTS, OUTPUTS));
    }

    @Test
    void completeSetsComposeWithOperandsExclusionsAndParentheses() {
        // & merges operands, so P&<literal> is the union of the input set and that literal.
        assertTrue(matches("P&minecraft:gold_ingot", "minecraft:gold_ingot", INPUTS, OUTPUTS));
        assertTrue(matches("P&minecraft:iron_block", "minecraft:iron_block", INPUTS, OUTPUTS));
        assertTrue(matches("P!minecraft:gold_ingot", "minecraft:iron_ingot", INPUTS, OUTPUTS));
        assertFalse(matches("P!minecraft:gold_ingot", "minecraft:gold_ingot", INPUTS, OUTPUTS));
        assertFalse(matches("P!P1", "minecraft:iron_ingot", INPUTS, OUTPUTS));
        assertTrue(matches("P!P1", "minecraft:gold_ingot", INPUTS, OUTPUTS));
        assertTrue(matches("O&minecraft:iron_block", "minecraft:iron_block", INPUTS, OUTPUTS));
        assertFalse(matches("(O&minecraft:iron_block)!(minecraft:iron_block)", "minecraft:iron_block", INPUTS, OUTPUTS));
        assertTrue(matches("P!(P2)", "minecraft:iron_ingot", INPUTS, OUTPUTS));
        assertFalse(matches("P!(P2)", "minecraft:gold_ingot", INPUTS, OUTPUTS));
    }

    @Test
    void completeSetsFailLoudlyWithoutARecipeResolver() {
        var selector = FactorySelector.parse("P");
        var failure = assertThrows(IllegalArgumentException.class,
                () -> selector.matches("minecraft::item", "minecraft:iron_ingot", index -> true, FactorySelector.TagLookup.NONE));
        assertTrue(failure.getMessage().contains("requires a recipe-bound pattern"));
    }
}
