package com.example.ae2lightoptimizer.crafting;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RipperPatternPolicyTest {
    private record Output(String item, String components) {}
    private record Pattern(String recipe, Output output) {}

    @Test void manualDefinitionsExcludeEveryAutomaticAlternativeForThatOutput() {
        var plank = new Output("oak_planks", "");
        var stick = new Output("stick", "");
        var installed = new Pattern("oak_log", plank);
        var otherManual = new Pattern("stripped_log", plank);
        var automatic = List.of(new Pattern("oak_wood", plank), installed,
                new Pattern("oak_to_stick", stick), new Pattern("birch_to_stick", stick));
        assertEquals(List.of(installed, otherManual, automatic.get(2), automatic.get(3)),
                RipperPatternPolicy.select(List.of(installed, otherManual), automatic, Set.of(plank), Pattern::output));
    }

    @Test void anInvalidInstalledDefinitionStillBlocksSilentAutomaticFallback() {
        var target = new Output("product", "custom-data");
        assertTrue(RipperPatternPolicy.select(List.of(), List.of(new Pattern("replacement", target)),
                Set.of(target), Pattern::output).isEmpty());
    }

    @Test void componentVariantsRemainSeparateTargets() {
        var named = new Output("potion", "healing");
        var other = new Output("potion", "water");
        var explicit = new Pattern("manual", named);
        var automatic = new Pattern("water_recipe", other);
        assertEquals(List.of(explicit, automatic), RipperPatternPolicy.select(List.of(explicit),
                List.of(new Pattern("other_healing", named), automatic), Set.of(named), Pattern::output));
    }

    @Test void emptyCardOverlayKeepsAllAutomaticRecipesAndNoCardKeepsOnlyManualOnes() {
        var pattern = new Pattern("recipe", new Output("item", ""));
        assertEquals(List.of(pattern), RipperPatternPolicy.select(List.of(), List.of(pattern), Set.of(), Pattern::output));
        assertEquals(List.of(pattern), RipperPatternPolicy.select(List.of(pattern), List.of(), Set.of(pattern.output()), Pattern::output));
    }

    @Test void capacityKeepsOneTrailingRowAndCrossesTheOldLimitWithoutAPhantomCeiling() {
        assertEquals(9, RipperPatternLayout.slotsAfter(-1));
        assertEquals(18, RipperPatternLayout.slotsAfter(0));
        assertEquals(18, RipperPatternLayout.slotsAfter(8));
        assertEquals(27, RipperPatternLayout.slotsAfter(9));
        assertEquals(45, RipperPatternLayout.slotsAfter(35));
        assertEquals(54, RipperPatternLayout.slotsAfter(36));
        assertEquals(10017, RipperPatternLayout.slotsAfter(10000));
        assertEquals(0, RipperPatternLayout.clampRow(100, 2));
        assertEquals(996, RipperPatternLayout.clampRow(Integer.MAX_VALUE, 1000));
        assertEquals(90035, RipperPatternLayout.storageSlot(10000, 35));
        assertThrows(IllegalArgumentException.class, () -> RipperPatternLayout.storageSlot(1, 36));
    }
}
