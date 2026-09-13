package com.example.ae2lightoptimizer.factory;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FactorySlashCommentTest {
    private static void sameInstructions(String plain, String commented) {
        var expected = FactoryCompiler.compile(plain, false);
        var actual = FactoryCompiler.compile(commented, false);
        assertEquals(expected.instructions(), actual.instructions());
        assertEquals(expected.tags(), actual.tags());
        assertEquals(expected.functions(), actual.functions());
        assertEquals(expected.name(), actual.name());
        assertEquals(commented, actual.source(), "Keep original editable and persisted source");
    }

    @Test void indentationInlineCommentsDoNotChangeExecutionOrScope() {
        String plain = "import 输入,输出\nfunc 搬运\n    get 3 minecraft:stone from 输入\n    put 3 minecraft:stone into 输出\nend\nchannel\n    搬运\ndone";
        sameInstructions(plain, plain.replace("\n", " // ignored put O0 into storage\n") + "//end of file");
    }

    @Test void sfmInlineCommentsPreserveTokensTriggersAndLineNumbers() {
        String plain = "NAME \"Demo\"\nEVERY 2 GLOBAL PLUS 1 TICKS DO\nINPUT MUST 3 iron_ingot FROM A\nOUTPUT 3 iron_ingot TO B\nEND";
        String commented = plain.replace("\n", "// ignored END INPUT 999 dirt\n") + " // end";
        assertTrue(SfmSyntax.recognizes(commented));
        assertEquals(SfmSyntax.lex(plain), SfmSyntax.lex(commented));
        sameInstructions(plain, commented);
    }

    @Test void leadingCommentsDoNotPreventSfmDetection() {
        String source = "// header \" quote\n// EVERY fake\nNAME \"Demo\" // label\n// another header\nEVERY 20 TICKS DO // start\nEND // finish";
        assertTrue(SfmSyntax.recognizes(source));
        assertDoesNotThrow(() -> FactoryCompiler.compile(source, false));
    }

    @Test void quotedSlashPairsAndEscapedQuotesArePreserved() {
        String plain = "NAME \"https://example/a\"\nEVERY 20 TICKS DO\nINPUT stone FROM \"chest // left\"\nEND";
        sameInstructions(plain, plain.replace("\n", " // ignored\n"));
        String escaped = "NAME \"a \\\" // b\"\nEVERY 20 TICKS DO END";
        assertEquals("a \" // b", SfmSyntax.parse(escaped + " // ignored").name());
        sameInstructions("name \"https://example/a\"\ndone", "name \"https://example/a\" // note\ndone// note");
    }

    @Test void commentsDoNotCreateSymbolsOrHideNextLinesWithQuotes() {
        String source = "// func fake \"\n# \" legacy\nimport 输入,输出 // trailing \"\nfunc 搬运 // trailing \"\n    wait 1 tick // body\nend\n搬运\ndone";
        assertEquals(java.util.List.of("输入", "输出"), FactoryCompletion.tags(source));
        assertEquals(java.util.List.of("搬运"), FactoryCompletion.functions(source));
        assertDoesNotThrow(() -> FactoryCompiler.compile(source, false));
        assertDoesNotThrow(() -> FactoryCompiler.compile("-- quote \"\n// quote \"\nEVERY 20 TICKS DO END", false));
    }

    @Test void errorsAfterCommentsRetainOriginalLineNumbers() {
        var error = assertThrows(FactoryProgram.CompileException.class,
                () -> FactoryCompiler.compile("// ignored\r\n// more\r\ninvalid instruction", false));
        assertEquals(3, error.line());
    }

    @Test void adjacentSlashCommentsAndSingleResourceSlashesAreDistinct() {
        sameInstructions("get minecraft:folder/item from source\ndone", "get minecraft:folder/item from source//anything\ndone//");
        assertDoesNotThrow(() -> FactoryCompiler.compile("// only comment", false));
        assertThrows(FactoryProgram.CompileException.class,
                () -> FactoryCompiler.compile("//" + "x".repeat(65535), false));
    }

    @Test void commentHighlightingEndsAtTheLineAndPreservesQuotedSlashPairs() {
        for (String source : java.util.List.of("get #c:tag from source// P0 channel \"\ndone", "EVERY 20 TICKS DO // OUTPUT 999 stone\nEND")) {
            var spans = FactorySyntaxHighlighter.highlight(source);
            int comment = source.indexOf("//"), end = source.indexOf('\n', comment);
            assertTrue(FactorySyntaxHighlighter.slice(spans, comment, end).stream()
                    .allMatch(s -> s.kind() == FactorySyntaxHighlighter.Kind.COMMENT));
            assertEquals(FactorySyntaxHighlighter.Kind.KEYWORD,
                    FactorySyntaxHighlighter.slice(spans, end + 1, source.length()).getFirst().kind());
        }
        String quoted = "name \"https://example/a\" // note";
        assertEquals(FactorySyntaxHighlighter.Kind.STRING,
                FactorySyntaxHighlighter.slice(FactorySyntaxHighlighter.highlight(quoted), quoted.indexOf("//"), quoted.indexOf("//") + 2).getFirst().kind());
    }
}
