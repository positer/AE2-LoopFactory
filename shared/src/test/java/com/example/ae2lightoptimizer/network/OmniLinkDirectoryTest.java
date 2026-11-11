package com.example.ae2lightoptimizer.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OmniLinkDirectoryTest {
    @Test void stableNamesSurviveRestoreAndCaseInsensitiveSelection() {
        var directory = new OmniLinkDirectory();
        var first = directory.createOrGet("  Factory A  ");
        assertEquals(first, directory.createOrGet("factory a"));
        directory.createOrGet("跨维度连接");
        var restored = new OmniLinkDirectory(directory.links());
        assertEquals(directory.links(), restored.links());
        assertEquals(first, restored.get(first.id()));
        assertNull(restored.get("missing"));
    }
    @Test void namesAreNormalizedAndBoundedWithoutInterpretingPunctuation() {
        var directory = new OmniLinkDirectory();
        assertEquals(directory.createOrGet("é"), directory.createOrGet("e\u0301"));
        assertEquals("工".repeat(48), directory.createOrGet("工".repeat(48)).name());
        for (String invalid : new String[]{"", "  ", "bad\nname", "bad\u2028name", "x".repeat(49)})
            assertThrows(IllegalArgumentException.class, () -> directory.createOrGet(invalid));
        assertEquals("A/B:{C}", directory.createOrGet("A/B:{C}").name());
    }
    @Test void whitePalettePreservesAlphaGrayAndTransparentPixels() {
        assertEquals(0xFF858585, OmniWhitePalette.neutralize(0xFF858585));
        assertEquals(0xFF909090, OmniWhitePalette.neutralize(0xFF858590));
        assertEquals(0x00123456, OmniWhitePalette.neutralize(0x00123456));
        assertEquals(0x80EFEFEF, OmniWhitePalette.neutralize(0x80EF35B2));
        assertEquals(0xFFAAAAAA, OmniWhitePalette.neutralize(0xFF20AA45));
    }
}
