package com.example.ae2lightoptimizer.factory;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A channel-aware logistics host must receive the owning channel when the VM inherits GET MUST. */
class FactoryChannelMustTest {
    private static final long TRILLION = 1_000_000_000_000L;

    private static class Host implements FactoryMachine.Host {
        FactoryRoutes routes = new FactoryRoutes();
        final Map<String, Long> stock = new HashMap<>();
        long moved;

        public long count(String tag, String resource) { return stock.getOrDefault(tag, 0L); }
        public void pulse(String tag, long ticks) {}
        public void declare(String resource, String tag, String face, long limit, boolean must) {
            declare(resource, tag, face, limit, must, 0);
        }
        public void declare(String resource, String tag, String face, long limit, boolean must, int channel) {
            routes.declare(resource, tag, face, limit, must, channel);
        }
        // The legacy API cannot select the active channel. A VM using it inherits unrelated debt.
        public long requiredTransfer(String resource, String tag, String face, long limit) {
            return Math.min(limit, routes.snapshot().stream().filter(FactoryRoutes.Source::must)
                    .mapToLong(FactoryRoutes.Source::remaining).sum());
        }
        @Override public long requiredTransfer(String resource, String tag, String face, long limit, int channel) {
            return Math.min(limit, routes.snapshot().stream().filter(source -> source.must() && source.channel() == channel)
                    .mapToLong(FactoryRoutes.Source::remaining).sum());
        }
        public long transfer(boolean get, String resource, String tag, String face, long limit) {
            return transfer(get, resource, tag, face, limit, 0);
        }
        public long transfer(boolean get, String resource, String tag, String face, long limit, int channel) {
            if (get) { declare(resource, tag, face, limit, false, channel); return 0; }
            return routes.output(resource, tag, face, limit, source -> source.channel() == channel,
                    (source, selector, destination, side, allowed) -> {
                        long sent = Math.min(allowed, stock.getOrDefault(source.tag(), 0L));
                        stock.compute(source.tag(), (key, available) -> (available == null ? 0 : available) - sent);
                        moved = Math.addExact(moved, sent);
                        return sent;
                    });
        }
    }

    /** Recreates the old production lookup so snapshots come from an actually interrupted PUT. */
    private static final class LegacyDebtHost extends Host {
        @Override public long requiredTransfer(String resource, String tag, String face, long limit, int channel) {
            return requiredTransfer(resource, tag, face, limit);
        }
    }

    private static Host restoredHost(Host original) {
        var host = new Host();
        host.routes = FactoryRoutes.restore(original.routes.snapshot());
        host.stock.putAll(original.stock);
        host.moved = original.moved;
        return host;
    }

    @Test void anotherChannelsTrillionItemMustDoesNotBlockOuterPut() {
        var program = FactoryCompiler.compile("""
                channel
                    get must 1000000000000 minecraft::item from storage
                put minecraft::item into source
                done
                """, false);
        var host = new Host();
        var machine = new FactoryMachine(program);
        machine.tick(host);
        assertTrue(machine.stopped(), "A channel-0 PUT with no source must advance despite channel-1 debt");
        assertEquals("", machine.error());
        assertFalse(machine.snapshot().pendingTransfer());
        assertEquals(0, host.moved);
        assertEquals(TRILLION, host.routes.snapshot().getFirst().remaining());
    }

    @Test void outerChannelDebtDoesNotInflateInnerChannelsRequirement() {
        var program = FactoryCompiler.compile("""
                import A,B
                get must 1000000000000 minecraft::item from A
                channel
                    get must 7 minecraft::item from B
                    put minecraft::item into source
                done
                """, false);
        var host = new Host();
        host.stock.put("B", 7L);
        var machine = new FactoryMachine(program);
        machine.tick(host);
        assertTrue(machine.stopped(), "The inner PUT owes seven items, not the unrelated outer trillion");
        assertEquals("", machine.error());
        assertEquals(7, host.moved);
        assertEquals(TRILLION, host.routes.snapshot().stream().filter(source -> source.channel() == 0).findFirst().orElseThrow().remaining());
        assertEquals(0, host.routes.snapshot().stream().filter(source -> source.channel() != 0).findFirst().orElseThrow().remaining());
    }

    @Test void owningChannelMustStillWaitsForItsExactRemainderAfterRestore() {
        var program = FactoryCompiler.compile("""
                channel
                    get must 1000000000000 minecraft::item from storage
                    put minecraft::item into source
                done
                """, false);
        var host = new Host();
        host.stock.put("storage", 123L);
        var machine = new FactoryMachine(program);
        machine.tick(host);
        assertFalse(machine.stopped());
        assertTrue(machine.snapshot().pendingTransfer());
        assertEquals(TRILLION - 123, machine.snapshot().transferRemaining());
        assertEquals(123, host.moved);
        machine = FactoryMachine.restore(program, machine.snapshot());
        host.routes = FactoryRoutes.restore(host.routes.snapshot());
        machine.tick(host);
        assertFalse(machine.stopped());
        assertEquals(123, host.moved, "Restoring a blocked channel must not replay its partial transfer");
        host.stock.put("storage", TRILLION - 123);
        machine.tick(host);
        assertTrue(machine.stopped());
        assertEquals("", machine.error());
        assertEquals(TRILLION, host.moved);
        assertEquals(0, host.routes.snapshot().getFirst().remaining());
    }

    @Test void legacyForeignDebtSnapshotFinishesWithoutInventingATransfer() {
        var program = FactoryCompiler.compile("""
                channel
                    get must 1000000000000 minecraft::item from storage
                put minecraft::item into source
                done
                """, false);
        var legacy = new LegacyDebtHost();
        var machine = new FactoryMachine(program);
        machine.tick(legacy);
        assertEquals(TRILLION, machine.snapshot().transferRemaining());
        assertFalse(machine.stopped());
        var restored = restoredHost(legacy);
        machine = FactoryMachine.restore(program, machine.snapshot());
        machine.tick(restored);
        assertTrue(machine.stopped(), "Old foreign-channel waiting debt has no obligation in this PUT");
        assertEquals("", machine.error());
        assertEquals(0, restored.moved);
        assertEquals(TRILLION, restored.routes.snapshot().getFirst().remaining());
    }

    @Test void legacyInflatedSnapshotRetainsOnlyUnpaidOwningChannelDebt() {
        var program = FactoryCompiler.compile("""
                import A,B
                get must 1000000000000 minecraft::item from A
                channel
                    get must 7 minecraft::item from B
                    put minecraft::item into source
                done
                """, false);
        var legacy = new LegacyDebtHost();
        legacy.stock.put("B", 2L);
        var machine = new FactoryMachine(program);
        machine.tick(legacy);
        assertEquals(TRILLION + 5, machine.snapshot().transferRemaining());
        assertEquals(2, legacy.moved);
        var restored = restoredHost(legacy);
        machine = FactoryMachine.restore(program, machine.snapshot());
        machine.tick(restored);
        assertFalse(machine.stopped(), "The legitimate five-item remainder must still wait");
        assertEquals(5, machine.snapshot().transferRemaining(), "Migration must discard only the foreign debt");
        assertEquals(2, restored.moved, "Migration cannot replay the two committed items");
        restored.stock.put("B", 5L);
        machine.tick(restored);
        assertTrue(machine.stopped());
        assertEquals("", machine.error());
        assertEquals(7, restored.moved);
        assertEquals(TRILLION, restored.routes.snapshot().stream().filter(source -> source.channel() == 0).findFirst().orElseThrow().remaining());
    }

    @Test void explicitPutMustSnapshotKeepsItsUserSpecifiedRemainder() {
        var program = FactoryCompiler.compile("""
                channel
                    get 7 minecraft::item from storage
                    put must 7 minecraft::item into source
                done
                """, false);
        var host = new Host();
        host.stock.put("storage", 2L);
        var machine = new FactoryMachine(program);
        machine.tick(host);
        assertEquals(5, machine.snapshot().transferRemaining());
        host = restoredHost(host);
        machine = FactoryMachine.restore(program, machine.snapshot());
        machine.tick(host);
        assertFalse(machine.stopped(), "An explicit PUT MUST does not depend on GET MUST declarations");
        assertEquals(5, machine.snapshot().transferRemaining());
        host.stock.put("storage", 5L);
        machine.tick(host);
        assertTrue(machine.stopped());
        assertEquals("", machine.error());
        assertEquals(7, host.moved);
    }
}
