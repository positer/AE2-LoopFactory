package com.example.ae2lfprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryContent;
import com.example.ae2lightoptimizer.factory.FactoryEditorMenu;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryServer;
import com.example.ae2lightoptimizer.factory.UniqueNetworkServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real-client reproduction of a bare factory network: one interface cable plus one network
 * terminal, a pattern installed through the production editor save path, then redstone
 * applied to the terminal and to the cable in turn.
 *
 * Enable with -Dae2lf.probe.minimalRedstone=true. Writes minimal-redstone-report.json.
 */
public final class MinimalRedstoneAudit {
    private static final boolean ENABLED = Boolean.getBoolean("ae2lf.probe.minimalRedstone");
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static final List<String> runtimeErrors = new ArrayList<>();
    private static final BlockPos CABLE = new BlockPos(240, 100, 240);
    private static final BlockPos TERMINAL = CABLE.south();
    private static final BlockPos CABLE2 = new BlockPos(250, 100, 240);
    private static final BlockPos TERMINAL2 = CABLE2.south();
    private static final BlockPos CABLE3 = new BlockPos(260, 100, 240);
    private static final BlockPos TERMINAL3 = CABLE3.south();
    private static final BlockPos UNION_SRC = CABLE3.north();
    private static final BlockPos UNION_DST = CABLE3.west();
    private static final BlockPos CABLE4 = new BlockPos(270, 100, 240);
    private static final BlockPos TERMINAL4 = CABLE4.south();
    private static final BlockPos WILD_SRC = CABLE4.north();
    private static final BlockPos WILD_DST = CABLE4.west();
    private static final BlockPos CABLE5 = new BlockPos(280, 100, 240);
    private static final BlockPos TERMINAL5 = CABLE5.south();
    private static final BlockPos TAG_SRC = CABLE5.north();
    private static final BlockPos TAG_DST = CABLE5.west();
    private static final BlockPos CABLE6 = new BlockPos(290, 100, 240);
    private static final BlockPos TERMINAL6 = CABLE6.south();
    private static final BlockPos BREAK_SRC = CABLE6.north();
    private static final BlockPos BREAK_DST = CABLE6.west();
    private static final String BREAK_CODE = """
            import Src,Dst
            while has minecraft:iron_ingot in Src > 0 do
                get 1 minecraft:iron_ingot from Src
                put 1 minecraft:iron_ingot into Dst
                break
            done
            """.strip();
    private static final String TAG_CODE = """
            import Src,Dst
            get #minecraft:planks from Src
            put #minecraft:planks into Dst
            done
            """.strip();
    private static final String WILD_CODE = """
            import Src,Dst
            get (minecraft:*_ingot)!(minecraft:copper_ingot) from Src
            put minecraft:?ron_ingot&minecraft:gold_ingot into Dst
            done
            """.strip();
    private static final String UNION_CODE = """
            import Src,Dst
            get minecraft:iron_ingot&minecraft:gold_ingot from Src
            put minecraft:iron_ingot&minecraft:gold_ingot into Dst
            done
            """.strip();
    private static final String CODE = "wait 20 tick\ndone";
    private static boolean done;
    private static int phase, wait, ticks, maxJobs, maxJobs2;
    private static String lastError = "";
    private static ServerLevel level;

    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || done) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        if (!server.getWorldData().getLevelName().startsWith("AE2LF-Factory-Probe-")) return;
        level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        try {
            ticks++;
            if (ticks > 3000) { evidence.put("timeout", true); finish("timeout"); return; }
            wait++;
            observe();
            switch (phase) {
                case 0 -> {
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CABLE.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(CABLE, FactoryContent.CABLE.get().defaultBlockState());
                    level.setBlockAndUpdate(TERMINAL, FactoryContent.TERMINAL.get().defaultBlockState());
                    var terminal = terminal();
                    var blank = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                    evidence.put("blank_pattern_factory_id", FactoryPatternData.get(blank).factoryId());
                    terminal.factoryPatterns().setItemDirect(0, blank);
                    // Teleport first and let the chunk reach the client before any menu is opened,
                    // otherwise the vanilla menu-open packet arrives before the block entity exists client side.
                    player.connection.teleport(CABLE.getX() + 0.5, CABLE.getY() + 1, CABLE.getZ() + 4.5, 180f, 20f);
                    phase = 1; wait = 0;
                }
                case 1 -> {
                    if (wait < 60) return;
                    var terminal = terminal();
                    topology(terminal, "after_load");
                    terminal.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(terminal));
                    phase = 2; wait = 0;
                }
                case 2 -> {
                    if (wait < 20) return;
                    var terminal = terminal();
                    evidence.put("editor_open", player.containerMenu instanceof FactoryEditorMenu);
                    evidence.put("editing_factory_present", terminal.editingFactory() != null);
                    if (player.containerMenu instanceof FactoryEditorMenu menu) {
                        menu.saveCode(CODE);
                        evidence.put("save_status", menu.status);
                    } else {
                        evidence.put("save_status", "no editor menu");
                    }
                    phase = 3; wait = 0;
                }
                case 3 -> {
                    if (wait < 10) return;
                    var terminal = terminal();
                    var installed = FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0));
                    evidence.put("installed_code", installed.code());
                    evidence.put("installed_factory_id", installed.factoryId());
                    evidence.put("terminal_factory_id", terminal.factoryId());
                    evidence.put("pattern_binding_matches", installed.factoryId().equals(terminal.factoryId()));
                    phase = 4; wait = 0;
                }
                case 4 -> {
                    maxJobs = 0;
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    evidence.put("caseA_terminal_powered", powered(TERMINAL));
                    evidence.put("caseA_cable_powered", powered(CABLE));
                    phase = 5; wait = 0;
                }
                case 5 -> {
                    if (wait < 60) return;
                    evidence.put("caseA_started_round", maxJobs > 0);
                    level.setBlockAndUpdate(TERMINAL.south(), Blocks.AIR.defaultBlockState());
                    phase = 6; wait = 0;
                }
                case 6 -> {
                    if (wait < 60) return;
                    maxJobs = 0;
                    level.setBlockAndUpdate(CABLE.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    evidence.put("caseB_terminal_powered", powered(TERMINAL));
                    evidence.put("caseB_cable_powered", powered(CABLE));
                    phase = 7; wait = 0;
                }
                case 7 -> {
                    if (wait < 60) return;
                    evidence.put("caseB_started_round", maxJobs > 0);
                    level.setBlockAndUpdate(CABLE.north(), Blocks.AIR.defaultBlockState());
                    phase = 8; wait = 0;
                }
                case 8 -> {
                    if (wait < 40) return;
                    evidence.put("runtime_errors", List.copyOf(runtimeErrors));
                    // Second bare network: cable + terminal + pattern + powered cable all in one tick,
                    // so the redstone edge exists before the 20-tick ownership election settles.
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CABLE2.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(CABLE2, FactoryContent.CABLE.get().defaultBlockState());
                    level.setBlockAndUpdate(TERMINAL2, FactoryContent.TERMINAL.get().defaultBlockState());
                    var second = (FactoryBlockEntity) level.getBlockEntity(TERMINAL2);
                    var script = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                    script.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(CODE, second.factoryId(), net.minecraft.world.item.ItemStack.EMPTY));
                    second.factoryPatterns().setItemDirect(0, script);
                    maxJobs2 = 0;
                    level.setBlockAndUpdate(CABLE2.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    evidence.put("caseC_cable_powered_before_election", powered(CABLE2));
                    phase = 9; wait = 0;
                }
                case 9 -> {
                    observeSecond();
                    if (wait < 80) return;
                    evidence.put("caseC_started_round", maxJobs2 > 0);
                    level.setBlockAndUpdate(CABLE2.north(), Blocks.AIR.defaultBlockState());
                    phase = 10; wait = 0;
                }
                case 10 -> {
                    if (wait < 20) return;
                    unionSetup();
                    phase = 11; wait = 0;
                }
                case 11 -> {
                    // The ownership election and node readiness settle first; tagging before that is
                    // rejected by the membership validator and would leave every tag empty.
                    if (wait < 60) return;
                    var third = (FactoryBlockEntity) level.getBlockEntity(TERMINAL3);
                    third.tags.reconcile(List.of("Src", "Dst"));
                    third.tags.tag("Src", UNION_SRC.asLong(),
                            pos -> FactoryServer.contains(third.factoryGrid(), BlockPos.of(pos)));
                    third.tags.tag("Dst", UNION_DST.asLong(),
                            pos -> FactoryServer.contains(third.factoryGrid(), BlockPos.of(pos)));
                    evidence.put("caseD_unionSrcTagged", third.tags.positions("Src").size());
                    evidence.put("caseD_unionDstTagged", third.tags.positions("Dst").size());
                    evidence.put("caseD_ownerIsTerminal", FactoryServer.owner(third.factoryGrid()) == third);
                    phase = 12; wait = 0;
                }
                case 12 -> {
                    if (wait < 5) return;
                    level.setBlockAndUpdate(TERMINAL3.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase = 13; wait = 0;
                }
                case 13 -> {
                    if (wait < 80) return;
                    level.setBlockAndUpdate(TERMINAL3.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("caseD_unionSrcAfter", contents(UNION_SRC));
                    evidence.put("caseD_unionDstAfter", contents(UNION_DST));
                    phase = 14; wait = 0;
                }
                case 14 -> {
                    if (wait < 20) return;
                    wildcardSetup();
                    phase = 15; wait = 0;
                }
                case 15 -> {
                    if (wait < 60) return;
                    var fourth = (FactoryBlockEntity) level.getBlockEntity(TERMINAL4);
                    fourth.tags.reconcile(List.of("Src", "Dst"));
                    fourth.tags.tag("Src", WILD_SRC.asLong(),
                            pos -> FactoryServer.contains(fourth.factoryGrid(), BlockPos.of(pos)));
                    fourth.tags.tag("Dst", WILD_DST.asLong(),
                            pos -> FactoryServer.contains(fourth.factoryGrid(), BlockPos.of(pos)));
                    evidence.put("caseE_taggedSrc", fourth.tags.positions("Src").size());
                    evidence.put("caseE_taggedDst", fourth.tags.positions("Dst").size());
                    phase = 16; wait = 0;
                }
                case 16 -> {
                    if (wait < 5) return;
                    level.setBlockAndUpdate(TERMINAL4.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase = 17; wait = 0;
                }
                case 17 -> {
                    if (wait < 80) return;
                    level.setBlockAndUpdate(TERMINAL4.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("caseE_wildSrcAfter", contents(WILD_SRC));
                    evidence.put("caseE_wildDstAfter", contents(WILD_DST));
                    phase = 18; wait = 0;
                }
                case 18 -> {
                    if (wait < 20) return;
                    tagSetup();
                    phase = 19; wait = 0;
                }
                case 19 -> {
                    if (wait < 60) return;
                    var fifth = (FactoryBlockEntity) level.getBlockEntity(TERMINAL5);
                    fifth.tags.reconcile(List.of("Src", "Dst"));
                    fifth.tags.tag("Src", TAG_SRC.asLong(),
                            pos -> FactoryServer.contains(fifth.factoryGrid(), BlockPos.of(pos)));
                    fifth.tags.tag("Dst", TAG_DST.asLong(),
                            pos -> FactoryServer.contains(fifth.factoryGrid(), BlockPos.of(pos)));
                    evidence.put("caseF_taggedSrc", fifth.tags.positions("Src").size());
                    evidence.put("caseF_taggedDst", fifth.tags.positions("Dst").size());
                    phase = 20; wait = 0;
                }
                case 20 -> {
                    if (wait < 5) return;
                    level.setBlockAndUpdate(TERMINAL5.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase = 21; wait = 0;
                }
                case 21 -> {
                    if (wait < 80) return;
                    level.setBlockAndUpdate(TERMINAL5.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("caseF_tagSrcAfter", contents(TAG_SRC));
                    evidence.put("caseF_tagDstAfter", contents(TAG_DST));
                    phase = 22; wait = 0;
                }
                case 22 -> {
                    if (wait < 20) return;
                    breakSetup();
                    phase = 23; wait = 0;
                }
                case 23 -> {
                    if (wait < 60) return;
                    var sixth = (FactoryBlockEntity) level.getBlockEntity(TERMINAL6);
                    sixth.tags.reconcile(List.of("Src", "Dst"));
                    sixth.tags.tag("Src", BREAK_SRC.asLong(),
                            pos -> FactoryServer.contains(sixth.factoryGrid(), BlockPos.of(pos)));
                    sixth.tags.tag("Dst", BREAK_DST.asLong(),
                            pos -> FactoryServer.contains(sixth.factoryGrid(), BlockPos.of(pos)));
                    evidence.put("caseJ_taggedSrc", sixth.tags.positions("Src").size());
                    evidence.put("caseJ_taggedDst", sixth.tags.positions("Dst").size());
                    phase = 24; wait = 0;
                }
                case 24 -> {
                    if (wait < 5) return;
                    level.setBlockAndUpdate(TERMINAL6.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase = 25; wait = 0;
                }
                case 25 -> {
                    if (wait < 80) return;
                    level.setBlockAndUpdate(TERMINAL6.south(), Blocks.AIR.defaultBlockState());
                    evidence.put("caseJ_srcAfter", contents(BREAK_SRC));
                    evidence.put("caseJ_dstAfter", contents(BREAK_DST));
                    evidence.put("caseJ_error", ((FactoryBlockEntity) level.getBlockEntity(TERMINAL6)).executionError());
                    phase = 26; wait = 0;
                }
                case 26 -> {
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

    private static FactoryBlockEntity terminal() {
        return level.getBlockEntity(TERMINAL) instanceof FactoryBlockEntity terminal ? terminal : null;
    }

    private static boolean powered(BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    private static void topology(FactoryBlockEntity terminal, String label) {
        evidence.put(label + "_terminal_node_active", terminal.getMainNode().isActive());
        evidence.put(label + "_terminal_grid_present", terminal.getMainNode().getGrid() != null);
        evidence.put(label + "_terminal_isolated", terminal.isolated());
        evidence.put(label + "_owner_is_terminal", FactoryServer.owner(terminal.factoryGrid()) == terminal);
        evidence.put(label + "_terminal_service_disconnected", UniqueNetworkServices.disconnected(terminal));
        if (level.getBlockEntity(CABLE) instanceof FactoryBlockEntity cable) {
            evidence.put(label + "_cable_grid_present", cable.getMainNode().getGrid() != null);
            evidence.put(label + "_same_grid", cable.getMainNode().getGrid() == terminal.getMainNode().getGrid());
            evidence.put(label + "_cable_service_disconnected", UniqueNetworkServices.disconnected(cable));
        } else {
            evidence.put(label + "_cable_block_entity", false);
        }
    }

    private static void observe() {
        var terminal = terminal();
        if (terminal == null) return;
        int active = terminal.activeJobCount();
        if (active > maxJobs) maxJobs = active;
        String error = terminal.executionError();
        if (!error.isEmpty() && !error.equals(lastError)) {
            lastError = error;
            runtimeErrors.add(error);
        }
    }

    private static void observeSecond() {
        if (!(level.getBlockEntity(TERMINAL2) instanceof FactoryBlockEntity terminal)) return;
        int active = terminal.activeJobCount();
        if (active > maxJobs2) maxJobs2 = active;
        String error = terminal.executionError();
        if (!error.isEmpty() && !evidence.containsKey("caseC_error")) evidence.put("caseC_error", error);
    }

    /** Real-world end-to-end check that {@code A&B} aggregates two resource ids into one set. */
    private static void unionSetup() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            level.setBlockAndUpdate(CABLE3.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(CABLE3, FactoryContent.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(TERMINAL3, FactoryContent.TERMINAL.get().defaultBlockState());
        level.setBlockAndUpdate(UNION_SRC, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(UNION_DST, Blocks.CHEST.defaultBlockState());
        var source = (net.minecraft.world.Container) level.getBlockEntity(UNION_SRC);
        source.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 8));
        source.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 8));
        source.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 4));
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL3);
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),
                new FactoryPatternData(UNION_CODE, terminal.factoryId(), net.minecraft.world.item.ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0, stack);
        evidence.put("caseD_unionSrcBefore", contents(UNION_SRC));
        evidence.put("caseD_unionDstBefore", contents(UNION_DST));
        evidence.put("caseD_unionCode", UNION_CODE);
    }

    private static Map<String, Integer> contents(BlockPos pos) {
        var result = new LinkedHashMap<String, Integer>();
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)) return result;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            result.merge(id, stack.getCount(), Integer::sum);
        }
        return result;
    }

    /** Real-world check for {@code *}, {@code ?} and parenthesised precedence in one program. */
    private static void wildcardSetup() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            level.setBlockAndUpdate(CABLE4.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(CABLE4, FactoryContent.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(TERMINAL4, FactoryContent.TERMINAL.get().defaultBlockState());
        level.setBlockAndUpdate(WILD_SRC, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(WILD_DST, Blocks.CHEST.defaultBlockState());
        var source = (net.minecraft.world.Container) level.getBlockEntity(WILD_SRC);
        source.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 6));
        source.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 6));
        source.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 6));
        source.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 2));
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL4);
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),
                new FactoryPatternData(WILD_CODE, terminal.factoryId(), net.minecraft.world.item.ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0, stack);
        evidence.put("caseE_wildCode", WILD_CODE);
        evidence.put("caseE_wildSrcBefore", contents(WILD_SRC));
        evidence.put("caseE_wildDstBefore", contents(WILD_DST));
    }

    /** Real-world check that {@code #minecraft:planks} selects every item carrying that item tag. */
    private static void tagSetup() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            level.setBlockAndUpdate(CABLE5.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(CABLE5, FactoryContent.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(TERMINAL5, FactoryContent.TERMINAL.get().defaultBlockState());
        level.setBlockAndUpdate(TAG_SRC, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(TAG_DST, Blocks.CHEST.defaultBlockState());
        var source = (net.minecraft.world.Container) level.getBlockEntity(TAG_SRC);
        source.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));
        source.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BIRCH_PLANKS, 4));
        source.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL5);
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),
                new FactoryPatternData(TAG_CODE, terminal.factoryId(), net.minecraft.world.item.ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0, stack);
        evidence.put("caseF_tagCode", TAG_CODE);
        evidence.put("caseF_tagSrcBefore", contents(TAG_SRC));
        evidence.put("caseF_tagDstBefore", contents(TAG_DST));
    }

    /** Real-world check that {@code break} leaves its loop instead of draining the whole chest. */
    private static void breakSetup() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            level.setBlockAndUpdate(CABLE6.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(CABLE6, FactoryContent.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(TERMINAL6, FactoryContent.TERMINAL.get().defaultBlockState());
        level.setBlockAndUpdate(BREAK_SRC, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(BREAK_DST, Blocks.CHEST.defaultBlockState());
        var source = (net.minecraft.world.Container) level.getBlockEntity(BREAK_SRC);
        for (int slot = 0; slot < 6; slot++)
            source.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
        var terminal = (FactoryBlockEntity) level.getBlockEntity(TERMINAL6);
        var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),
                new FactoryPatternData(BREAK_CODE, terminal.factoryId(), net.minecraft.world.item.ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0, stack);
        evidence.put("caseJ_code", BREAK_CODE);
        evidence.put("caseJ_srcBefore", contents(BREAK_SRC));
        evidence.put("caseJ_dstBefore", contents(BREAK_DST));
    }

    private static void finish(String status) {
        done = true;
        evidence.put("status", status);
        evidence.put("ticks", ticks);
        try {
            var directory = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("minimal-redstone-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        RestartState.visualReady = false;
        RestartState.exitRequested = true;
    }

    private MinimalRedstoneAudit() {}
}
