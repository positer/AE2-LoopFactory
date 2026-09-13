package com.example.ae2lfprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryContent;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryResourceSelector;
import com.example.ae2lightoptimizer.factory.FactoryServer;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real-client checks for tag operands and machine tag aggregation:
 * <ul>
 *   <li>item, fluid and mod-contributed tags resolve against the live registries;</li>
 *   <li>{@code has resource in Tag} reads one group while a bare {@code has resource} reads every
 *       machine tag;</li>
 *   <li>{@code A&B} aggregates machine tags for both reads and transfers.</li>
 * </ul>
 * Enable with -Dae2lf.probe.tagHas=true. Writes tag-has-report.json.
 */
public final class TagAndHasAudit {
    private static final boolean ENABLED = Boolean.getBoolean("ae2lf.probe.tagHas");
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static final BlockPos CABLE = new BlockPos(300, 100, 240);
    private static final BlockPos TERMINAL = CABLE.south();
    private static final BlockPos SRC_A = CABLE.north();
    private static final BlockPos SRC_B = CABLE.east();
    private static final BlockPos DST = CABLE.west();
    private static final String MOD_ITEM = "appflux:harden_insulating_resin";
    private static final String READ_PROGRAM = """
            import SrcA,SrcB,Dst
            if has #c:ingots in SrcB = 6 do
                get #c:ingots from SrcB
                put #c:ingots into Dst
            if has #c:ingots = 10 do
                get #c:ingots from SrcA
                put #c:ingots into Dst
            done
            """.strip();
    private static final String COMPOUND_PROGRAM = """
            import SrcA,SrcB,Dst
            get #c:ingots from SrcA&SrcB
            put #c:ingots into Dst
            done
            """.strip();
    private static boolean done;
    private static int phase, wait, ticks;
    private static ServerLevel level;
    private static net.minecraft.server.level.ServerPlayer player;

    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || done) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        player = server.getPlayerList().getPlayers().getFirst();
        level = server.overworld();
        try {
            ticks++;
            if (ticks > 3000) { evidence.put("timeout", true); finish("timeout"); return; }
            if (phase == 0 && ticks < 40) return;
            wait++;
            switch (phase) {
                case 0 -> {
                    evidence.put("selectorMatrix", selectorMatrix());
                    build();
                    // The fixture sits far from spawn; keep its chunks ticking for the whole run.
                    player.connection.teleport(CABLE.getX() + 0.5, CABLE.getY() + 2, CABLE.getZ() + 4.5, 180f, 30f);
                    phase = 1; wait = 0;
                }
                case 1 -> {
                    if (wait < 60) return;
                    // The ownership election has to settle before a position counts as network member.
                    if (!tagFixture()) {
                        if (wait < 300) return;
                        evidence.put("taggingTimedOut", true);
                        finish("completed");
                        return;
                    }
                    evidence.put("readProgram", READ_PROGRAM);
                    evidence.put("containersBefore", containers());
                    install(READ_PROGRAM);
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (wait < 5) return;
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase = 3; wait = 0;
                }
                case 3 -> {
                    if (wait < 120) return;
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("containersAfterHas", containers());
                    evidence.put("executionErrorAfterHas", error());
                    evidence.put("jobsAfterHas", jobs());
                    refill();
                    evidence.put("compoundProgram", COMPOUND_PROGRAM);
                    evidence.put("containersBeforeCompound", containers());
                    install(COMPOUND_PROGRAM);
                    phase = 4; wait = 0;
                }
                case 4 -> {
                    if (wait < 5) return;
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase = 5; wait = 0;
                }
                case 5 -> {
                    if (wait < 120) return;
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("containersAfterCompound", containers());
                    evidence.put("executionErrorAfterCompound", error());
                    evidence.put("jobsAfterCompound", jobs());
                    phase = 6; wait = 0;
                }
                case 6 -> {
                    if (wait < 20) return;
                    finish("completed");
                }
                default -> finish("completed");
            }
        } catch (Throwable failure) {
            evidence.put("failure", failure.toString());
            evidence.put("stack", java.util.Arrays.toString(failure.getStackTrace()));
            finish("failed");
        }
    }

    private static boolean tagFixture() {
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
        terminal.tags.reconcile(List.of("SrcA", "SrcB", "Dst"));
        terminal.tags.tag("SrcA", SRC_A.asLong(),
                pos -> FactoryServer.contains(terminal.factoryGrid(), BlockPos.of(pos)));
        terminal.tags.tag("SrcB", SRC_B.asLong(),
                pos -> FactoryServer.contains(terminal.factoryGrid(), BlockPos.of(pos)));
        terminal.tags.tag("Dst", DST.asLong(),
                pos -> FactoryServer.contains(terminal.factoryGrid(), BlockPos.of(pos)));
        evidence.put("taggedSrcA", terminal.tags.positions("SrcA").size());
        evidence.put("taggedSrcB", terminal.tags.positions("SrcB").size());
        evidence.put("taggedDst", terminal.tags.positions("Dst").size());
        return terminal.tags.positions("SrcA").size() == 1
                && terminal.tags.positions("SrcB").size() == 1
                && terminal.tags.positions("Dst").size() == 1;
    }

    private static void install(String program) {
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),
                new FactoryPatternData(program, terminal.factoryId(), ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0, stack);
    }

    private static String error() {
        return ((FactoryBlockEntity) level.getBlockEntity(TERMINAL)).executionError();
    }

    private static int jobs() {
        return ((FactoryBlockEntity) level.getBlockEntity(TERMINAL)).activeJobCount();
    }

    /** Every entry is evaluated against the live registries of this running client/server. */
    private static Map<String, Object> selectorMatrix() {
        var result = new LinkedHashMap<String, Object>();
        var resin = item(MOD_ITEM);
        var iron = item("minecraft:iron_ingot");
        var oakPlanks = item("minecraft:oak_planks");
        var oakLog = item("minecraft:oak_log");
        result.put("modItemPresent:" + MOD_ITEM, resin != null);
        result.put("#minecraft:planks matches minecraft:oak_planks", matches("#minecraft:planks", oakPlanks));
        result.put("#minecraft:planks matches minecraft:iron_ingot", matches("#minecraft:planks", iron));
        result.put("#minecraft:logs matches minecraft:oak_log", matches("#minecraft:logs", oakLog));
        result.put("#c:ingots matches " + MOD_ITEM, matches("#c:ingots", resin));
        result.put("#c:ingots matches minecraft:iron_ingot", matches("#c:ingots", iron));
        result.put("#minecraft:water matches fluid water", matches("#minecraft:water",
                AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER)));
        result.put("#minecraft:water matches fluid lava", matches("#minecraft:water",
                AEFluidKey.of(net.minecraft.world.level.material.Fluids.LAVA)));
        result.put("#minecraft:water matches item oak_log",
                oakLog != null && matches("#minecraft:water", AEItemKey.of(oakLog)));
        result.put("#minecraft:lava matches fluid lava", matches("#minecraft:lava",
                AEFluidKey.of(net.minecraft.world.level.material.Fluids.LAVA)));
        result.put("#ae2lfprobe:not_a_real_tag matches anything", matches("#ae2lfprobe:not_a_real_tag", resin));
        return result;
    }

    private static boolean matches(String selector, AEKey key) {
        if (key == null) return false;
        try {
            return FactoryResourceSelector.parse(selector).test(key);
        } catch (RuntimeException failure) {
            evidence.put("selectorFailure:" + selector, failure.toString());
            return false;
        }
    }

    private static boolean matches(String selector, Item item) {
        return item != null && matches(selector, AEItemKey.of(item));
    }

    private static Item item(String name) {
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString().equals(name)) return item;
        }
        return null;
    }

    private static void build() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            level.setBlockAndUpdate(CABLE.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(CABLE, FactoryContent.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(TERMINAL, FactoryContent.TERMINAL.get().defaultBlockState());
        level.setBlockAndUpdate(SRC_A, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(SRC_B, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(DST, Blocks.CHEST.defaultBlockState());
        evidence.put("fixtureModItem", item(MOD_ITEM) == null ? "missing" : MOD_ITEM);
        refill();
    }

    private static void refill() {
        var resin = item(MOD_ITEM);
        var sourceA = (net.minecraft.world.Container) level.getBlockEntity(SRC_A);
        var sourceB = (net.minecraft.world.Container) level.getBlockEntity(SRC_B);
        clear(sourceA);
        clear(sourceB);
        if (resin != null) {
            sourceA.setItem(0, new ItemStack(resin, 4));
            sourceB.setItem(0, new ItemStack(resin, 6));
        }
    }

    private static void clear(net.minecraft.world.Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) container.setItem(slot, ItemStack.EMPTY);
    }

    private static Map<String, Object> containers() {
        var result = new LinkedHashMap<String, Object>();
        for (var pos : List.of(SRC_A, SRC_B, DST)) {
            if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)) continue;
            var counts = new LinkedHashMap<String, Integer>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                counts.merge(id, stack.getCount(), Integer::sum);
            }
            result.put(pos.toShortString(), counts);
        }
        return result;
    }

    private static void finish(String status) {
        done = true;
        evidence.put("status", status);
        evidence.put("ticks", ticks);
        try {
            var directory = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("tag-has-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        // A full-audit launch keeps running and owns the camera; only a focused launch ends the client.
        if (!Boolean.getBoolean("ae2lf.probe.fullAudit")) {
            RestartState.visualReady = false;
            RestartState.exitRequested = true;
        }
    }

    private TagAndHasAudit() {}
}
