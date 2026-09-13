package com.example.ae2lightoptimizer.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FactoryCompletionTest {
    @Test
    void indentationAndNewlinePreserveTheCurrentLevel() {
        var newline = FactoryCompletion.newline("    get minecraft:item from A", 4, 4);
        assertEquals("    \n    get minecraft:item from A", newline.text());
        assertEquals(9, newline.cursor());

        var indented = FactoryCompletion.indent("if true do\nwait 1 tick", 0, 22, false);
        assertEquals("    if true do\n    wait 1 tick", indented.text());
        var restored = FactoryCompletion.indent(indented.text(), 0, indented.text().length(), true);
        assertEquals("if true do\nwait 1 tick", restored.text());
    }

    @Test
    void classifiesSyntaxForTheEditorOverlay() {
        var spans = FactorySyntaxHighlighter.highlightLine(
                "get 64 minecraft:item from A on up # note", Set.of("A"), Set.of("work"));
        assertTrue(spans.stream().anyMatch(span -> span.kind() == FactorySyntaxHighlighter.Kind.KEYWORD));
        assertTrue(spans.stream().anyMatch(span -> span.kind() == FactorySyntaxHighlighter.Kind.NUMBER));
        assertTrue(spans.stream().anyMatch(span -> span.kind() == FactorySyntaxHighlighter.Kind.RESOURCE));
        assertTrue(spans.stream().anyMatch(span -> span.kind() == FactorySyntaxHighlighter.Kind.TAG));
        assertTrue(spans.stream().anyMatch(span -> span.kind() == FactorySyntaxHighlighter.Kind.COMMENT));
    }
}
