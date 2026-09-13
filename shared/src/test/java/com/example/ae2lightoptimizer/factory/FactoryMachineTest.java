package com.example.ae2lightoptimizer.factory;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryMachineTest {
    private static class Host implements FactoryMachine.Host {
        final Map<String, Long> counts = new HashMap<>();
        final FactoryRoutes routes = new FactoryRoutes();
        long output;
        long pulse;
        @Override public long count(String tag, String resource) { return counts.getOrDefault(tag, 0L); }
        @Override public long transfer(boolean get, String resource, String tag, String face, long limit) {
            return transfer(get, resource, tag, face, limit, 0);
        }
        @Override public long transfer(boolean get, String resource, String tag, String face, long limit, int channel) {
            if (get) {
                routes.declare(resource, tag, face, limit, false, channel);
                return 0;
            }
            return routes.output(resource, tag, face, limit, source -> source.channel() == channel,
                    (source, selector, target, side, allowed) -> {
                        long amount = Math.min(allowed, counts.getOrDefault(source.tag(), 0L));
                        counts.put(source.tag(), counts.getOrDefault(source.tag(), 0L) - amount);
                        output = Math.addExact(output, amount);
                        return amount;
                    });
        }
        @Override public void pulse(String tag, long ticks) { pulse = ticks; }
        @Override public void declare(String resource, String tag, String face, long limit, boolean must, int channel) {
            routes.declare(resource, tag, face, limit, must, channel);
        }
    }

    @Test void restartDuringWaitPreservesCallStackAndDoesNotRepeatExtraction() {
        String source = """
                import A
                name "Test factory"
                func send
                    get 64 minecraft:iron_ingot from A on north
                    wait 2 tick
                    put minecraft:iron_ingot into source
                end
                send
                done
                """;
        var program = FactoryCompiler.compile(source, true);
        var host = new Host(); host.counts.put("A", 128L);
        var machine = new FactoryMachine(program);
        machine.tick(host);
        assertEquals(128, host.counts.get("A"));
        assertEquals(0, host.output);
        machine = FactoryMachine.restore(program, machine.snapshot());
        machine.tick(host);
        assertEquals(0, host.output);
        machine.tick(host);
        assertEquals(64, host.output);
        assertEquals(64, host.counts.get("A"));
        assertTrue(machine.stopped());
        assertEquals("", machine.error());
    }

    @Test void finiteWhileAndElseRespectIndentation() {
        var host = new Host(); host.counts.put("A", 3L);
        var machine = new FactoryMachine(FactoryCompiler.compile("""
                import A
                while A has minecraft:stone > 0 do
                    get 1 minecraft:stone from A
                    if A has minecraft:stone > 1 do
                        put minecraft:stone into source
                    else put minecraft:stone into storage
                done
                """, true));
        machine.tick(host);
        assertEquals(3, host.output);
        assertEquals("", machine.error());
    }

    @Test void continuousLoopMustYieldAndRecipeCannotBeUnconditional() {
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("while true do\n    wait 1 tick", true));
        var program = FactoryCompiler.compile("while true do\n    wait 1 tick", false);
        var machine = new FactoryMachine(program);
        var host = new Host();
        for (int i = 0; i < 20; i++) machine.tick(host);
        assertFalse(machine.stopped());
        var runaway = new FactoryMachine(FactoryCompiler.compile("if true do\n    while true do\n        if false do\n            wait 1 tick", false));
        runaway.tick(host);
        assertTrue(runaway.stopped());
        assertTrue(runaway.error().contains("no progress"));
    }

    @Test void exactCountsBeyondDoublePrecision() {
        assertEquals(1, FactoryExpression.evaluate("A has neoforge::fe > 9007199254740992", (a, b) -> 9007199254740993L));
        assertEquals(0, FactoryExpression.evaluate("9223372036854775807 < 9223372036854775806", (a, b) -> 0));
    }

    @Test void hasReadsOneTagOrEveryMachineTag() {
        // "has resource in Tag" reads exactly that tag.
        assertEquals(1, FactoryExpression.evaluate("has minecraft:iron_ingot in SrcA = 4",
                (tag, resource) -> tag.equals("SrcA") && resource.equals("minecraft:iron_ingot") ? 4 : 99));
        // A bare "has resource" asks the host for every machine tag.
        assertEquals(1, FactoryExpression.evaluate("has minecraft:iron_ingot = 10",
                (tag, resource) -> tag.equals(FactoryExpression.ALL_TAGS)
                        && resource.equals("minecraft:iron_ingot") ? 10 : 99));
        // The original tag-first spelling keeps working, and in-tag accepts a full selector.
        assertEquals(1, FactoryExpression.evaluate("SrcB has minecraft:?ron_ingot = 6",
                (tag, resource) -> tag.equals("SrcB") ? 6 : 99));
        assertEquals(1, FactoryExpression.evaluate("has minecraft::item&minecraft::fluid in SrcC = 3",
                (tag, resource) -> tag.equals("SrcC")
                        && resource.equals("minecraft::item&minecraft::fluid") ? 3 : 99));
        // Boolean composition still applies to the new forms.
        assertEquals(1, FactoryExpression.evaluate("has minecraft:iron_ingot > 0 and not has minecraft:gold_ingot > 0",
                (tag, resource) -> resource.equals("minecraft:iron_ingot") ? 5 : 0));
        // A missing resource or a dangling "in" leaves an invalid selector, which the compiler
        // rejects by line and reason instead of silently counting nothing.
        for (String condition : new String[]{"has in SrcA", "has minecraft:iron_ingot in"})
            assertThrows(FactoryProgram.CompileException.class,
                    () -> FactoryCompiler.compile("if " + condition + " do\n    done", false), condition);
    }

    @Test void bareHasCompilesWithTheAllTagsSentinel() {
        var program = FactoryCompiler.compile("""
                import SrcA,SrcB
                if has minecraft:iron_ingot = 10 do
                    get minecraft:iron_ingot from SrcA
                    put minecraft:iron_ingot into SrcB
                done
                while has minecraft:iron_ingot in SrcB > 0 do
                    wait 1 tick
                done
                done
                """, false);
        assertFalse(program.instructions().isEmpty());
        assertThrows(FactoryProgram.CompileException.class,
                () -> FactoryCompiler.compile("if has minecraft:stone in Missing do\n    done", false));
    }

    @Test void chineseTagAndFunctionNamesAreAccepted() {
        var host = new Host();
        host.counts.put("存储", 8L);
        var machine = new FactoryMachine(FactoryCompiler.compile("""
                import 存储,目标
                func 搬运
                    get 2 minecraft::item from 存储
                    put 2 minecraft::item into 目标
                end
                channel
                    if has minecraft:iron_ingot in 存储 > 0 do
                        get 3 minecraft::item from 存储
                        put 3 minecraft::item into 目标
                name "中文名称"
                搬运
                done
                """, false, 0, 0));
        for (int tick = 0; tick < 8 && !machine.stopped(); tick++) machine.tick(host);
        assertTrue(machine.error().isEmpty(), machine.error());
        // The function moves 2, the channel moves 3; every name involved is Chinese.
        assertEquals(5, host.output, "chinese names address the same logistics");
        assertThrows(FactoryProgram.CompileException.class,
                () -> FactoryCompiler.compile("import 存储\nfunc 1非法\n    done\nend", false),
                "a name starting with a digit is still rejected");
    }

    @Test void channelBlocksIsolateTheirDeclarations() {
        var host = new Host();
        host.counts.put("A", 10L);
        var machine = new FactoryMachine(FactoryCompiler.compile("""
                import A,B
                channel
                    get 4 minecraft::item from A
                put 4 minecraft::item into B
                channel
                    get minecraft::item from A
                    put 4 minecraft::item into B
                done
                """, false, 0, 0));
        for (int tick = 0; tick < 4 && !machine.stopped(); tick++) machine.tick(host);
        assertTrue(machine.error().isEmpty(), machine.error());
        // The channel-0 put cannot consume the first channel's declaration, so only the second
        // channel moves anything: one shared source never leaks across channel boundaries.
        assertEquals(4, host.output, "channels must not share declarations");
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("""
                channel
                    channel
                        wait 1 tick
                    wait 1 tick
                """, false), "channel cannot nest");
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("}\n", false),
                "a stray closing brace is still rejected");
    }

    @Test void omittedQuantityMeansEverythingAvailable() {
        var nestedHost = new Host();
        nestedHost.counts.put("A", 10L);
        // channel -> if -> while: the innermost statements stay in the channel they were written in.
        var nested = new FactoryMachine(FactoryCompiler.compile("""
                import A,B
                channel
                    if has minecraft:iron_ingot in A > 0 do
                        get 2 minecraft::item from A
                        put 2 minecraft::item into B
                        while has minecraft:iron_ingot in B > 0 do
                            put 1 minecraft::item into B
                            break
                done
                """, false, 0, 0));
        for (int tick = 0; tick < 6 && !nested.stopped(); tick++) nested.tick(nestedHost);
        assertTrue(nested.error().isEmpty(), nested.error());
        assertEquals(2, nestedHost.output, "nested if/while inside a channel keeps its group");

        var loopHost = new Host();
        loopHost.counts.put("A", 10L);
        // while -> channel: a channel declared inside a loop body owns only that block.
        var inLoop = new FactoryMachine(FactoryCompiler.compile("""
                import A,B
                while has minecraft:iron_ingot in A > 0 do
                    channel
                        get 1 minecraft::item from A
                        put 1 minecraft::item into B
                    break
                done
                """, false, 0, 0));
        for (int tick = 0; tick < 6 && !inLoop.stopped(); tick++) inLoop.tick(loopHost);
        assertTrue(inLoop.error().isEmpty(), inLoop.error());
        assertEquals(1, loopHost.output, "a channel inside a loop body keeps its own declaration");

        // channel -> if -> channel is still nesting and must be rejected.
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("""
                channel
                    if true do
                        channel
                            wait 1 tick
                """, false), "nesting through if is rejected");
    }

    @Test void omittedQuantityStillMeansEverythingAvailable() {
        var program = FactoryCompiler.compile("""
                import A
                get minecraft:iron_ingot from A
                put minecraft:iron_ingot into A
                done
                """, false, 0, 0);
        for (var instruction : program.instructions())
            if (instruction.op() == FactoryProgram.Op.GET || instruction.op() == FactoryProgram.Op.PUT)
                assertEquals(Long.MAX_VALUE, instruction.amount(),
                        "an unwritten quantity removes the limit");
        var explicit = FactoryCompiler.compile("import A\nget 8 minecraft:iron_ingot from A\ndone", false, 0, 0);
        assertEquals(8, explicit.instructions().stream()
                .filter(instruction -> instruction.op() == FactoryProgram.Op.GET)
                .findFirst().orElseThrow().amount(), "an explicit quantity still wins");
    }

    @Test void machineTagExpressionsAggregateWithAmpersand() {
        var program = FactoryCompiler.compile("""
                import A,B
                get minecraft:iron_ingot from A&B
                put minecraft:iron_ingot into A&B
                redstone A&B 1 tick
                while has minecraft:iron_ingot in A&B > 0 do
                    get 1 minecraft:iron_ingot from A&B
                    put 1 minecraft:iron_ingot into B&source
                done
                done
                """, false, 0, 0);
        assertTrue(program.instructions().stream().anyMatch(i -> i.op() == FactoryProgram.Op.GET
                && i.argument().contains(" A&B ")));
        assertTrue(program.instructions().stream().anyMatch(i -> i.op() == FactoryProgram.Op.REDSTONE
                && i.argument().equals("A&B")));
        // A compound tag mixes machine groups with the network storage or provider source.
        assertDoesNotThrow(() -> FactoryCompiler.compile("import A\nput minecraft:iron_ingot into A&storage", false, 0, 0));
        for (String source : new String[]{"import A\nget minecraft:iron_ingot from A&Missing",
                "import A\nget minecraft:iron_ingot from A&",
                "import A\nredstone A& 1 tick",
                "import A\nif has minecraft:iron_ingot in A&Missing do\n    done"}) {
            assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile(source, false, 0, 0), source);
        }
    }

    @Test void rejectInvalidSourceBeforeAnyWorldAccess() {
        for (String source : new String[]{"func storage\n    done\nend", "else done", "wait 0 tick",
                "wait 9223372036854775807 min", "get minecraft:stone from missing", "func foo\n    done",
                "if true do\n\tdone", "missingFunction", "if A has minecraft:stone do\n    done"}) {
            assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile(source, false), source);
        }
    }

    @Test void breakLeavesItsLoopAndARootBreakStopsTheProgram() {
        var host = new Host();
        host.counts.put("A", 1L);
        var machine = new FactoryMachine(FactoryCompiler.compile("""
                import A
                while has minecraft:iron_ingot in A > 0 do
                    redstone A 1 tick
                    break
                redstone A 5 tick
                done
                """, false));
        machine.tick(host);
        assertEquals(1, host.pulse, "the loop body runs once");
        for (int tick = 0; tick < 10 && !machine.stopped(); tick++) machine.tick(host);
        assertEquals(5, host.pulse, "break leaves the loop, so the tail after it runs");
        assertTrue(machine.stopped());
        assertTrue(machine.error().isEmpty());

        // break only leaves the innermost loop; the outer loop keeps its own control flow.
        var nestedHost = new Host();
        nestedHost.counts.put("A", 1L);
        var nested = new FactoryMachine(FactoryCompiler.compile("""
                import A
                while has minecraft:iron_ingot in A > 0 do
                    while has minecraft:iron_ingot in A > 0 do
                        redstone A 1 tick
                        break
                    redstone A 3 tick
                    break
                redstone A 7 tick
                done
                """, false));
        for (int tick = 0; tick < 30 && !nested.stopped(); tick++) nested.tick(nestedHost);
        assertEquals(7, nestedHost.pulse);
        assertTrue(nested.stopped());
        assertTrue(nested.error().isEmpty());

        // A break outside every loop behaves exactly like done.
        var rootHost = new Host();
        var root = new FactoryMachine(FactoryCompiler.compile("""
                import A
                redstone A 9 tick
                break
                redstone A 1 tick
                """, false));
        for (int tick = 0; tick < 30 && !root.stopped(); tick++) root.tick(rootHost);
        assertTrue(root.stopped());
        assertEquals(9, rootHost.pulse);
        assertTrue(root.error().isEmpty());

        // break is reserved, so it cannot become a tag or function name.
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("import break", false));
        assertThrows(FactoryProgram.CompileException.class, () -> FactoryCompiler.compile("func break\n    done\nend", false));
    }

    @Test void redstoneWaitAndMinuteConversion() {
        var host = new Host();
        var machine = new FactoryMachine(FactoryCompiler.compile("import A\nredstone A 1 min\ndone", false));
        machine.tick(host);
        assertEquals(1200, host.pulse);
        for (int i = 0; i < 1199; i++) machine.tick(host);
        assertFalse(machine.stopped());
        machine.tick(host);
        assertTrue(machine.stopped());
    }

    @Test void unavailableDestinationSuspendsWithoutRepeatingCompletedTransfers() {
        boolean[] available = {false};
        var host = new Host() {
            @Override public long transfer(boolean get, String resource, String tag, String face, long limit) {
                if (!get && !available[0]) throw new FactoryMachine.Pause("unloaded");
                return super.transfer(get, resource, tag, face, limit);
            }
        };
        host.counts.put("A", 64L);
        var program = FactoryCompiler.compile("import A\nget 64 minecraft:stone from A\nput minecraft:stone into source\ndone", true);
        var machine = new FactoryMachine(program);
        machine.tick(host);
        var first = machine.snapshot();
        for (int i = 0; i < 100; i++) machine.tick(host);
        assertEquals(first, machine.snapshot());
        assertEquals("unloaded", machine.waitingReason());
        assertEquals(64, host.counts.get("A"));
        assertEquals(0, host.output);
        machine = FactoryMachine.restore(program, machine.snapshot());
        available[0] = true;
        machine.tick(host);
        assertTrue(machine.stopped());
        assertEquals("", machine.error());
        assertEquals(64, host.output);
        machine.tick(host);
        assertEquals(64, host.output);
    }
    @Test void finiteMutualRecursionSurvivesEveryWaitWithoutReplay() {
        var host = new Host(); host.counts.put("A", 8L);
        var program = FactoryCompiler.compile("""
                import A
                func first
                    if A has minecraft:stone > 0 do
                        get 1 minecraft:stone from A
                        put minecraft:stone into source
                        wait 1 tick
                        second
                end
                func second
                    if A has minecraft:stone > 0 do
                        get 1 minecraft:stone from A
                        put minecraft:stone into source
                        wait 1 tick
                        first
                end
                first
                done
                """, true);
        var machine = new FactoryMachine(program);
        int maxDepth = 0;
        for (int i = 0; i < 12 && !machine.stopped(); i++) {
            machine.tick(host);
            maxDepth = Math.max(maxDepth, machine.snapshot().returns().size());
            machine = FactoryMachine.restore(program, machine.snapshot());
        }
        assertTrue(maxDepth >= 8);
        assertTrue(machine.stopped());
        assertEquals("", machine.error());
        assertEquals(8, host.output);
        assertEquals(0L, host.counts.get("A"));
        assertTrue(machine.snapshot().returns().isEmpty());
    }

    @Test void directAndMutualRunawayRecursionStopAtBoundedDepth() {
        for (String code : new String[]{
                "func recurse\n    recurse\nend\nrecurse",
                "func first\n    second\nend\nfunc second\n    first\nend\nfirst",
                "func recurse\n    wait 1 tick\n    recurse\nend\nrecurse"}) {
            var machine = new FactoryMachine(FactoryCompiler.compile(code, false));
            var host = new Host(); host.counts.put("A", 64L);
            for (int i = 0; i < 70 && !machine.stopped(); i++) machine.tick(host);
            assertTrue(machine.stopped());
            assertTrue(machine.error().contains("recursion exceeds 64"), machine.error());
            assertEquals(64, machine.snapshot().returns().size());
            assertEquals(64L, host.counts.get("A"));
            assertEquals(0, host.output);
        }
    }

    @Test void restoreRejectsForgedRecursiveContinuations() {
        var program = FactoryCompiler.compile("done", false);
        assertThrows(IllegalArgumentException.class, () -> FactoryMachine.restore(program,
                new FactoryMachine.Snapshot(0, 1, java.util.Collections.nCopies(65, 0), false, "", 0)));
        assertThrows(IllegalArgumentException.class, () -> FactoryMachine.restore(program,
                new FactoryMachine.Snapshot(0, 1, java.util.List.of(999), false, "", 0)));
    }
}
