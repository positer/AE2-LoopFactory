package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.config.CondenserOutput;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import com.example.ae2lightoptimizer.block.*;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor;
import com.example.ae2lightoptimizer.storage.LoopStorageCellItem;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Isolated native fixture: assertions use exact inventories and balances, never fabricated grid edges. */
public final class MachineAudit {
    public static final boolean ENABLED = "machine_audit".equals(System.getProperty("ae2lo.probe.chain"));
    private static final BlockPos C = new BlockPos(0, 100, 0);
    private static final BlockPos T = C.above();
    private static final BlockPos R = C.west().above();
    private static final BlockPos F = T.east();
    private static final BlockPos CONTROL = C.east(4).above();
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Path output;
    private static LoopCondenserBlockEntity condenser;
    private static LoopCondenserBlockEntity offline;
    private static LoopTickerBlockEntity ticker;
    private static IGrid grid;
    public static volatile String uiAction = "";
    public static volatile boolean sliderUpdatedInput;
    public static volatile String uiLocale="", uiTitle="";
    private static int ticks;
    private static int stage;
    private static int since;
    private static double lastBalance;
    private static long lastPlanks;
    private static ItemStack marked;
    private static net.minecraft.world.entity.Entity timeWand;
    private static BlockPos torchPos;
    private static BlockPos compatTarget = C.east(7).above();
    private static final BaseActionSource SOURCE = new BaseActionSource();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Boolean> checks = new LinkedHashMap<>();

    public static void start(MinecraftServer server, ServerPlayer value, Path path) {
        level = server.overworld(); player = value; output = path;
        report.put("minecraft", "26.1.2");
        report.put("world", server.getWorldData().getLevelName());
        report.put("chain", "machine_audit");
        report.put("checks", checks);
        if (Boolean.getBoolean("ae2lo.probe.migrationCopy")) {
            var pos = new BlockPos(-3, 100, 0);
            var entity = level.getBlockEntity(pos);
            check("legacy_world_block", net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock()).toString().equals("ae2lpt:recipe_ring_solver_terminal"));
            check("legacy_world_entity", entity != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .getKey(entity.getType()).toString().equals("ae2lpt:recipe_ring_solver_terminal"));
            check("without_crystal_science", !net.neoforged.fml.ModList.get().isLoaded("ae2cs"));
        }
        if (player != null) {
            player.setGameMode(GameType.CREATIVE);
            player.connection.teleport(0.5, 103, 4.5, 180, 30);
            player.getAbilities().flying = true; player.onUpdateAbilities();
        }
        level.setBlockAndUpdate(C.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(C.west(), AEBlocks.CONTROLLER.block().defaultBlockState());
        level.setBlockAndUpdate(C, ModBlocks.LOOP_CONDENSER.get().defaultBlockState());
        level.setBlockAndUpdate(C.east(), AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(C.east());
        drive.getInternalInventory().setItemDirect(0, ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
        condenser = (LoopCondenserBlockEntity) level.getBlockEntity(C);
        level.setBlockAndUpdate(T, ModBlocks.LOOP_TICKER.get().defaultBlockState().setValue(LoopTickerBlock.FACING, Direction.EAST));
        ticker = (LoopTickerBlockEntity) level.getBlockEntity(T);
        furnace(F); furnace(CONTROL);
        level.setBlockAndUpdate(R, ModBlocks.LOOP_TICKER.get().defaultBlockState().setValue(LoopTickerBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(R.south().below(), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(R.south(), Blocks.WHEAT.defaultBlockState());
        // Isolate the condenser's own channel/connection overhead from ordinary downstream devices.
        var freePos = C.north(8);
        level.setBlockAndUpdate(freePos.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(freePos.west(), AEBlocks.CONTROLLER.block().defaultBlockState());
        level.setBlockAndUpdate(freePos, ModBlocks.LOOP_CONDENSER.get().defaultBlockState());
        var crystalTicker = C.west(2).above();
        level.setBlockAndUpdate(crystalTicker, ModBlocks.LOOP_TICKER.get().defaultBlockState().setValue(LoopTickerBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(crystalTicker.west(), AEBlocks.FLAWLESS_BUDDING_QUARTZ.block().defaultBlockState());
        ProbeState.phase = "machine_audit";
    }

    public static void tick() {
        if (ProbeState.finished) return;
        try {
            if (++ticks > 1800) throw new IllegalStateException("Timeout stage=" + stage
                    + " node=" + condenser.mainNode().getNode() + " grid=" + condenser.mainNode().getGrid());
            since++;
            switch (stage) {
                case 0 -> {
                    if (ticks < 80 || !condenser.mainNode().isActive() || !ticker.getMainNode().isActive()) return;
                    grid = condenser.mainNode().getGrid();
                    var node = condenser.mainNode().getNode();
                    testMachineRecipes();
                    check("condenser_physically_joined", node.getConnections().size() > 0
                            && ticker.getMainNode().getGrid() == grid);
                    int downstream = 0;
                    for (var edge : node.getConnections()) {
                        if (((appeng.me.GridConnection)edge).getControllerRoute() == node) downstream += edge.getUsedChannels();
                    }
                    report.put("condenserTotalRoutedChannels", node.getUsedChannels());
                    report.put("condenserDownstreamChannels", downstream);
                    check("condenser_one_channel", node.getUsedChannels() - downstream == 1);
                    check("condenser_zero_idle", node.getIdlePowerUsage() == 0);
                    var freeCondenser = (LoopCondenserBlockEntity)level.getBlockEntity(C.north(8));
                    var powerField = appeng.me.service.PathingService.class.getDeclaredField("channelPowerUsage"); powerField.setAccessible(true);
                    double nativeChannelPower = powerField.getDouble(freeCondenser.mainNode().getGrid().getPathingService());
                    report.put("isolatedCondenserChannelPower", nativeChannelPower);
                    check("one_channel_without_channel_energy", freeCondenser.mainNode().isActive()
                            && freeCondenser.mainNode().getNode().getUsedChannels() == 1 && nativeChannelPower == 0);
                    var nativeMenu = new appeng.menu.implementations.CondenserMenu(91, player.getInventory(), condenser);
                    marked = new ItemStack(ModItems.LOOP_STORAGE_CELL_64K.get());
                    check("native_template_slot_accepts_loop_disk", nativeMenu.getSlots(appeng.menu.SlotSemantics.STORAGE_CELL).get(0).mayPlace(marked));
                    condenser.getInternalInventory().setItemDirect(2, marked);
                    condenser.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS);
                    insert(Items.OAK_PLANKS, 1025);
                    next();
                }
                case 1 -> {
                    if (since < 15) return;
                    check("unmarked_no_network_behavior", count(Items.OAK_PLANKS) == 1025 && count(AEItems.MATTER_BALL.asItem()) == 0);
                    ((LoopStorageCellItem)marked.getItem()).getConfigInventory(marked)
                            .setStack(0, GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS)));
                    next();
                }
                case 2 -> {
                    long planks = count(Items.OAK_PLANKS);
                    check("marked_pulls_next_tick", since == 1 && planks == 0);
                    check("same_tick_native_quota_output", count(AEItems.MATTER_BALL.asItem()) == 4);
                    check("remainder_retained", condenser.getStoredPower() == 1);
                    report.put("firstSettlementDelayTicks", since);
                    report.put("firstSettlementBalance", condenser.getStoredPower());
                    benchmarkCondenser();
                    // Force network output rejection using a partitioned destination cell.
                    var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(C.east());
                    drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
                    var restricted = new ItemStack(ModItems.LOOP_STORAGE_CELL_64K.get());
                    ((LoopStorageCellItem)restricted.getItem()).getConfigInventory(restricted)
                            .setStack(0, GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS)));
                    drive.getInternalInventory().setItemDirect(0, restricted);
                    next();
                }
                case 3 -> {
                    if (since < 10) return;
                    insert(Items.OAK_PLANKS, 511); next();
                }
                case 4 -> {
                    if (since < 10) return;
                    check("rejected_output_keeps_credit", count(Items.OAK_PLANKS) == 0 && condenser.getStoredPower() == 512);
                    var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(C.east());
                    drive.getInternalInventory().setItemDirect(0, ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
                    next();
                }
                case 5 -> {
                    if (since < 15) return;
                    check("backpressure_retries_without_loss", condenser.getStoredPower() == 0 && count(AEItems.MATTER_BALL.asItem()) == 2);
                    extract(AEItems.MATTER_BALL.asItem());
                    ((LoopStorageCellItem)marked.getItem()).getUpgrades(marked).addItems(AEItems.INVERTER_CARD.stack());
                    insert(Items.OAK_PLANKS, 33); insert(Items.COBBLESTONE, 256); next();
                }
                case 6 -> {
                    if (since < 10) return;
                    check("inversion_keeps_marked_items", count(Items.OAK_PLANKS) == 33);
                    check("inversion_consumes_unmarked_items", count(Items.COBBLESTONE) == 0);
                    // Detach the condenser only; input handling stays native while disconnected.
                    BlockPos offlinePos = C.north(5);
                    level.setBlockAndUpdate(offlinePos, ModBlocks.LOOP_CONDENSER.get().defaultBlockState());
                    offline = (LoopCondenserBlockEntity) level.getBlockEntity(offlinePos);
                    var offlineCell = marked.copy();
                    ((LoopStorageCellItem)offlineCell.getItem()).getUpgrades(offlineCell).clear();
                    offline.getInternalInventory().setItemDirect(2, offlineCell);
                    offline.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS);
                    lastBalance = offline.getStoredPower(); lastPlanks = count(Items.OAK_PLANKS);
                    offline.getExternalInv().insertItem(0, new ItemStack(Items.STONE,17),false);
                    check("offline_native_input_works", offline.getStoredPower() == lastBalance + 17);
                    double offlineBalance = offline.getStoredPower();
                    int offlineOutput = offline.getInternalInventory().getStackInSlot(1).getCount();
                    for (int i=0; i<4; i++) offline.getExternalInv().insertItem(0, new ItemStack(Items.STONE,64),false);
                    check("offline_native_output_works", offline.getStoredPower() == offlineBalance
                            && offline.getInternalInventory().getStackInSlot(1).getCount() == offlineOutput + 1);
                    next();
                }
                case 7 -> {
                    if (since < 10) return;
                    check("offline_network_pull_stops", count(Items.OAK_PLANKS) == lastPlanks);
                    check("offline_no_physical_connections", offline.mainNode().getNode().getConnections().isEmpty());
                    testRates(); startCompat(); next();
                }
                case 8 -> {
                    if (ticks < 420) return;
                    int normal = ((AbstractFurnaceBlockEntity)level.getBlockEntity(CONTROL)).getItem(2).getCount();
                    int fast = ((AbstractFurnaceBlockEntity)level.getBlockEntity(F)).getItem(2).getCount();
                    report.put("normalFurnaceOutput", normal); report.put("acceleratedFurnaceOutput", fast);
                    check("native_progress_machine_accelerates", fast > normal && normal > 0);
                    if (timeWand != null) {
                        int combined = ((AbstractFurnaceBlockEntity)level.getBlockEntity(compatTarget)).getItem(2).getCount();
                        report.put("combinedJdtTorchTickerOutput", combined);
                        check("jdt_and_torcherino_combined_progress", combined > fast);
                        check("jdt_acceleration_entity_still_alive", timeWand.isAlive());
                        check("torcherino_block_entity_still_present", level.getBlockEntity(torchPos) != null);
                        var active = level.getBlockEntity(torchPos).getClass().getDeclaredField("active"); active.setAccessible(true);
                        check("real_torcherino_active", active.getBoolean(level.getBlockEntity(torchPos)));
                    }
                    check("native_random_tick_crop_matures", level.getBlockState(R.south()).getValue(CropBlock.AGE) == 7);
                    boolean crystalGrew = false;
                    for (Direction direction : Direction.values()) {
                        var state = level.getBlockState(C.west(3).above().relative(direction));
                        crystalGrew |= state.is(AEBlocks.SMALL_QUARTZ_BUD.block()) || state.is(AEBlocks.MEDIUM_QUARTZ_BUD.block())
                                || state.is(AEBlocks.LARGE_QUARTZ_BUD.block()) || state.is(AEBlocks.QUARTZ_CLUSTER.block());
                    }
                    check("native_random_tick_certus_crystal_grows", crystalGrew);
                    uiAction = "number";
                    appeng.menu.MenuOpener.open(com.example.ae2lightoptimizer.menu.LoopTickerMenu.TYPE.get(), player,
                            appeng.menu.locator.MenuLocators.forBlockEntity(ticker));
                    ProbeState.screenshotRequest = "ae2lo-ripper-machine-ticker-ui.png";
                    next();
                }
                case 9 -> {
                    if (since > 150) throw new IllegalStateException("UI packet timeout " + uiAction + " rate=" + ticker.rate());
                    if (uiAction.equals("number_sent") && ticker.rate() == 2) {
                        check("client_numeric_input_packet", true); uiAction = "slider";
                    }
                    if (uiAction.equals("slider_sent") && ticker.rate() == 4) {
                        check("client_slider_input_packet", true); check("slider_and_focused_input_share_value", sliderUpdatedInput);
                        report.put("uiLocale",uiLocale); report.put("uiTitle",uiTitle);
                        check("correct_chinese_ticker_title", uiLocale.equals("zh_cn") && uiTitle.equals("循环进刻器")); uiAction = "done"; since = 0;
                    }
                    if (!uiAction.equals("done") || since < 35) return;
                    appeng.menu.MenuOpener.open(appeng.menu.implementations.CondenserMenu.TYPE, player,
                            appeng.menu.locator.MenuLocators.forBlockEntity(condenser));
                    ProbeState.screenshotRequest = "ae2lo-ripper-machine-condenser-ui.png";
                    next();
                }
                case 10 -> {
                    if (since < 40) return;
                    if (net.neoforged.fml.ModList.get().isLoaded("ae2cs")) check("four_native_silver_sprites", crystalSpritesReady);
                    report.put("passed", checks.values().stream().allMatch(Boolean::booleanValue));
                    finish();
                }
            }
        } catch (Throwable failure) {
            report.put("failure", failure.toString()); report.put("stage", stage); report.put("passed", false); finish();
        }
    }

    public static volatile boolean crystalSpritesReady;

    private static void testMachineRecipes() {
        var old = net.minecraft.resources.Identifier.parse("ae2lightoptimizer:loop_ticker");
        var current = net.minecraft.resources.Identifier.parse("ae2lpt:loop_ticker");
        var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var entities = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE;
        check("old_addon_dependency", net.neoforged.fml.ModList.get().isLoaded("ae2lightoptimizer"));
        check("old_recipe_id", level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, old)).isPresent());
        check("old_inventory_id", items.getValue(old) == ModItems.LOOP_TICKER.get() && items.getKey(items.getValue(old)).equals(current));
        check("old_chunk_palette_id", blocks.getValue(old) == com.example.ae2lightoptimizer.block.ModBlocks.LOOP_TICKER.get());
        check("old_block_entity_id", entities.getValue(old) == entities.getValue(current) && entities.getValue(current) != null);
        String c = "ae2lpt:loop_crystal", p = "ae2:engineering_processor";
        String k = "ae2lpt:4k_loop_storage_core", l = "ae2:logic_processor";
        String block = "ae2lpt:loop_ticker", part = block + "_part";
        if (net.neoforged.fml.ModList.get().isLoaded("ae2cs")) testCrystalScience();
        verifyRecipe("loop_ticker", block, c, "ae2:speed_card", c, p, "ae2:cell_workbench", p, "", "ae2:export_bus", "");
        verifyRecipe("loop_condenser", "ae2lpt:loop_condenser", c, k, c, l, "ae2:condenser", l, c, k, c);
        verifyRecipe("loop_ticker_part_from_loop_ticker", part, block);
        verifyRecipe("loop_ticker_from_loop_ticker_part", block, part);
    }

    private static void testCrystalScience() {
        CrystalScienceAudit.run(level, MachineAudit::check);
        verifyRecipe("crystal_science/loop_crystal_seed", "ae2lpt:loop_crystal_seed",
                "ae2lpt:loop_crystal_powder", "minecraft:sand");
    }

    private static void verifyRecipe(String path, String outputId, String... ingredients) {
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var stacks = new java.util.ArrayList<ItemStack>();
        for (String id : ingredients) {
            stacks.add(id.isEmpty() ? ItemStack.EMPTY : new ItemStack(registry.getValue(net.minecraft.resources.Identifier.parse(id))));
        }
        var recipe = (net.minecraft.world.item.crafting.CraftingRecipe) level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.parse("ae2lpt:" + path))).orElseThrow().value();
        int width = Math.min(ingredients.length, 3);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(width, ingredients.length / width, stacks);
        check("recipe_matches_" + path, recipe.matches(input, level));
        var result = recipe.assemble(input);
        check("recipe_result_" + path, result.getCount() == (path.equals("crystal_science/loop_crystal_seed") ? 2 : 1) && registry.getKey(result.getItem()).toString().equals(outputId));
        if (width == 3) {
            java.util.Collections.swap(stacks, 0, 1);
            input = net.minecraft.world.item.crafting.CraftingInput.of(width, ingredients.length / width, stacks);
            check("recipe_order_" + path, !recipe.matches(input, level));
        }
    }

    // Measure the actual drive against an idle marked whitelist in native ME storage.
    private static void benchmarkCondenser() throws Exception {
        var drive = com.example.ae2lightoptimizer.integration.CondenserLoopService.class
                .getDeclaredMethod("drive", LoopCondenserBlockEntity.class);
        drive.setAccessible(true);
        var sampleItems = new java.util.ArrayList<net.minecraft.world.item.Item>();
        var storage = grid.getStorageService().getInventory();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item == Items.OAK_PLANKS || item == AEItems.MATTER_BALL.asItem()) continue;
            if (storage.insert(AEItemKey.of(item), 1, Actionable.MODULATE, SOURCE) == 1) sampleItems.add(item);
            if (sampleItems.size() == 256) break;
        }
        check("benchmark_has_256_distinct_items", sampleItems.size() == 256);
        java.lang.reflect.Method baseline = baselineDrive();
        for (boolean fuzzy : new boolean[]{false, true}) {
            var upgrades = ((LoopStorageCellItem)marked.getItem()).getUpgrades(marked);
            if (fuzzy) upgrades.addItems(AEItems.FUZZY_CARD.stack());
            String mode = fuzzy ? "fuzzy" : "exact";
            if (baseline != null) report.put(mode + "BaselineMedianNs", medianDriveNanos(baseline));
            report.put(mode + "MedianNs", medianDriveNanos(drive));
            if (fuzzy) upgrades.clear();
        }
        for (var item : sampleItems) storage.extract(AEItemKey.of(item), 1, Actionable.MODULATE, SOURCE);
    }

    // Optional A/B run. Load only the historical service and use the current native world for both.
    private static java.lang.reflect.Method baselineDrive() throws Exception {
        String path = System.getenv("AE2LF_PROBE_CONDENSER_BASELINE");
        if (path == null || path.isBlank()) return null;
        String name = com.example.ae2lightoptimizer.integration.CondenserLoopService.class.getName();
        byte[] code;
        try (var jar = new java.util.jar.JarFile(path)) {
            try (var in = jar.getInputStream(jar.getJarEntry(name.replace('.', '/') + ".class"))) {
                code = in.readAllBytes();
            }
        }
        var loader = new ClassLoader(MachineAudit.class.getClassLoader()) {
            Class<?> service() { return defineClass(name, code, 0, code.length); }
        };
        var drive = loader.service().getDeclaredMethod("drive", LoopCondenserBlockEntity.class);
        drive.setAccessible(true);
        return drive;
    }

    private static long medianDriveNanos(java.lang.reflect.Method drive) throws Exception {
        for (int i = 0; i < 120; i++) drive.invoke(null, condenser);
        long[] batches = new long[9];
        for (int batch = 0; batch < batches.length; batch++) {
            long start = System.nanoTime();
            for (int i = 0; i < 50; i++) drive.invoke(null, condenser);
            batches[batch] = (System.nanoTime() - start) / 50;
        }
        java.util.Arrays.sort(batches);
        return batches[4];
    }

    private static void testRates() throws Exception {
        check("default_maximum_four", ticker.rate() == 4 && ticker.maxRate() == 4);
        for (int i=0; i<4; i++) ticker.getUpgrades().setItemDirect(i, AEItems.SPEED_CARD.stack());
        check("default_follows_max_with_cards", ticker.rate() == 64);
        var drops = new java.util.ArrayList<ItemStack>(); ticker.addAdditionalDrops(level,T,drops);
        check("all_four_cards_returned_on_dismantle", drops.stream().filter(stack -> stack.is(AEItems.SPEED_CARD.asItem())).mapToInt(ItemStack::getCount).sum() == 4);
        for (int i=0; i<4; i++) ticker.getUpgrades().setItemDirect(i,ItemStack.EMPTY);
        ticker.setRate(3);
        for (int i = 0; i < 4; i++) ticker.getUpgrades().setItemDirect(i, AEItems.SPEED_CARD.stack());
        check("four_cards_double_each", ticker.maxRate() == 64 && ticker.rate() == 48);
        for (int i = 0; i < 4; i++) ticker.getUpgrades().setItemDirect(i, ItemStack.EMPTY);
        check("remove_cards_scales_and_remembers", ticker.rate() == 3 && ticker.maxRate() == 4);
        var problems = new net.minecraft.util.ProblemReporter.Collector();
        var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(problems, level.registryAccess());
        ticker.saveAdditional(saved);
        var restored = new LoopTickerBlockEntity(T, ticker.getBlockState());
        restored.loadTag(net.minecraft.world.level.storage.TagValueInput.create(problems, level.registryAccess(), saved.buildResult()));
        check("rate_persists_native_nbt", restored.rate() == 3 && problems.isEmpty());
        ticker.setRate(0); check("rate_lower_bound", ticker.rate() == 1);
        ticker.setRate(999); check("rate_upper_bound", ticker.rate() == 4);
        var menu = new com.example.ae2lightoptimizer.menu.LoopTickerMenu(
                com.example.ae2lightoptimizer.menu.LoopTickerMenu.TYPE.get(), 92, player.getInventory(), ticker);
        check("four_native_upgrade_slots", menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).size() == 4);
        check("wrong_upgrade_rejected", !menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).get(0).mayPlace(AEItems.INVERTER_CARD.stack()));
        menu.clickMenuButton(player, 2); check("server_rate_action", ticker.rate() == 2); ticker.setRate(4);
    }
    private static void startCompat() throws Exception {
        boolean jdt = net.neoforged.fml.ModList.get().isLoaded("justdirethings");
        boolean torch = net.neoforged.fml.ModList.get().isLoaded("torcherino");
        report.put("compatMods", Map.of("justdirethings", jdt, "torcherino", torch));
        if (!jdt && !torch) return;
        check("both_compat_mods_loaded", jdt && torch);
        furnace(compatTarget);
        // Dedicated combined machine: own loop ticker, a native JDT time entity and real torch.
        BlockPos extraTickerPos = compatTarget.above();
        level.setBlockAndUpdate(extraTickerPos, ModBlocks.LOOP_TICKER.get().defaultBlockState().setValue(LoopTickerBlock.FACING, Direction.DOWN));
        // An actual second physical network; no synthetic GridConnection is constructed.
        level.setBlockAndUpdate(extraTickerPos.west(), AEBlocks.CONTROLLER.block().defaultBlockState());
        level.setBlockAndUpdate(extraTickerPos.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var type = Class.forName("com.direwolf20.justdirethings.common.entities.TimeWandEntity");
        timeWand = (net.minecraft.world.entity.Entity) type.getConstructor(net.minecraft.world.level.Level.class, BlockPos.class).newInstance(level, compatTarget);
        type.getMethod("setTickSpeed", int.class).invoke(timeWand, 2);
        type.getMethod("setTotalTime", int.class).invoke(timeWand, 2000);
        type.getMethod("setRemainingTime", int.class).invoke(timeWand, 2000);
        check("real_jdt_time_entity_added", level.addFreshEntity(timeWand));
        torchPos = compatTarget.south();
        level.setBlockAndUpdate(torchPos.below(), Blocks.STONE.defaultBlockState());
        var torchBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("torcherino", "torcherino"));
        level.setBlockAndUpdate(torchPos, torchBlock.defaultBlockState());
        var torchEntity = level.getBlockEntity(torchPos);
        check("real_torcherino_placed", torchEntity != null && torchEntity.getClass().getName().contains("TorcherinoBlockEntity"));
        Object configured = torchEntity.getClass().getMethod("readClientData", int.class, int.class, int.class, int.class, int.class)
                .invoke(torchEntity, 1, 1, 1, 1, 0);
        check("real_torcherino_configuration_accepted", Boolean.TRUE.equals(configured));
    }

    private static void next() {
        stage++; since=0;
        report.put("stage", stage);
        try { Files.writeString(output.resolve("progress.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void check(String name, boolean value) { checks.put(name, value); if (!value) throw new IllegalStateException(name); }
    private static void furnace(BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var furnace = (AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64)); furnace.setItem(1, new ItemStack(Items.COAL, 16));
    }
    private static long count(net.minecraft.world.item.Item item) { return grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(item)); }
    private static void insert(net.minecraft.world.item.Item item, long amount) { check("insert_"+ticks+"_"+item, grid.getStorageService().getInventory().insert(AEItemKey.of(item), amount, Actionable.MODULATE, SOURCE) == amount); }
    private static void extract(net.minecraft.world.item.Item item) { grid.getStorageService().getInventory().extract(AEItemKey.of(item), Long.MAX_VALUE, Actionable.MODULATE, SOURCE); }
    private static void finish() {
        try { Files.writeString(output.resolve("report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception e) { throw new RuntimeException(e); }
        ProbeState.status = report.toString(); ProbeState.phase = Boolean.TRUE.equals(report.get("passed")) ? "passed" : "failed"; ProbeState.finished = true;
    }
}
