package com.example.ae2lfprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryContent;
import com.example.ae2lightoptimizer.factory.FactoryJob;
import com.example.ae2lightoptimizer.factory.FactoryMachine;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryRoutes;
import com.example.ae2lightoptimizer.factory.FactoryServer;
import com.example.ae2lightoptimizer.factory.UniqueNetworkServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real-world checks for the indentation based {@code channel} statement and for the rule that a
 * pattern carrying code replaces the editor contents.
 *
 * Enable with -Dae2lf.probe.channel=true in the background audit. Writes channel-report.json.
 */
public final class ChannelAudit {
    private static final boolean ENABLED = Boolean.getBoolean("ae2lf.probe.channel");
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static final BlockPos CABLE = new BlockPos(340, 100, 240);
    private static final BlockPos TERMINAL = CABLE.south();
    private static final BlockPos SRC_A = CABLE.north();
    private static final BlockPos SRC_B = CABLE.east();
    private static final BlockPos DST = CABLE.west();
    private static final BlockPos MUST_SRC = CABLE.above();
    private static final BlockPos MUST_DST = CABLE.below();
    private static final long TRILLION = 1_000_000_000_000L;
    private static final String CROSS_CHANNEL_EMPTY = """
            import MustSrc,MustDst
            channel
                get must 1000000000000 minecraft:iron_ingot from MustSrc
            put minecraft:iron_ingot into MustDst
            done
            """.strip();
    private static final String CROSS_CHANNEL_TRANSFER = """
            import MustSrc,MustDst
            channel
                get must 1000000000000 minecraft:iron_ingot from MustSrc
            get 3 minecraft:iron_ingot from MustSrc
            put minecraft:iron_ingot into MustDst
            done
            """.strip();
    private static final String SAME_CHANNEL_MUST = """
            import MustSrc,MustDst
            channel
                get must 5 minecraft:iron_ingot from MustSrc
                put minecraft:iron_ingot into MustDst
            done
            """.strip();
    private static final String CODED = """
            import SrcA,SrcB,Dst
            channel
                if has minecraft:iron_ingot in SrcB > 0 do
                    get 4 minecraft:iron_ingot from SrcB
                    put 4 minecraft:iron_ingot into Dst
            channel
                while has minecraft:iron_ingot in SrcA > 0 do
                    get 3 minecraft:iron_ingot from SrcA
                    put 3 minecraft:iron_ingot into Dst
                    break
            put 3 minecraft:iron_ingot into Dst
            done
            """.strip();
    private static volatile boolean started, done, accepted, clientHostPresent;
    /** The full-audit step machine waits on this instead of polling the report file. */
    public static boolean finished() { return done; }
    public static boolean passed() { return done && accepted; }
    private static int phase, wait, ticks, stableOwnerTicks, stableClientHostTicks;
    private static ServerLevel level;
    private static FactoryJob mustJob;
    private static final Map<String, Object> mustEvidence = new LinkedHashMap<>();

    /** Called by the background channel scene after the base native fixture has completed. */
    public static void start(ServerLevel world) {
        if (!ENABLED || started || done) return;
        level = world;
        evidence.put("startedByBackgroundScene", true);
        evidence.put("startedAtGameTime", world.getGameTime());
        clientHostPresent = false;
        try {
            var directory = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            for (String report : List.of("factory-report.json", "native-cpu-report.json")) {
                String status = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve(report)))
                        .getAsJsonObject().get("status").getAsString();
                evidence.put("prerequisite_" + report, status);
                check("passed".equals(status), "completed_prerequisite_" + report);
            }
            started = true;
        } catch (Throwable failure) {
            evidence.put("failure", failure.toString());
            finish("failed");
        }
    }

    /** Actual receiving-client observation; a forced server chunk is insufficient for opening a menu. */
    public static void observeClientHost(boolean present) {
        clientHostPresent = present;
    }

    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !started || done) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        try {
            ticks++;
            if (ticks > 2000) { evidence.put("timeout", true); finish("timeout"); return; }
            if (phase == 0 && ticks < 40) return;
            wait++;
            switch (phase) {
                case 0 -> {
                    // The source to the north is in a different chunk. Other native fixtures move
                    // the player, so a teleport alone cannot keep either half of this grid ticking.
                    var forcedChunks = new java.util.ArrayList<String>();
                    for (int x = (CABLE.getX() - 2) >> 4; x <= (CABLE.getX() + 2) >> 4; x++)
                        for (int z = (CABLE.getZ() - 2) >> 4; z <= (CABLE.getZ() + 2) >> 4; z++) {
                            level.setChunkForced(x, z, true);
                            forcedChunks.add(x + ", " + z);
                        }
                    evidence.put("forcedFixtureChunks", forcedChunks);
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CABLE.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(CABLE, FactoryContent.CABLE.get().defaultBlockState());
                    level.setBlockAndUpdate(TERMINAL, FactoryContent.TERMINAL.get().defaultBlockState());
                    level.setBlockAndUpdate(SRC_A, Blocks.CHEST.defaultBlockState());
                    level.setBlockAndUpdate(SRC_B, Blocks.CHEST.defaultBlockState());
                    level.setBlockAndUpdate(DST, Blocks.CHEST.defaultBlockState());
                    fill(SRC_A, 6);
                    fill(SRC_B, 6);
                    player.connection.teleport(CABLE.getX() + 0.5, CABLE.getY() + 2, CABLE.getZ() + 4.5, 180f, 30f);
                    phase = 1; wait = 0;
                }
                case 1 -> {
                    if (wait < 60) return;
                    if (!tagged(true)) {
                        stableOwnerTicks = 0;
                        if (wait < 400) return;
                        finish("missing-owner"); return;
                    }
                    // Observe the real election and physical cable membership across a complete
                    // service-election interval; never create a node or elect an owner in the probe.
                    if (++stableOwnerTicks < 20) return;
                    evidence.put("stableOwnerTicks", stableOwnerTicks);
                    evidence.put("program", CODED);
                    evidence.put("containersBefore", containers());
                    check(containers().equals(Map.of(SRC_A.toShortString(), 6, SRC_B.toShortString(), 6,
                            DST.toShortString(), 0)), "exact_initial_inventory");
                    install();
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (wait < 5) return;
                    check(tagged(false), "real_owner_and_all_tags_ready_before_signal");
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    check(FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0))
                            .factoryId().equals(terminal.factoryId()), "pattern_bound_to_actual_owner");
                    check(terminal.activeJobCount() == 0, "code_installation_run_settled_before_ledger_check");
                    phase = 3; wait = 0;
                }
                case 3 -> {
                    if (wait < 100) return;
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("containersAfter", containers());
                    evidence.put("executionError", ((FactoryBlockEntity) level.getBlockEntity(TERMINAL)).executionError());
                    // Visual check: open the terminal so the client renders the editor contents.
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    check(tagged(false), "real_owner_and_all_tags_retained_after_execution");
                    check(containers().equals(Map.of(SRC_A.toShortString(), 3, SRC_B.toShortString(), 2,
                            DST.toShortString(), 7)), "exact_channel_source_and_destination_balances");
                    check(terminal.executionError().isEmpty(), "execution_has_no_error");
                    check(terminal.activeJobCount() == 0, "channel_jobs_settled");
                    terminal.setFactoryDraft("stale draft that must be replaced");
                    terminal.factoryPatterns().setItemDirect(0, ItemStack.EMPTY);
                    var coded = pattern(terminal.factoryId(), CODED);
                    terminal.factoryPatterns().setItemDirect(0, coded);
                    evidence.put("draftAfterCodedPattern", terminal.factoryDraft());
                    check(CODED.equals(terminal.factoryDraft()), "coded_pattern_replaces_draft");
                    terminal.setFactoryDraft("draft kept when a blank pattern arrives");
                    terminal.factoryPatterns().setItemDirect(0, ItemStack.EMPTY);
                    terminal.factoryPatterns().setItemDirect(0, pattern(terminal.factoryId(), ""));
                    evidence.put("draftAfterBlankPattern", terminal.factoryDraft());
                    check("draft kept when a blank pattern arrives".equals(terminal.factoryDraft()),
                            "blank_pattern_keeps_draft");
                    terminal.factoryPatterns().setItemDirect(0, coded);
                    // Draft replacement is synchronous; remove the script before the next server tick.
                    terminal.factoryPatterns().setItemDirect(0, ItemStack.EMPTY);
                    phase = 4; wait = 0;
                }
                case 4 -> {
                    if (!clientHostPresent) {
                        stableClientHostTicks = 0;
                        if (wait < 400) return;
                        check(false, "client_received_terminal_block_entity");
                    }
                    if (++stableClientHostTicks < 20) return;
                    evidence.put("stableClientHostTicks", stableClientHostTicks);
                    check(clientHostPresent, "client_received_terminal_block_entity");
                    check(player.distanceToSqr(TERMINAL.getX() + 0.5, TERMINAL.getY() + 0.5,
                            TERMINAL.getZ() + 0.5) < 64, "player_remains_at_channel_scene");
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    terminal.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(terminal));
                    System.setProperty("ae2lf.probe.screenshotScreen", "FactoryEditorScreen");
                    System.setProperty("ae2lf.probe.screenshot", "channel-editor-code");
                    phase = 5; wait = 0;
                }
                case 5 -> {
                    if (wait < 40) return;
                    boolean captured = Files.isRegularFile(Path.of(System.getProperty("ae2lf.probe.reportDir"))
                            .resolve("screenshots/channel-editor-code.png"));
                    if (!captured && wait < 200) return;
                    check(captured, "native_channel_editor_captured");
                    // The enclosing background step expects the world after this optional editor view.
                    player.closeContainer();
                    phase = 6; wait = 0;
                }
                case 6 -> {
                    if (wait < 10) return;
                    // Keep the original native UI and its 6/6/0 -> 3/2/7 ledger intact. These
                    // additional real barrels belong to the same physical cable, after capture.
                    check(tagged(false), "real_owner_retained_before_must_regression");
                    level.setBlockAndUpdate(MUST_SRC, Blocks.BARREL.defaultBlockState());
                    level.setBlockAndUpdate(MUST_DST, Blocks.BARREL.defaultBlockState());
                    fill(MUST_SRC, 6);
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    var grid = terminal.factoryGrid();
                    terminal.tags.reconcile(List.of("SrcA", "SrcB", "Dst", "MustSrc", "MustDst"));
                    check(terminal.tags.tag("MustSrc", MUST_SRC.asLong(),
                            pos -> FactoryServer.contains(grid, BlockPos.of(pos))), "must_source_is_real_grid_member");
                    check(terminal.tags.tag("MustDst", MUST_DST.asLong(),
                            pos -> FactoryServer.contains(grid, BlockPos.of(pos))), "must_destination_is_real_grid_member");
                    terminal.saveChanges();
                    evidence.put("channelMustRegression", mustEvidence);
                    mustEvidence.put("status", "running");
                    mustEvidence.put("scope", "Real production FactoryJob, native barrels and native Saved codec; server-tick execution");
                    mustEvidence.put("sourcePosition", MUST_SRC.toShortString());
                    mustEvidence.put("destinationPosition", MUST_DST.toShortString());
                    mustEvidence.put("initialPhysicalIron", 6);
                    mustEvidence.put("explicitRefillIron", 0);
                    check(mustBalance(6, 0), "must_regression_exact_initial_inventory");
                    mustJob = newMustJob(CROSS_CHANNEL_EMPTY);
                    recordMustStage("crossChannelWithoutSourceBefore");
                    phase = 7; wait = 0;
                }
                case 7 -> {
                    tickMustJob();
                    recordMustStage("crossChannelWithoutSourceAfter");
                    check(mustJob.error().isEmpty() && mustJob.finished() && !mustSnapshot().pendingTransfer(),
                            "channel_1_trillion_must_does_not_block_channel_0_empty_put");
                    check(mustBalance(6, 0) && mustRemaining(1) == TRILLION,
                            "channel_0_empty_put_leaves_physical_stock_and_channel_1_quota_unchanged");
                    mustJob = newMustJob(CROSS_CHANNEL_TRANSFER);
                    recordMustStage("crossChannelWithSourceBefore");
                    phase = 8; wait = 0;
                }
                case 8 -> {
                    tickMustJob();
                    recordMustStage("crossChannelWithSourceAfter");
                    check(mustJob.error().isEmpty() && mustJob.finished() && !mustSnapshot().pendingTransfer(),
                            "channel_1_trillion_must_does_not_block_channel_0_real_transfer");
                    check(mustBalance(3, 3) && mustRemaining(1) == TRILLION && mustRemaining(0) == 0,
                            "channel_0_moves_exactly_three_without_borrowing_channel_1_quota");
                    mustJob = newMustJob(SAME_CHANNEL_MUST);
                    recordMustStage("sameChannelBefore");
                    phase = 9; wait = 0;
                }
                case 9 -> {
                    tickMustJob();
                    recordMustStage("sameChannelPartial");
                    check(mustJob.error().isEmpty() && !mustJob.finished() && !mustJob.waitingStatus().isEmpty()
                            && mustSnapshot().pendingTransfer() && mustSnapshot().transferRemaining() == 2,
                            "same_channel_must_waits_for_exact_missing_two");
                    check(mustBalance(0, 6) && mustRemaining(1) == 2,
                            "same_channel_partial_transfer_preserves_exact_inventory_and_quota");
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
                    var saved = FactoryJob.Saved.CODEC.encodeStart(ops, mustJob.save()).getOrThrow();
                    mustEvidence.put("blockedNativeCodec", saved);
                    mustJob = FactoryJob.restore(terminal, FactoryJob.Saved.CODEC.parse(ops, saved).getOrThrow());
                    recordMustStage("sameChannelRestored");
                    check(mustSnapshot().pendingTransfer() && mustSnapshot().transferRemaining() == 2
                            && mustRemaining(1) == 2 && mustBalance(0, 6),
                            "same_channel_native_codec_preserves_pending_transfer_without_replay");
                    // Reproduce only the old VM's inflated saved debt. All inventories, routes,
                    // program and every other continuation field remain the real blocked job's.
                    var legacySaved = saved.deepCopy().getAsJsonObject();
                    var continuation = com.google.gson.JsonParser.parseString(
                            legacySaved.get("continuation").getAsString()).getAsJsonObject();
                    continuation.addProperty("transferRemaining", Math.addExact(TRILLION, 2));
                    legacySaved.addProperty("continuation", continuation.toString());
                    mustEvidence.put("legacyInflatedDebtFixturePolicy",
                            "Simulated old-bug save: change only transferRemaining from 2 to 1000000000002; preserve all real stock and other Saved fields");
                    mustEvidence.put("legacyInflatedDebtNativeCodec", legacySaved);
                    var restoredContinuation = continuation.deepCopy();
                    restoredContinuation.addProperty("transferRemaining", 2);
                    var unchangedSaved = legacySaved.deepCopy();
                    unchangedSaved.addProperty("continuation", saved.getAsJsonObject().get("continuation").getAsString());
                    check(unchangedSaved.equals(saved) && restoredContinuation.equals(
                            com.google.gson.JsonParser.parseString(saved.getAsJsonObject().get("continuation").getAsString())),
                            "legacy_save_fixture_changes_only_transfer_remaining");
                    mustJob = FactoryJob.restore(terminal, FactoryJob.Saved.CODEC.parse(ops, legacySaved).getOrThrow());
                    recordMustStage("sameChannelLegacyStateRestored");
                    check(mustSnapshot().pendingTransfer() && mustSnapshot().transferRemaining() == TRILLION + 2
                            && mustRemaining(1) == 2 && mustBalance(0, 6),
                            "legacy_inflated_debt_is_reproduced_without_mutating_physical_stock");
                    phase = 10; wait = 0;
                }
                case 10 -> {
                    tickMustJob();
                    recordMustStage("sameChannelBlockedAfterRestore");
                    check(mustJob.error().isEmpty() && !mustJob.finished() && !mustJob.waitingStatus().isEmpty()
                            && mustSnapshot().pendingTransfer() && mustSnapshot().transferRemaining() == 2
                            && mustRemaining(1) == 2 && mustBalance(0, 6),
                            "same_channel_must_remains_blocked_without_stock_or_replay");
                    check(mustSnapshot().transferRemaining() == 2,
                            "legacy_inflated_debt_normalizes_to_own_channel_exact_remaining_two");
                    if (wait < 20) return;
                    mustEvidence.put("blockedServerTicksAfterRestore", wait);
                    fill(MUST_SRC, 2); // Explicit finite fixture supply, after observing the blocked job.
                    mustEvidence.put("explicitRefillIron", 2);
                    recordMustStage("sameChannelAfterPhysicalRefill");
                    check(mustBalance(2, 6), "same_channel_refill_adds_exactly_two_physical_items");
                    phase = 11; wait = 0;
                }
                case 11 -> {
                    tickMustJob();
                    recordMustStage("sameChannelCompleted");
                    check(mustJob.error().isEmpty() && mustJob.finished() && !mustSnapshot().pendingTransfer()
                            && mustSnapshot().transferRemaining() == 0 && mustRemaining(1) == 0,
                            "same_channel_must_finishes_only_after_real_refill");
                    check(mustBalance(0, 8) && !mustJob.hasBufferedResources(),
                            "must_regression_exact_conservation_six_initial_plus_two_refill_equals_eight_delivered");
                    check(containers().equals(Map.of(SRC_A.toShortString(), 3, SRC_B.toShortString(), 2,
                            DST.toShortString(), 7)), "must_regression_preserves_original_channel_balances");
                    check(tagged(false), "real_owner_and_original_tags_retained_after_must_regression");
                    mustEvidence.put("status", "passed");
                    phase = 12; wait = 0;
                }
                case 12 -> {
                    var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
                    if (!ChannelFunctionAudit.tick(terminal, SRC_A, SRC_B, DST)) return;
                    check(true, "function_channel_isolation_passed");
                    terminal.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(terminal));
                    SyntaxHighlightAudit.requested = true;
                    phase = 13; wait = 0;
                }
                case 13 -> {
                    if (!SyntaxHighlightAudit.finished && wait < 700) return;
                    check(SyntaxHighlightAudit.finished && SyntaxHighlightAudit.passed, "complete_highlighting_native_passed");
                    player.closeContainer();
                    phase = 14; wait = 0;
                }
                case 14 -> {
                    if (wait < 10) return;
                    TerminalRefreshAudit.run((FactoryBlockEntity) level.getBlockEntity(TERMINAL), SRC_A, SRC_B, DST);
                    finish("passed");
                }
                default -> throw new IllegalStateException("Unknown channel audit phase " + phase);
            }
        } catch (Throwable failure) {
            evidence.put("failure", failure.toString());
            evidence.put("stack", java.util.Arrays.toString(failure.getStackTrace()));
            finish("failed");
        }
    }

    private static FactoryJob newMustJob(String code) {
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
        return new FactoryJob(terminal, new FactoryPatternData(code, terminal.factoryId(), ItemStack.EMPTY));
    }

    private static void tickMustJob() {
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
        check(terminal != null && terminal.isolated() && FactoryServer.owner(terminal.factoryGrid()) == terminal
                && FactoryServer.contains(terminal.factoryGrid(), MUST_SRC)
                && FactoryServer.contains(terminal.factoryGrid(), MUST_DST), "must_job_has_actual_owner_and_native_endpoints");
        mustJob.tick();
    }

    private static FactoryMachine.Snapshot mustSnapshot() {
        return new com.google.gson.Gson().fromJson(mustJob.save().continuation(), FactoryMachine.Snapshot.class);
    }

    private static long mustRemaining(int channel) {
        long remaining = 0;
        for (var source : new com.google.gson.Gson().fromJson(mustJob.save().routes(), FactoryRoutes.Source[].class))
            if (source.channel() == channel) remaining = Math.addExact(remaining, source.remaining());
        return remaining;
    }

    private static int ironCount(BlockPos pos) {
        var container = (net.minecraft.world.Container) level.getBlockEntity(pos);
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!stack.is(net.minecraft.world.item.Items.IRON_INGOT))
                throw new IllegalStateException("Unexpected item in MUST fixture at " + pos.toShortString());
            count = Math.addExact(count, stack.getCount());
        }
        return count;
    }

    private static boolean mustBalance(int source, int destination) {
        return ironCount(MUST_SRC) == source && ironCount(MUST_DST) == destination;
    }

    private static void recordMustStage(String name) {
        var stage = new LinkedHashMap<String, Object>();
        stage.put("gameTime", level.getGameTime());
        stage.put("sourceIron", ironCount(MUST_SRC));
        stage.put("destinationIron", ironCount(MUST_DST));
        stage.put("finished", mustJob.finished());
        stage.put("error", mustJob.error());
        stage.put("waitingStatus", mustJob.waitingStatus());
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
        stage.put("savedJob", FactoryJob.Saved.CODEC.encodeStart(ops, mustJob.save()).getOrThrow());
        mustEvidence.put(name, stage);
    }

    private static ItemStack pattern(String factoryId, String code) {
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(code, factoryId, ItemStack.EMPTY));
        return stack;
    }

    private static void install() {
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL);
        terminal.factoryPatterns().setItemDirect(0, pattern(terminal.factoryId(), CODED));
    }

    private static boolean tagged(boolean bindTags) {
        var state = new LinkedHashMap<String, Object>();
        evidence.put("networkState", state);
        state.put("terminalChunkLoaded", level.hasChunkAt(TERMINAL));
        state.put("sourceAChunkLoaded", level.hasChunkAt(SRC_A));
        if (!(level.getBlockEntity(TERMINAL) instanceof FactoryBlockEntity terminal)
                || !(level.getBlockEntity(CABLE) instanceof FactoryBlockEntity cable)) {
            state.put("factoryBlocksPresent", false);
            return false;
        }
        state.put("factoryBlocksPresent", true);
        state.put("terminalNodeReady", terminal.getMainNode().isReady());
        state.put("cableNodeReady", cable.getMainNode().isReady());
        var grid = terminal.factoryGrid();
        var owner = FactoryServer.owner(grid);
        boolean connected = grid != null && grid == cable.factoryGrid();
        state.put("samePhysicalGrid", connected);
        state.put("terminalDisconnected", UniqueNetworkServices.disconnected(terminal));
        state.put("ownerIsTerminal", owner == terminal);
        state.put("ownerPosition", owner == null ? "none" : owner.getBlockPos().toShortString());
        state.put("terminalId", terminal.factoryId());
        state.put("ownerId", owner == null ? "none" : owner.factoryId());
        if (!connected || owner != terminal || !terminal.getMainNode().isReady()
                || !cable.getMainNode().isReady()) return false;
        if (bindTags) {
            terminal.tags.reconcile(List.of("SrcA", "SrcB", "Dst"));
            terminal.tags.tag("SrcA", SRC_A.asLong(), pos -> FactoryServer.contains(grid, BlockPos.of(pos)));
            terminal.tags.tag("SrcB", SRC_B.asLong(), pos -> FactoryServer.contains(grid, BlockPos.of(pos)));
            terminal.tags.tag("Dst", DST.asLong(), pos -> FactoryServer.contains(grid, BlockPos.of(pos)));
            terminal.saveChanges();
        }
        boolean sourceAMember = FactoryServer.contains(grid, SRC_A);
        boolean sourceBMember = FactoryServer.contains(grid, SRC_B);
        boolean destinationMember = FactoryServer.contains(grid, DST);
        state.put("sourceAMember", sourceAMember);
        state.put("sourceBMember", sourceBMember);
        state.put("destinationMember", destinationMember);
        state.put("tags", terminal.tags.snapshot());
        return sourceAMember && sourceBMember && destinationMember
                && terminal.tags.positions("SrcA").equals(java.util.Set.of(SRC_A.asLong()))
                && terminal.tags.positions("SrcB").equals(java.util.Set.of(SRC_B.asLong()))
                && terminal.tags.positions("Dst").equals(java.util.Set.of(DST.asLong()));
    }

    private static void check(boolean condition, String name) {
        evidence.put(name, condition);
        if (!condition) throw new IllegalStateException(name);
    }

    private static void fill(BlockPos pos, int count) {
        var container = (net.minecraft.world.Container) level.getBlockEntity(pos);
        for (int slot = 0; slot < count; slot++)
            container.setItem(slot, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
    }

    private static Map<String, Object> containers() {
        var result = new LinkedHashMap<String, Object>();
        for (var pos : List.of(SRC_A, SRC_B, DST)) {
            var container = (net.minecraft.world.Container) level.getBlockEntity(pos);
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++)
                if (!container.getItem(slot).isEmpty()) count += container.getItem(slot).getCount();
            result.put(pos.toShortString(), count);
        }
        return result;
    }

    private static void finish(String status) {
        evidence.put("status", status);
        evidence.put("ticks", ticks);
        evidence.put("phase", phase);
        if (!mustEvidence.isEmpty() && !"passed".equals(status)) mustEvidence.put("status", "failed");
        try {
            var directory = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            Files.createDirectories(directory.resolve("screenshots"));
            Files.writeString(directory.resolve("channel-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        accepted = "passed".equals(status);
        done = true;
        if (!Boolean.getBoolean("ae2lf.probe.fullAudit")) {
            RestartState.visualReady = false;
            RestartState.exitRequested = true;
        }
    }

    private ChannelAudit() {}
}
