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
import com.example.ae2lightoptimizer.factory.UniqueNetworkServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Read-only diagnosis of a prepared copy of a real player world: locate the factory terminal,
 * dump its live state, then simulate a genuine button press/release and record what happens.
 *
 * Enable with -Dae2lf.probe.inspectWorld=true. Writes inspect-redstone-report.json.
 */
public final class InspectRedstoneAudit {
    private static final boolean ENABLED = Boolean.getBoolean("ae2lf.probe.inspectWorld");
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static boolean done;
    private static int phase, wait, ticks;
    private static ServerLevel level;
    private static BlockPos terminalPos, buttonPos;
    private static int lastJobs = -1;
    private static final List<Map<String, Object>> timeline = new ArrayList<>();
    private static final List<Map<String, Object>> pressSamples = new ArrayList<>();
    private static final List<BlockPos> nearContainers = new ArrayList<>();
    private static int sampleBaseline = -1;
    private static ItemStack savedSlot = ItemStack.EMPTY;

    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || done) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        level = (ServerLevel) player.level();
        try {
            ticks++;
            if (ticks == 1) {
                player.getAbilities().flying = true;
                player.getAbilities().mayfly = true;
            }
            if (ticks > 4000) { evidence.put("timeout", true); finish("timeout"); return; }
            if (phase == 0 && ticks < 40) return;
            wait++;
            if (phase == 2 || phase == 4) samplePress();
            switch (phase) {
                case 0 -> {
                    locate();
                    if (terminalPos == null) {
                        evidence.put("terminal_found", false);
                        finish("completed");
                        return;
                    }
                    dump("before", null);
                    collectContainers();
                    evidence.put("containersBefore", containerSummary());
                    if (buttonPos != null) player.connection.teleport(terminalPos.getX() + 0.5, terminalPos.getY() + 2,
                            terminalPos.getZ() + 0.5, 180f, 30f);
                    phase = 1; wait = 0;
                }
                case 1 -> {
                    if (buttonPos == null) { evidence.put("button_found", false); finish("completed"); return; }
                    pressSamples.clear();
                    sampleBaseline = ((FactoryBlockEntity) level.getBlockEntity(terminalPos)).activeJobCount();
                    evidence.put("press1_baselineJobs", sampleBaseline);
                    setButton(true);
                    evidence.put("press1_buttonPowered", isButtonPowered());
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (wait < 40) return;
                    dump("afterPress1", null);
                    evidence.put("containersAfterPress1", containerSummary());
                    setButton(false);
                    phase = 3; wait = 0;
                }
                case 3 -> {
                    if (wait < 40) return;
                    dump("afterRelease1", null);
                    pressSamples.clear();
                    sampleBaseline = ((FactoryBlockEntity) level.getBlockEntity(terminalPos)).activeJobCount();
                    evidence.put("press2_baselineJobs", sampleBaseline);
                    setButton(true);
                    evidence.put("press2_buttonPowered", isButtonPowered());
                    phase = 4; wait = 0;
                }
                case 4 -> {
                    if (wait < 40) return;
                    dump("afterPress2", null);
                    evidence.put("containersAfterPress2", containerSummary());
                    setButton(false);
                    evidence.put("timeline", List.copyOf(timeline));
                    evidence.put("pressSamples", List.copyOf(pressSamples));
                    directJobTrial();
                    evidence.put("conditionsAtPress", redstoneConditions());
                    savedSlot = ((FactoryBlockEntity) level.getBlockEntity(terminalPos)).factoryPatterns().getStackInSlot(0).copy();
                    installProbeProgram();
                    phase = 5; wait = 0;
                }
                case 5 -> {
                    if (wait < 5) return;
                    pressSamples.clear();
                    sampleBaseline = ((FactoryBlockEntity) level.getBlockEntity(terminalPos)).activeJobCount();
                    setButton(true);
                    phase = 6; wait = 0;
                }
                case 6 -> {
                    if (wait < 8) return;
                    evidence.put("probePress_baselineJobs", sampleBaseline);
                    evidence.put("probePress_jobsAfterPress", ((FactoryBlockEntity) level.getBlockEntity(terminalPos)).activeJobCount());
                    setButton(false);
                    phase = 7; wait = 0;
                }
                case 7 -> {
                    if (wait < 60) return;
                    ((FactoryBlockEntity) level.getBlockEntity(terminalPos)).factoryPatterns().setItemDirect(0, savedSlot);
                    evidence.put("slotRestored", true);
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
                    if (host.kind() != FactoryBlock.Kind.TERMINAL) {
                        continue;
                    }
                    if (terminalPos == null) terminalPos = pos.immutable();
                    for (var side : Direction.values()) {
                        var neighbour = pos.relative(side);
                        if (level.getBlockState(neighbour).getBlock() instanceof ButtonBlock && buttonPos == null) {
                            buttonPos = neighbour.immutable();
                        }
                    }
                }
            }
        }
        evidence.put("terminal_found", terminalPos != null);
        evidence.put("button_found", buttonPos != null);
        if (terminalPos != null) evidence.put("terminalPos", terminalPos.toShortString());
        if (buttonPos != null) evidence.put("buttonPos", buttonPos.toShortString());
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

    private static void dump(String label, String unused) {
        if (!(level.getBlockEntity(terminalPos) instanceof FactoryBlockEntity terminal)) {
            evidence.put(label + "_terminalPresent", false);
            return;
        }
        evidence.put(label + "_terminalPresent", true);
        evidence.put(label + "_terminalNeighborSignal", level.hasNeighborSignal(terminalPos));
        evidence.put(label + "_lastRedstone", readLastRedstone(terminal));
        evidence.put(label + "_activeJobs", terminal.activeJobCount());
        evidence.put(label + "_executionError", terminal.executionError());
        evidence.put(label + "_ownerIsTerminal", FactoryServer.owner(terminal.factoryGrid()) == terminal);
        evidence.put(label + "_serviceDisconnected", UniqueNetworkServices.disconnected(terminal));
        evidence.put(label + "_nodeActive", terminal.getMainNode().isActive());
        evidence.put(label + "_gridPresent", terminal.getMainNode().getGrid() != null);
        evidence.put(label + "_terminalFactoryId", terminal.factoryId());
        var installed = FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0));
        evidence.put(label + "_installedFactoryId", installed.factoryId());
        evidence.put(label + "_installedCode", installed.code());
        evidence.put(label + "_installedHasRecipe", installed.hasRecipe());
        evidence.put(label + "_installedIsFactoryItem", terminal.factoryPatterns().getStackInSlot(0).getItem()
                instanceof com.example.ae2lightoptimizer.factory.FactoryPatternItem);
        evidence.put(label + "_waiting", terminal.waitingStatus());
        int jobs = terminal.activeJobCount();
        if (lastJobs < 0) lastJobs = jobs;
        var row = new LinkedHashMap<String, Object>();
        row.put("label", label);
        row.put("jobs", jobs);
        row.put("waiting", terminal.waitingStatus());
        row.put("error", terminal.executionError());
        timeline.add(row);
    }

    private static void samplePress() {
        if (!(level.getBlockEntity(terminalPos) instanceof FactoryBlockEntity terminal)) return;
        var row = new LinkedHashMap<String, Object>();
        row.put("tick", wait);
        row.put("powered", level.hasNeighborSignal(terminalPos));
        row.put("lastRedstone", readLastRedstone(terminal));
        row.put("jobs", terminal.activeJobCount());
        row.put("waiting", terminal.waitingStatus());
        row.put("error", terminal.executionError());
        pressSamples.add(row);
    }

    private static void collectContainers() {
        nearContainers.clear();
        for (int x = -2; x <= 6; x++) {
            for (int z = 4; z <= 10; z++) {
                for (int y = -62; y <= -58; y++) {
                    var pos = new BlockPos(x, y, z);
                    if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container) nearContainers.add(pos);
                }
            }
        }
    }

    private static Map<String, Object> containerSummary() {
        var result = new LinkedHashMap<String, Object>();
        for (var pos : nearContainers) {
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

    /** Exact conditions the production redstone branch evaluates, reported from the live world. */
    private static Map<String, Object> redstoneConditions() {
        var result = new LinkedHashMap<String, Object>();
        if (!(level.getBlockEntity(terminalPos) instanceof FactoryBlockEntity terminal)) return result;
        var stack = terminal.factoryPatterns().getStackInSlot(0);
        var installed = FactoryPatternData.get(stack);
        result.put("powered", level.hasNeighborSignal(terminalPos));
        result.put("lastRedstone", readLastRedstone(terminal));
        result.put("recognizes", com.example.ae2lightoptimizer.factory.SfmSyntax.recognizes(installed.code()));
        result.put("idMatches", installed.factoryId().equals(terminal.factoryId()));
        result.put("recipeFree", !installed.hasRecipe());
        result.put("isFactoryItem", stack.getItem() instanceof com.example.ae2lightoptimizer.factory.FactoryPatternItem);
        result.put("jobsBelowCap", terminal.activeJobCount() < 64);
        result.put("owner", FactoryServer.owner(terminal.factoryGrid()) == terminal);
        result.put("storTagPositions", terminal.tags.positions("stor").stream().map(BlockPos::of)
                .map(BlockPos::toShortString).toList());
        result.put("furnanceTagPositions", terminal.tags.positions("Furnance").size());
        return result;
    }

    private static void installProbeProgram() {
        if (!(level.getBlockEntity(terminalPos) instanceof FactoryBlockEntity terminal)) return;
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),
                new FactoryPatternData("wait 20 tick\ndone", terminal.factoryId(), ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0, stack);
        evidence.put("probeProgramInstalled", true);
    }

    /** Drive candidate programs directly so the selector itself is isolated from the redstone path. */
    private static void directJobTrial() {
        if (!(level.getBlockEntity(terminalPos) instanceof FactoryBlockEntity terminal)) return;
        String[][] variants = {
                {"user_exact_single_colon",
                        "get 64 minecraft:item from stor\nput 64 minecraft:item into Furnance on up\ndone"},
                {"double_colon_on_up",
                        "get 64 minecraft::item from stor\nput 64 minecraft::item into Furnance on up\ndone"},
                {"double_colon_plain_put",
                        "get 64 minecraft::item from stor\nput minecraft::item into Furnance\ndone"},
        };
        var results = new LinkedHashMap<String, Object>();
        for (var variant : variants) {
            var before = containerSummary();
            String code = "name \"Fue\"\nimport Furnance\nimport stor\n" + variant[1];
            var row = new LinkedHashMap<String, Object>();
            try {
                var job = new com.example.ae2lightoptimizer.factory.FactoryJob(terminal,
                        new FactoryPatternData(code, terminal.factoryId(), ItemStack.EMPTY));
                row.put("constructed", true);
                for (int i = 0; i < 60; i++) job.tick();
                row.put("error", job.error());
                row.put("waiting", job.waitingStatus());
                row.put("finished", job.finished());
            } catch (Throwable failure) {
                row.put("constructed", false);
                row.put("failure", failure.toString());
            }
            row.put("containersBefore", before);
            row.put("containersAfter", containerSummary());
            results.put(variant[0], row);
        }
        evidence.put("directTrials", results);
    }

    private static boolean readLastRedstone(FactoryBlockEntity terminal) {
        try {
            var field = FactoryBlockEntity.class.getDeclaredField("lastRedstone");
            field.setAccessible(true);
            return field.getBoolean(terminal);
        } catch (ReflectiveOperationException failure) {
            evidence.put("reflectionFailure", failure.toString());
            return false;
        }
    }

    private static void finish(String status) {
        done = true;
        evidence.put("status", status);
        evidence.put("ticks", ticks);
        try {
            var directory = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("inspect-redstone-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        RestartState.visualReady = false;
        RestartState.exitRequested = true;
    }

    private InspectRedstoneAudit() {}
}
