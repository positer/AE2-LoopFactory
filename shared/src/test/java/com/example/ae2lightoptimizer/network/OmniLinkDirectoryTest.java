package com.example.ae2lightoptimizer.network;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OmniLinkDirectoryTest {
    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";
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
    @Test void legacyLinksLoadAsPublicAndStayVisibleToEveryone() {
        var legacy = new OmniLinkDirectory(List.of(new OmniLinkDirectory.Link(
                "33333333-3333-3333-3333-333333333333", "Legacy")));
        var link = legacy.get("33333333-3333-3333-3333-333333333333");
        assertEquals(OmniLinkDirectory.Visibility.PUBLIC, link.visibility());
        assertEquals("", link.owner());
        assertTrue(link.visibleTo(ALICE));
        assertTrue(link.visibleTo(BOB));
        assertEquals(1, legacy.visibleTo(BOB).size());
    }
    @Test void privateLinksAreVisibleOnlyToTheirOwner() {
        var directory = new OmniLinkDirectory();
        var link = directory.createOrGet("Mine", OmniLinkDirectory.Visibility.PRIVATE, ALICE);
        assertEquals(OmniLinkDirectory.Visibility.PRIVATE, link.visibility());
        assertEquals(ALICE, link.owner());
        assertTrue(link.visibleTo(ALICE));
        assertFalse(link.visibleTo(BOB));
        assertEquals(1, directory.visibleTo(ALICE).size());
        assertEquals(0, directory.visibleTo(BOB).size());
        assertTrue(directory.canSee(link.id(), ALICE));
        assertFalse(directory.canSee(link.id(), BOB));
        assertFalse(directory.canSee("missing", ALICE));
    }
    @Test void publicAndPrivateNamesAreSeparateNamespaces() {
        var directory = new OmniLinkDirectory();
        var shared = directory.createOrGet("Factory", OmniLinkDirectory.Visibility.PUBLIC, "");
        var alice = directory.createOrGet("Factory", OmniLinkDirectory.Visibility.PRIVATE, ALICE);
        var bob = directory.createOrGet("Factory", OmniLinkDirectory.Visibility.PRIVATE, BOB);
        assertNotEquals(shared.id(), alice.id());
        assertNotEquals(alice.id(), bob.id());
        assertEquals(shared, directory.createOrGet("factory", OmniLinkDirectory.Visibility.PUBLIC, ""));
        assertEquals(alice, directory.createOrGet("FACTORY", OmniLinkDirectory.Visibility.PRIVATE, ALICE));
        assertEquals(2, directory.visibleTo(ALICE).size());
        assertEquals(2, directory.visibleTo(BOB).size());
        assertThrows(IllegalArgumentException.class,
                () -> directory.createOrGet("Factory", OmniLinkDirectory.Visibility.PRIVATE, "not-a-uuid"));
    }
    @Test void privateVisibilitySurvivesSaveAndRestore() {
        var directory = new OmniLinkDirectory();
        var publicLink = directory.createOrGet("Shared");
        var privateLink = directory.createOrGet("Hidden", OmniLinkDirectory.Visibility.PRIVATE, ALICE);
        var restored = new OmniLinkDirectory(directory.links());
        assertEquals(publicLink, restored.get(publicLink.id()));
        assertEquals(privateLink, restored.get(privateLink.id()));
        assertEquals(List.of(publicLink), restored.visibleTo(BOB));
        assertEquals(2, restored.visibleTo(ALICE).size());
    }
    @Test void whitePalettePreservesAlphaGrayAndTransparentPixels() {
        assertEquals(0xFF858585, OmniWhitePalette.neutralize(0xFF858585));
        assertEquals(0xFF909090, OmniWhitePalette.neutralize(0xFF858590));
        assertEquals(0x00123456, OmniWhitePalette.neutralize(0x00123456));
        assertEquals(0x80EFEFEF, OmniWhitePalette.neutralize(0x80EF35B2));
        assertEquals(0xFFAAAAAA, OmniWhitePalette.neutralize(0xFF20AA45));
    }
}
