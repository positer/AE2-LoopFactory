package com.example.ae2lightoptimizer.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryChannelIsolationTest {
    private static final String ITEM = "minecraft:iron_ingot";
    private static final class Host implements FactoryMachine.Host {
        FactoryRoutes routes = new FactoryRoutes();
        final Map<String, Long> stock = new HashMap<>();
        @Override public long count(String tag, String resource) { return stock.getOrDefault(tag, 0L); }
        @Override public void pulse(String tag, long ticks) {}
        @Override public void declare(String r, String tag, String face, long limit, boolean must) {
            declare(r, tag, face, limit, must, 0);
        }
        @Override public void declare(String r, String tag, String face, long limit, boolean must, int channel) {
            routes.declare(r, tag, face, limit, must, channel);
        }
        @Override public long requiredTransfer(String r, String tag, String face, long limit, int channel) {
            return Math.min(limit, routes.snapshot().stream().filter(s -> s.channel() == channel && s.must())
                    .mapToLong(FactoryRoutes.Source::remaining).sum());
        }
        @Override public long transfer(boolean get, String r, String tag, String face, long limit) {
            return transfer(get, r, tag, face, limit, 0);
        }
        @Override public long transfer(boolean get, String r, String tag, String face, long limit, int channel) {
            if (get) { declare(r, tag, face, limit, false, channel); return 0; }
            return routes.output(r, tag, face, limit, s -> s.channel() == channel, (s, selector, dst, side, allowed) -> {
                long n = Math.min(allowed, stock.getOrDefault(s.tag(), 0L));
                stock.merge(s.tag(), -n, Long::sum);
                stock.merge(dst, n, Long::sum);
                return n;
            });
        }
    }

    @Test void channelIsAKeywordEvenWhenIndentingOrEditingConflictingNames() {
        var spans = FactorySyntaxHighlighter.highlightLine("    channel", Set.of("channel"), Set.of());
        assertEquals(new FactorySyntaxHighlighter.Span(4, 11, FactorySyntaxHighlighter.Kind.KEYWORD), spans.getLast());
        assertEquals(FactorySyntaxHighlighter.Kind.COMMENT,
                FactorySyntaxHighlighter.highlightLine("# channel", Set.of(), Set.of()).getFirst().kind());
        assertEquals(FactorySyntaxHighlighter.Kind.RESOURCE,
                FactorySyntaxHighlighter.highlightLine("test:channel", Set.of(), Set.of()).getFirst().kind());
    }

    @Test void channelCannotBeImportedOrDefinedAsAnUncallableFunctionName() {
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("import channel\ndone", false));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("func channel\n    wait 1 tick\nend", false));
    }

    @Test void functionPutUsesTheCallingChannelInsteadOfAnOuterDeclaration() {
        var p = FactoryCompiler.compile("""
                import A,B,Dst
                func send
                    put 3 minecraft:iron_ingot into Dst
                end
                get 3 minecraft:iron_ingot from A
                channel
                    get 3 minecraft:iron_ingot from B
                    send
                done
                """, false);
        var h = new Host(); h.stock.put("A", 7L); h.stock.put("B", 5L);
        var vm = new FactoryMachine(p); vm.tick(h);
        assertTrue(vm.stopped()); assertEquals("", vm.error());
        assertEquals(7L, h.stock.get("A")); assertEquals(2L, h.stock.get("B")); assertEquals(3L, h.stock.get("Dst"));
    }

    @Test void functionGetCannotLeakOutOfItsCallingChannel() {
        var p = FactoryCompiler.compile("""
                import A,Dst
                func load
                    get 3 minecraft:iron_ingot from A
                end
                channel
                    load
                put 3 minecraft:iron_ingot into Dst
                done
                """, false);
        var h = new Host(); h.stock.put("A", 7L);
        var vm = new FactoryMachine(p); vm.tick(h);
        assertTrue(vm.stopped()); assertEquals("", vm.error());
        assertEquals(7L, h.stock.get("A")); assertEquals(0L, h.stock.getOrDefault("Dst", 0L));
        assertEquals(1, h.routes.snapshot().getFirst().channel());
    }

    @Test void repeatedFunctionDeclarationsStayDistinctAcrossSiblingChannels() {
        var p = FactoryCompiler.compile("""
                import A
                func load
                    get 3 minecraft:iron_ingot from A
                end
                channel
                    load
                channel
                    load
                done
                """, false);
        var h = new Host(); var vm = new FactoryMachine(p); vm.tick(h);
        assertTrue(vm.stopped());
        assertEquals(Set.of(1, 2), h.routes.snapshot().stream().map(FactoryRoutes.Source::channel).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void nestedCallMustRetainsChannelAcrossSnapshotAndReturnsToOuterChannel() {
        var p = FactoryCompiler.compile("""
                import A,B,Dst
                func inner
                    put minecraft:iron_ingot into Dst
                end
                func outer
                    inner
                end
                get must 1000000000000 minecraft:iron_ingot from A
                channel
                    get must 5 minecraft:iron_ingot from B
                    outer
                put must 1 minecraft:iron_ingot into Dst
                done
                """, false);
        var h = new Host(); h.stock.put("A", 10L); h.stock.put("B", 3L);
        var vm = new FactoryMachine(p); vm.tick(h);
        assertFalse(vm.stopped()); assertEquals("", vm.error());
        assertEquals(2, vm.snapshot().transferRemaining()); assertEquals(2, vm.snapshot().returns().size());
        assertEquals(10L, h.stock.get("A")); assertEquals(3L, h.stock.get("Dst"));
        h.routes = FactoryRoutes.restore(h.routes.snapshot());
        vm = FactoryMachine.restore(p, vm.snapshot());
        vm.tick(h); assertEquals(3L, h.stock.get("Dst")); assertEquals(2, vm.snapshot().transferRemaining());
        h.stock.put("B", 2L); vm.tick(h);
        assertTrue(vm.stopped()); assertEquals("", vm.error());
        assertEquals(9L, h.stock.get("A")); assertEquals(0L, h.stock.get("B")); assertEquals(6L, h.stock.get("Dst"));
    }
}
