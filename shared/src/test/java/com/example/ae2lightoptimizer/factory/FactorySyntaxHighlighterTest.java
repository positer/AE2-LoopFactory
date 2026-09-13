package com.example.ae2lightoptimizer.factory;

import static com.example.ae2lightoptimizer.factory.FactorySyntaxHighlighter.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FactorySyntaxHighlighterTest {
    private static void token(String source, String token, FactorySyntaxHighlighter.Kind expected) {
        int offset = source.indexOf(token);
        assertTrue(offset >= 0, token);
        var spans = FactorySyntaxHighlighter.slice(FactorySyntaxHighlighter.highlight(source), offset, offset + token.length());
        assertFalse(spans.isEmpty(), token);
        assertTrue(spans.stream().allMatch(s -> s.kind() == expected), token + " = " + spans);
    }

    @Test void allCompilerKeywordsHaveColors() {
        for (String keyword : FactoryCompiler.RESERVED) token(keyword, keyword, KEYWORD);
    }

    @Test void unicodeDeclarationsAndCallsMatchCompilerSymbols() {
        String source = "import 输入, 输出\nfunc 搬运\n    get minecraft:stone from 输入\n    put minecraft:stone into 输出\nend\n搬运\ndone";
        assertDoesNotThrow(() -> FactoryCompiler.compile(source, false));
        assertEquals(List.of("输入", "输出"), FactoryCompletion.tags(source));
        assertEquals(List.of("搬运"), FactoryCompletion.functions(source));
        token(source, "输入", TAG);
        token(source, "搬运", FUNCTION);
        var spans = FactorySyntaxHighlighter.highlight(source);
        int call = source.lastIndexOf("搬运");
        assertEquals(FUNCTION, FactorySyntaxHighlighter.slice(spans, call, call + 2).getFirst().kind());
    }

    @Test void conditionOperatorsAndFacesHaveContext() {
        String source = "if not true and false or P0 in O do\nget minecraft:stone from source on north";
        for (String word : List.of("not", "and", "or")) token(source, word, OPERATOR);
        token(source, "in", KEYWORD);
    }

    @Test void membershipFacesAndAllRecipeReferences() {
        token("if P in O do", "in", KEYWORD);
        for (String face : List.of("up", "down", "north", "south", "east", "west"))
            token("get minecraft:stone from source on " + face, face, KEYWORD);
        for (String ref : List.of("P", "O", "P0", "O0", "P123", "O999")) token(ref, ref, REFERENCE);
        token("Pine", "Pine", PLAIN);
    }

    @Test void resourceTagsWildcardsAndNumbersAreNotSplitOrCommented() {
        String source = "get #c:ingots/iron & !minecraft:*ore? from source\nput 123mod:thing into storage\nwait -20 tick";
        for (String resource : List.of("#c:ingots/iron", "minecraft:*ore?", "123mod:thing")) token(source, resource, RESOURCE);
        token(source, "&", OPERATOR);
        token(source, "!", OPERATOR);
        token(source, "-20", NUMBER);
        token("get * from source", "*", RESOURCE);
    }

    @Test void quotedAndIncompleteStringsProtectTheirContents() {
        String source = "name \"channel \\\" #c:tag 64 P0 😀\"\ndone";
        token(source, "channel", STRING);
        token(source, "#c:tag", STRING);
        token(source, "done", KEYWORD);
        token("name \"unfinished channel\nget stone", "get", STRING);
        token("# channel P0 minecraft:stone", "channel", COMMENT);
        token("  #c:tag comment\ndone", "#c:tag", COMMENT);
    }

    @Test void sfmTriggersClausesAndCaseFolding() {
        String source = "NaMe \"Demo\"\nEvErY 20G PLUS 2 TICKS DO\nINPUT MUST 64 iron_ingot EXCEPT gold_ingot FROM Src TOP SIDE\nOUTPUT fe:: TO Dst BOTTOM SIDE\nFORGET Src\nEND";
        for (String word : List.of("NaMe", "EvErY", "G", "PLUS", "TICKS", "DO", "INPUT", "MUST", "EXCEPT", "FROM", "TOP", "SIDE", "OUTPUT", "TO", "BOTTOM", "FORGET", "END")) token(source, word, KEYWORD);
        for (String word : List.of("iron_ingot", "gold_ingot", "fe::")) token(source, word, RESOURCE);
        for (String word : List.of("Src", "Dst")) token(source, word, TAG);
        token(source, "20", NUMBER);
    }

    @Test void sfmConditionsLabelsAndResourceContextAcrossNewlines() {
        String source = "EVERY REDSTONE PULSE DO\nIF OVERALL Src HAS GE 64 iron_ingot AND NOT Dst HAS LT 2 gold_ingot THEN\nFROM\nSrc INPUT\n#c:ingots/iron\nELSE IF FALSE OR TRUE THEN\nOUTPUT * TO Dst\nEND\nEND";
        for (String word : List.of("REDSTONE", "PULSE", "OVERALL", "HAS", "THEN", "ELSE", "FALSE", "TRUE")) token(source, word, KEYWORD);
        for (String word : List.of("GE", "AND", "NOT", "LT", "OR")) token(source, word, OPERATOR);
        token(source, "Src", TAG);
        token(source, "Dst", TAG);
        token(source, "iron_ingot", RESOURCE);
        token(source, "gold_ingot", RESOURCE);
        token(source, "#c:ingots/iron", RESOURCE);
    }

    @Test void sfmCommentsAndQuotedLabelsProtectKeywordLookalikes() {
        String source = "-- INPUT channel\nEVERY 20 TICKS DO\nINPUT stone FROM \"IF HAS\" -- P0 fe::\nEND";
        token(source, "INPUT channel", COMMENT);
        token(source, "IF HAS", STRING);
        token(source, "P0", COMMENT);
        token(source, "stone", RESOURCE);
    }

    @Test void wrappedFragmentsRetainOriginalTokenKinds() {
        for (var sample : List.of("name \"" + "channel ".repeat(90) + "\"", "# " + "get ".repeat(90), "get minecraft:" + "long_id_".repeat(90) + " from source")) {
            var full = FactorySyntaxHighlighter.highlight(sample);
            for (int start = 0; start < sample.length(); start += 37) {
                int end = Math.min(start + 37, sample.length());
                var line = FactorySyntaxHighlighter.slice(full, start, end);
                int cursor = 0;
                for (var span : line) {
                    assertEquals(cursor, span.start());
                    int absolute = start + span.start();
                    assertEquals(full.stream().filter(s -> s.start() <= absolute && absolute < s.end()).findFirst().orElseThrow().kind(), span.kind());
                    cursor = span.end();
                }
                assertEquals(end - start, cursor);
            }
        }
    }

    @Test void maximumSourceHasContinuousUtf16CoverageAndCanBeRecolored() {
        String seed = "import 输入\n# 😀 comment\nget #c:tag from 输入\n";
        String source = seed.repeat(65536 / seed.length()) + " ".repeat(65536 % seed.length());
        int cursor = 0;
        for (var span : FactorySyntaxHighlighter.highlight(source)) {
            assertEquals(cursor, span.start());
            assertTrue(span.end() > span.start());
            cursor = span.end();
        }
        assertEquals(65536, cursor);
        token("# channel", "channel", COMMENT);
        token("channel", "channel", KEYWORD);
    }
}
