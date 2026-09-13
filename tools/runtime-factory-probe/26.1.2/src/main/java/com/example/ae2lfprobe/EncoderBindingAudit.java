package com.example.ae2lfprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryContent;
import com.example.ae2lightoptimizer.factory.FactoryEncoderItem;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real-server checks for the handheld encoder binding rules: a shift click on a non-interactive
 * block clears the binding, while the same click on a member of the bound network removes the
 * selected tag and keeps the binding.
 *
 * Enable with -Dae2lf.probe.encoderBinding=true. Writes encoder-binding-report.json.
 */
public final class EncoderBindingAudit {
    private static final boolean ENABLED = Boolean.getBoolean("ae2lf.probe.encoderBinding");
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static final BlockPos CABLE = new BlockPos(320, 100, 240);
    private static final BlockPos TERMINAL = CABLE.south();
    private static final BlockPos PLAIN = CABLE.north();
    private static final BlockPos MACHINE = CABLE.east();
    private static final String DRAFT = "name \"T\"\nwait 1 tick\ndone";
    private static boolean done;
    private static int phase, wait, ticks;
    private static ServerLevel level;

    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || done) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        level = server.overworld();
        try {
            ticks++;
            if (ticks > 1200) { evidence.put("timeout", true); finish("timeout"); return; }
            if (phase == 0 && ticks < 40) return;
            wait++;
            switch (phase) {
                case 0 -> {
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CABLE.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(CABLE, FactoryContent.CABLE.get().defaultBlockState());
                    level.setBlockAndUpdate(TERMINAL, FactoryContent.TERMINAL.get().defaultBlockState());
                    level.setBlockAndUpdate(PLAIN, Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(MACHINE, Blocks.BARREL.defaultBlockState());
                    player.connection.teleport(CABLE.getX() + 0.5, CABLE.getY() + 2, CABLE.getZ() + 4.5, 180f, 30f);
                    phase = 1; wait = 0;
                }
                case 1 -> {
                    if (wait < 60) return;
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    var owner = FactoryServer.owner(terminal.factoryGrid());
                    evidence.put("ownerIsTerminal", owner == terminal);
                    if (owner == null) {
                        // The ownership election can take a few cycles after the fixture is placed.
                        if (wait < 400) return;
                        finish("missing-owner");
                        return;
                    }
                    evidence.put("terminalId", owner.factoryId());
                    evidence.put("plainBlockHasBlockEntity", level.getBlockEntity(PLAIN) != null);
                    evidence.put("machineBlockHasBlockEntity", level.getBlockEntity(MACHINE) != null);
                    evidence.put("machineIsNetworkMember",
                            FactoryServer.contains(owner.factoryGrid(), MACHINE));
                    evidence.put("plainIsNetworkMember", FactoryServer.contains(owner.factoryGrid(), PLAIN));

                    // Shift click on a non-interactive block clears the binding but keeps the draft.
                    var encoder = bound(owner.factoryId());
                    FactoryEncoderItem.shiftClick(player, encoder, PLAIN, false);
                    var afterPlain = FactoryPatternData.get(encoder);
                    evidence.put("plainClear_binding", afterPlain.factoryId());
                    evidence.put("plainClear_draftKept", DRAFT.equals(afterPlain.code()));

                    // Shift click on a member of the bound network removes the tag, not the binding.
                    owner.tags.reconcile(List.of("T1"));
                    owner.tags.tag("T1", MACHINE.asLong(), pos -> true);
                    owner.saveChanges();
                    var second = bound(owner.factoryId());
                    var view = new com.example.ae2lightoptimizer.factory.FactoryEncoderView(
                            List.of("T1"), "T1", List.of(), List.of(), List.of(),
                            level.dimension().toString());
                    second.set(com.example.ae2lightoptimizer.factory.FactoryEncoderView.TYPE.get(), view);
                    int before = owner.tags.positions("T1").size();
                    FactoryEncoderItem.shiftClick(player, second, MACHINE, false);
                    evidence.put("memberShift_tagBefore", before);
                    evidence.put("memberShift_tagAfter", owner.tags.positions("T1").size());
                    evidence.put("memberShift_bindingKept",
                            FactoryPatternData.get(second).factoryId().equals(owner.factoryId()));

                    // A plain click on a member still tags the machine.
                    FactoryEncoderItem.mark(player, second, MACHINE, false);
                    evidence.put("markRetagged", owner.tags.positions("T1").size());
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (wait < 10) return;
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

    private static ItemStack bound(String factoryId) {
        var stack = FactoryContent.ENCODER.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(DRAFT, factoryId, ItemStack.EMPTY));
        return stack;
    }

    private static void finish(String status) {
        done = true;
        evidence.put("status", status);
        evidence.put("ticks", ticks);
        try {
            var directory = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("encoder-binding-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        if (!Boolean.getBoolean("ae2lf.probe.fullAudit")) {
            RestartState.visualReady = false;
            RestartState.exitRequested = true;
        }
    }

    private EncoderBindingAudit() {}
}
