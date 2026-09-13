package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SfmSyntaxTest {
    @Test void multipleTimersPulseAndCaseInsensitiveKeywords() {
        var program = SfmSyntax.parse("""
                NAME "Factory -- not a comment"
                EVERY 2 seconds DO
                    INPUT 64 minecraft:iron_ingot EXCEPT minecraft:stone FROM "input chest"
                    OUTPUT TO machine
                END
                every redstone pulse do
                    from source input P1
                    to storage output *!(P2)
                end
                """);
        assertEquals("Factory -- not a comment", program.name());
        assertEquals(2, program.triggers().size());
        assertEquals(40, program.triggers().getFirst().ticks());
        assertTrue(program.triggers().get(1).redstone());
        assertEquals(2, program.triggers().getFirst().body().size());
        var route = (SfmSyntax.Route) program.triggers().getFirst().body().getFirst();
        assertEquals("input chest", route.destinations().getFirst().text());
    }
    @Test void preservesClausesForSemanticValidation() {
        var program = SfmSyntax.parse("""
                every 20g plus 3 ticks do
                    if overall inbox has ge 32 minecraft:iron_ingot then
                        input retain 4 each minecraft:iron_ingot from each inbox top side slots 0-3
                        output 8 to each machine round robin by block
                    else if redstone > 0 then
                        forget inbox
                    else
                        forget
                    end
                end
                """);
        var trigger = program.triggers().getFirst();
        assertTrue(trigger.global()); assertEquals(3, trigger.offset());
        var condition = (SfmSyntax.Conditional) trigger.body().getFirst();
        assertEquals(2, condition.branches().size());
        assertEquals(1, condition.otherwise().size());
        var route = (SfmSyntax.Route) condition.branches().getFirst().body().getFirst();
        assertTrue(route.resources().stream().anyMatch(token -> token.text().equals("retain")));
        assertTrue(route.destinations().stream().anyMatch(token -> token.text().equals("slots")));
    }
    @Test void malformedSourceReportsOriginalLine() {
        var error = assertThrows(FactoryProgram.CompileException.class, () -> SfmSyntax.parse("-- header\nevery 0 ticks do\nend"));
        assertEquals(2, error.line());
        assertThrows(FactoryProgram.CompileException.class, () -> SfmSyntax.parse("every 2 ticks do input from end"));
        assertThrows(FactoryProgram.CompileException.class, () -> SfmSyntax.parse("every 2 ticks do mystery end"));
        assertThrows(FactoryProgram.CompileException.class, () -> SfmSyntax.parse("name \"unfinished"));
    }
}
