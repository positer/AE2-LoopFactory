package com.example.ae2lfprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Self-test inside a copy of the reporter's own superflat world: install a program that uses the new
 * selector operators into the existing terminal, press its own button, and record what moved.
 *
 * Enable with -Dae2lf.probe.userTag=true together with the openWorld property. The source world
 * itself is never touched; only the prepared copy is edited.
 */
public final class UserWorldTagAudit {
    private static final boolean ENABLED = Boolean.getBoolean("ae2lf.probe.userTag");
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static final List<Map<String, Object>> samples = new ArrayList<>();
    private static final String PROGRAM = """
            name "Fue"
            import Furnance
            import stor
            get #minecraft:logs from stor
            put #minecraft:logs into Furnance on up
            done
            """.strip();
    private static boolean done;
    private static int phase, wait, ticks, maxJobs;
    private static ServerLevel level;
    private static BlockPos terminalPos, buttonPos, sourcePos;
    private static final List<BlockPos> furnaces = new ArrayList<>();

    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || done) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        level = server.overworld();
        try {
            ticks++;
            if (ticks > 4000) { evidence.put("timeout", true); finish("timeout"); return; }
            if (phase == 0 && ticks < 40) return;
            wait++;
            if (phase == 2) sample();
            switch (phase) {
                case 0 -> {
                    locate();
                    if (terminalPos == null || buttonPos == null) { finish("missing-fixture"); return; }
                    evidence.put("terminalPos", terminalPos.toShortString());
                    evidence.put("buttonPos", buttonPos.toShortString());
                    evidence.put("sourcePos", sourcePos == null ? "none" : sourcePos.toShortString());
                    evidence.put("furnaces", furnaces.size());
                    evidence.put("program", PROGRAM);
                    evidence.put("containersBefore", containers());
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(terminalPos);
                    var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                    stack.set(FactoryPatternData.TYPE.get(),
                            new FactoryPatternData(PROGRAM, terminal.factoryId(), ItemStack.EMPTY));
                    terminal.factoryPatterns().setItemDirect(0, stack);
                    player.connection.teleport(terminalPos.getX() + 0.5, terminalPos.getY() + 2,
                            terminalPos.getZ() + 0.5, 180f, 30f);
                    phase = 1; wait = 0;
                }
                case 1 -> {
                    if (wait < 60) return;
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(terminalPos);
                    evidence.put("ownerIsTerminal", FactoryServer.owner(terminal.factoryGrid()) == terminal);
                    evidence.put("installedFactoryId", FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0)).factoryId());
                    evidence.put("terminalFactoryId", terminal.factoryId());
                    evidence.put("installedCode", FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0)).code());
                    evidence.put("jobsBeforePress", terminal.activeJobCount());
                    setButton(true);
                    evidence.put("buttonPowered", isButtonPowered());
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (wait < 120) return;
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(terminalPos);
                    evidence.put("jobsAfterPress", terminal.activeJobCount());
                    evidence.put("maxObservedJobs", maxJobs);
                    evidence.put("executionError", terminal.executionError());
                    evidence.put("waiting", terminal.waitingStatus());
                    setButton(false);
                    evidence.put("containersAfter", containers());
                    evidence.put("samples", List.copyOf(samples));
                    phase = 3; wait = 0;
                }
                case 3 -> {
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

    private static void locate() {
        var origin = new BlockPos(0, -60, 7);
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                for (int y = -64; y <= -40; y++) {
                    var pos = origin.offset(x, y + 60, z);
                    if (!(level.getBlockEntity(pos) instanceof FactoryBlockEntity host)) continue;
                    if (host.kind() == FactoryBlock.Kind.TERMINAL && terminalPos == null) {
                        terminalPos = pos.immutable();
                        for (var side : net.minecraft.core.Direction.values()) {
                            var neighbour = pos.relative(side);
                            if (level.getBlockState(neighbour).getBlock() instanceof ButtonBlock && buttonPos == null)
                                buttonPos = neighbour.immutable();
                        }
                    }
                    if (host.kind() == FactoryBlock.Kind.CABLE) {
                        for (var side : net.minecraft.core.Direction.values()) {
                            var neighbour = pos.relative(side);
                            if (level.getBlockEntity(neighbour) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                                if (sourcePos == null) sourcePos = neighbour.immutable();
                            } else if (level.getBlockEntity(neighbour) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
                                if (!furnaces.contains(neighbour)) furnaces.add(neighbour.immutable());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void sample() {
        if (!(level.getBlockEntity(terminalPos) instanceof FactoryBlockEntity terminal)) return;
        var row = new LinkedHashMap<String, Object>();
        row.put("tick", wait);
        row.put("powered", level.hasNeighborSignal(terminalPos));
        row.put("jobs", terminal.activeJobCount());
        row.put("waiting", terminal.waitingStatus());
        row.put("error", terminal.executionError());
        samples.add(row);
        if (terminal.activeJobCount() > maxJobs) maxJobs = terminal.activeJobCount();
    }

    private static void setButton(boolean powered) {
        BlockState state = level.getBlockState(buttonPos);
        if (!(state.getBlock() instanceof ButtonBlock)) return;
        level.setBlockAndUpdate(buttonPos, state.setValue(ButtonBlock.POWERED, powered));
        level.updateNeighborsAt(buttonPos, state.getBlock());
    }

    private static boolean isButtonPowered() {
        return level.getBlockState(buttonPos).getValue(ButtonBlock.POWERED);
    }

    private static Map<String, Object> containers() {
        var result = new LinkedHashMap<String, Object>();
        List<BlockPos> positions = new ArrayList<>();
        if (sourcePos != null) positions.add(sourcePos);
        positions.addAll(furnaces);
        for (var pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)) continue;
            var counts = new LinkedHashMap<String, Integer>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                counts.merge(id, stack.getCount(), Integer::sum);
            }
            if (!counts.isEmpty()) result.put(pos.toShortString(), counts);
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
            Files.writeString(directory.resolve("user-world-tag-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        RestartState.visualReady = false;
        RestartState.exitRequested = true;
    }

    private UserWorldTagAudit() {}
}
