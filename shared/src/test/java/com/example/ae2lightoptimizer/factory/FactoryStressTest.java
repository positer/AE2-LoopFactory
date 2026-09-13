package com.example.ae2lightoptimizer.factory;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryStressTest {
    @Test void persistedNamesStayWithinTransportAndTagContracts() {
        String valid="A".repeat(128);
        var tags=new FactoryTags();tags.reconcile(FactoryCompiler.compile("import "+valid+"\ndone",false).tags());
        assertEquals(tags.snapshot(),FactoryTags.restore(tags.snapshot()).snapshot());
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("import "+valid+"A\ndone",false));
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("NAME \""+valid+"A\" EVERY TICK DO FORGET END",false));
    }
    @Test void unicodeChunksNeverSplitSurrogatePairs() {
        String source="#"+"中😀".repeat(21843)+"x\ndone";
        assertEquals(65536,source.length());
        for(int limit:new int[]{4096,8192}) {
            var chunks=FactoryCodeChunks.split(source,limit);
            assertEquals(source,String.join("",chunks));
            for(var chunk:chunks)assertEquals(chunk,new String(chunk.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test void maximumWildcardSelectorCannotOverflowJavaStack() {
        var selector = FactorySelector.parse("*".repeat(4096));
        assertTrue(selector.matches("minecraft::item", "minecraft:iron_ingot"));
        var mismatch = FactorySelector.parse("*a".repeat(1000) + "b");
        assertFalse(mismatch.matches("minecraft::item", "a".repeat(2000)));
    }

    @Test void longRunningYieldingFactoryRestoresWithoutReplay() {
        var program = FactoryCompiler.compile("while true do\n    wait 1 tick", false);
        var machine = new FactoryMachine(program);
        var host = new FactoryMachine.Host() {
            public long count(String tag, String resource) { throw new AssertionError(); }
            public long transfer(boolean get,String resource,String tag,String face,long limit) { throw new AssertionError(); }
            public void pulse(String tag,long ticks) { throw new AssertionError(); }
        };
        for (int i=0;i<100_000;i++) {
            machine.tick(host);
            machine = FactoryMachine.restore(program,machine.snapshot());
            assertFalse(machine.stopped());
            assertEquals(1,machine.snapshot().delay());
            assertEquals(List.of(),machine.snapshot().returns());
        }
    }

    @Test void reimportCannotAccumulateSourceBudgets() {
        var routes=new FactoryRoutes();
        for(int i=0;i<100_000;i++) {
            routes.declare("minecraft:iron_ingot","A","",64);
            assertEquals(64,routes.output("*","B","",Long.MAX_VALUE,(s,r,t,f,n)->n));
            assertEquals(0,routes.output("*","B","",Long.MAX_VALUE,(s,r,t,f,n)->n));
        }
        assertEquals(1,routes.snapshot().size());
    }

    @Test void oversizedAndDeepSourceFailsWithUserDiagnostic() {
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(" ".repeat(65537),false));
        var source=new StringBuilder();
        for(int i=0;i<100;i++)source.append(" ".repeat(i)).append("if true do\n");
        source.append(" ".repeat(100)).append("done");
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(source.toString(),false));
    }
}
