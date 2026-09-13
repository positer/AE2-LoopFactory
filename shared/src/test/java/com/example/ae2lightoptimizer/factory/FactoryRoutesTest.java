package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryRoutesTest {
    @Test void mandatoryDeclarationSurvivesRestoreBeforeFirstOutput() {
        var routes=new FactoryRoutes();
        routes.declare("minecraft:iron_ingot","A","",64,true);
        routes.declare("minecraft:gold_ingot","A","",16,false);
        var restored=FactoryRoutes.restore(routes.snapshot());
        assertEquals(routes.snapshot(),restored.snapshot());
        assertTrue(restored.snapshot().getFirst().must());
        restored.output("minecraft:iron_ingot","B","",64,s->s.must(),(s,f,t,face,n)->24);
        var again=FactoryRoutes.restore(restored.snapshot());
        assertTrue(again.snapshot().getFirst().must());
        assertEquals(40,again.snapshot().getFirst().remaining());
        assertFalse(again.snapshot().get(1).must());
    }
    @Test void declarationDoesNotExtractAndOutputOnlyMovesAcceptedAmount() {
        var routes = new FactoryRoutes();
        long[] machineStock = {100};
        routes.declare("minecraft:iron_ingot", "A", "north", 64);
        assertEquals(100, machineStock[0]);
        long first = routes.output("minecraft::item", "B", "", Long.MAX_VALUE, (source, filter, tag, face, limit) -> {
            long amount = Math.min(10, limit);
            machineStock[0] -= amount;
            return amount;
        });
        assertEquals(10, first);
        assertEquals(90, machineStock[0]);
        assertEquals(54, routes.snapshot().getFirst().remaining());
        routes = FactoryRoutes.restore(routes.snapshot());
        long rest = routes.output("minecraft::item", "C", "", Long.MAX_VALUE, (source, filter, tag, face, limit) -> {
            machineStock[0] -= limit;
            return limit;
        });
        assertEquals(54, rest);
        assertEquals(36, machineStock[0]);
    }
    @Test void blockedOutputAndForgetLeaveStockInSource() {
        var routes = new FactoryRoutes();
        routes.declare("P1", "source", "", 64);
        assertEquals(0, routes.output("P1", "A", "", 64, (s, f, t, side, max) -> 0));
        assertEquals(64, routes.snapshot().getFirst().remaining());
        routes.forget("source");
        assertTrue(routes.snapshot().isEmpty());
    }
    @Test void repeatedDeclarationRefreshesInsteadOfDuplicatingSources() {
        var routes = new FactoryRoutes();
        routes.declare("*!(P1)", "A", "", 32);
        routes.declare("*!(P1)", "A", "", 64);
        assertEquals(1, routes.snapshot().size());
        assertEquals(64, routes.snapshot().getFirst().remaining());
    }
}
