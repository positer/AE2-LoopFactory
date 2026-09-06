package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.block.RecipeRingSolverTerminalBlockEntity;
import com.example.ae2lightoptimizer.block.SupercomputingCraftingOptimizerInterfaceBlockEntity;
import com.example.ae2lightoptimizer.integration.CraftingExecutionSchedule;
import com.example.ae2lightoptimizer.integration.CraftingRipperExecutor;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingPlan;
import com.example.ae2lightoptimizer.item.ModItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** Separate acceptance-test mod. It never replaces the production planner, storage or executor. */
@Mod("ae2lo_runtime_probe")
public final class RuntimeRipperProbe {
    private static final long ORDER = Core256MFixture.ENABLED ? Core256MFixture.ORDER : 1_000_000L;
    private static final long RAW = 3_000_000_000L;
    private static final boolean LAYERED_CHAIN = "layered".equals(System.getProperty("ae2lo.probe.chain", "template"));
    private static final boolean SMITHING_CHAIN = LAYERED_CHAIN
            || "smithing".equals(System.getProperty("ae2lo.probe.chain", "template"));
    private static final int CPU_WIDTH = Core256MFixture.ENABLED ? 15 : LAYERED_CHAIN ? 5 : 4;
    private static final int CPU_HEIGHT = Core256MFixture.ENABLED ? 15 : 4;
    private static final int CPU_DEPTH = Core256MFixture.ENABLED ? 15 : 4;
    private static final int CPU_BLOCKS = CPU_WIDTH * CPU_HEIGHT * CPU_DEPTH;
    private static final long CPU_CAPACITY = CPU_BLOCKS * 262144L;
    private static final BlockPos CPU = new BlockPos(0, 100, 0);
    private static final BlockPos DRIVE = new BlockPos(-1, 100, 0);
    private static final BlockPos RIPPER = new BlockPos(-2, 100, 0);
    private static final BlockPos RING = new BlockPos(-3, 100, 0);
    private static final BlockPos OPTIMIZER = new BlockPos(-4, 100, 0);
    private static final BlockPos ENERGY = new BlockPos(-1, 100, 1);
    private static final int ENERGY_CELLS = Core256MFixture.ENABLED ? 3 : 1;
    private static final double ENERGY_LOAD = Core256MFixture.ENABLED ? 4_500_000D : 1_500_000D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson JSONL = new Gson();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static ServerLevel level;
    private static ServerPlayer player;
    private static IGrid grid;
    private static CraftingCPUCluster cpu;
    private static CraftingRipperBlockEntity ripper;
    private static Future<ICraftingPlan> future;
    private static long stateTick;
    private static long startedTick;
    private static long submitTick;
    private static int run;
    private static int cpuCalls;
    private static int ripCalls;
    private static long growthApplications;
    private static long smithingApplications;
    private static long plankApplications;
    private static long stickApplications;
    private static long toolApplications;
    private static double energyBefore;
    private static double measuredFee;
    private static long executionTick;
    private static long executionStartedNanos;
    private static long executionNanos;
    private static boolean sameExecutionTick;
    private static boolean executionCompleted;
    private static boolean cpuFinished;
    private static boolean powerInjected;
    private static Path output;
    private static Map<String, Long> beforeStock;

    public RuntimeRipperProbe() {
        NeoForge.EVENT_BUS.addListener(RuntimeRipperProbe::onPlayer);
        NeoForge.EVENT_BUS.addListener(RuntimeRipperProbe::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RuntimeRipperProbe::onTick);
    }

    private static void onPlayer(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("ae2lo.probe") && event.getEntity() instanceof ServerPlayer serverPlayer
                && serverPlayer.level().getServer().getWorldData().getLevelName().startsWith("AE2LO-Ripper-Probe-")) {
            start(serverPlayer.level().getServer(), serverPlayer);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("ae2lo.probe.auto")) start(event.getServer(), null);
    }

    public static void start(MinecraftServer server, ServerPlayer probePlayer) {
        if (level != null) return;
        try {
            level = server.overworld();
            player = probePlayer;
            output = Path.of(System.getProperty("ae2lo.probe.reportDir",
                    System.getProperty("ae2lo.probe.output", "ae2lo-probe-evidence"))).toAbsolutePath();
            Files.createDirectories(output);
            report.put("probe", "real-serverlevel-ae2-grid-ring-whole-job-ripper");
            report.put("minecraft", "26.1.2");
            report.put("orderPerRun", ToolComponentFixture.ENABLED ? 12 : ExactComponentFixture.ENABLED ? 3000 : NativeCatalogFixture.ENABLED ? 3 : CatalogAuditFixture.ENABLED || PackRecipeReloadFixture.ENABLED ? 0 : ORDER);
            report.put("chain", ExactComponentFixture.ENABLED ? "exact_components"
                    : ToolComponentFixture.ENABLED ? "tool_components"
                    : PackRecipeReloadFixture.ENABLED ? "pack_catalog"
                    : NativeCatalogFixture.ENABLED ? "native_catalog_map_extension"
                    : CatalogAuditFixture.ENABLED ? "catalog_audit"
                    : Core256MFixture.ENABLED ? "all_ten_core_tiers_crystal_growth_and_netherite_ingots"
                    : LAYERED_CHAIN ? "logs_planks_sticks_tool_and_template_growth_then_smithing"
                    : SMITHING_CHAIN ? "template_growth_then_netherite_pickaxe_smithing" : "template_growth");
            if (!ToolComponentFixture.ENABLED && !ExactComponentFixture.ENABLED && !NativeCatalogFixture.ENABLED && !CatalogAuditFixture.ENABLED && !PackRecipeReloadFixture.ENABLED) report.put("initialRawStockPerKey", RAW);
            report.put("cpuStorageBlocks", CPU_BLOCKS);
            report.put("capacityOverride", false);
            report.put("finiteDenseEnergyCells", ENERGY_CELLS);
            report.put("energyMeasurement", "Public extractAEPower(1e9, SIMULATE, ONE) before/after production execute");
            report.put("world", server.getWorldData().getLevelName());
            startedTick = tick();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
            for (int x = -9; x <= Math.max(8, CPU_WIDTH + 1); x++) for (int z = -5; z <= Math.max(12, CPU_DEPTH + 1); z++) {
                level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
            for (int x = 0; x < CPU_WIDTH; x++) for (int y = 0; y < CPU_HEIGHT; y++) for (int z = 0; z < CPU_DEPTH; z++) {
                level.setBlockAndUpdate(CPU.offset(x, y, z), AEBlocks.CRAFTING_STORAGE_256K.block().defaultBlockState());
            }
            level.setBlockAndUpdate(DRIVE, AEBlocks.DRIVE.block().defaultBlockState());
            for (int z = 0; z < ENERGY_CELLS; z++) {
                level.setBlockAndUpdate(ENERGY.offset(0, 0, z), AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
            }
            level.setBlockAndUpdate(RIPPER, ModBlocks.CRAFTING_RIPPER.get().defaultBlockState());
            level.setBlockAndUpdate(RING, ModBlocks.RECIPE_RING_SOLVER_TERMINAL.get().defaultBlockState());
            if (Core256MFixture.ENABLED) level.setBlockAndUpdate(OPTIMIZER,
                    ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get().defaultBlockState());
            var drive = (DriveBlockEntity) level.getBlockEntity(DRIVE);
            drive.getInternalInventory().setItemDirect(0, ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
            if (player != null) {
                player.setGameMode(GameType.CREATIVE);
                player.connection.teleport(-6.0, 103.0, 7.0, -150.0f, 16.0f);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            phase("network_boot");
            event("setup", Map.of("cpuBlocks", CPU_BLOCKS, "physicalCapacity", CPU_CAPACITY));
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (level == null || ProbeState.finished) return;
        try {
            if (tick() - startedTick > 2400) throw new IllegalStateException("Probe timeout in " + ProbeState.phase);
            switch (ProbeState.phase) {
                case "network_boot" -> bootNetwork();
                case "tool_components" -> {
                    ToolComponentFixture.tick();
                    if (ToolComponentFixture.isFinished()) {
                        report.put("passed", ToolComponentFixture.passed());
                        report.put("toolComponents", ToolComponentFixture.summary());
                        ProbeState.status = "Tool components: " + ToolComponentFixture.summary();
                        ProbeState.phase = ToolComponentFixture.passed() ? "passed" : "failed";
                        ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
                        writeReport();
                        ProbeState.finished = true;
                        if (player != null) player.sendSystemMessage(Component.literal(ProbeState.status));
                    }
                }
                case "pack_catalog" -> {
                    PackRecipeReloadFixture.tick();
                    if (PackRecipeReloadFixture.isFinished()) {
                        report.put("passed", PackRecipeReloadFixture.passed());
                        report.put("packCatalog", PackRecipeReloadFixture.summary());
                        ProbeState.status = "Pack recipes: " + PackRecipeReloadFixture.summary();
                        ProbeState.phase = PackRecipeReloadFixture.passed() ? "passed" : "failed";
                        ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
                        writeReport();
                        ProbeState.finished = true;
                        if (player != null) player.sendSystemMessage(Component.literal(ProbeState.status));
                    }
                }
                case "exact_components" -> {
                    ExactComponentFixture.tick();
                    if (ExactComponentFixture.isFinished()) {
                        report.put("passed", ExactComponentFixture.passed());
                        report.put("exactComponents", ExactComponentFixture.summary());
                        ProbeState.status = "Exact components: " + ExactComponentFixture.summary();
                        ProbeState.phase = ExactComponentFixture.passed() ? "passed" : "failed";
                        ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
                        writeReport();
                        ProbeState.finished = true;
                        if (player != null) player.sendSystemMessage(Component.literal(ProbeState.status));
                    }
                }
                case "native_catalog" -> {
                    NativeCatalogFixture.tick();
                    if (NativeCatalogFixture.isFinished()) {
                        report.put("passed", NativeCatalogFixture.passed() && NativeContinuationAssertions.passed());
                        report.put("nativeCatalog", NativeCatalogFixture.summary());
                        report.put("nativeContinuation", NativeContinuationAssertions.summary());
                        ProbeState.status = "Native catalog: " + NativeCatalogFixture.summary();
                        ProbeState.phase = NativeCatalogFixture.passed() && NativeContinuationAssertions.passed()
                                ? "passed" : "failed";
                        ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
                        writeReport();
                        ProbeState.finished = true;
                        if (player != null) player.sendSystemMessage(Component.literal(ProbeState.status));
                    }
                }
                case "catalog_audit" -> {
                    CatalogAuditFixture.tick();
                    if (CatalogAuditFixture.isFinished()) {
                        report.put("passed", CatalogAuditFixture.passed());
                        report.put("catalogAudit", CatalogAuditFixture.summary());
                        ProbeState.status = "Catalog audit: " + CatalogAuditFixture.summary();
                        ProbeState.phase = CatalogAuditFixture.passed() ? "passed" : "failed";
                        ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
                        writeReport();
                        ProbeState.finished = true;
                        if (player != null) player.sendSystemMessage(Component.literal(ProbeState.status));
                    }
                }
                case "stock_settling" -> {
                    if (tick() - stateTick >= 30) {
                        ProbeState.screenshotRequest = "ae2lo-ripper-before.png";
                        phase("before_screenshot");
                    }
                }
                case "before_screenshot" -> {
                    if (player == null || "ae2lo-ripper-before.png".equals(ProbeState.screenshotCompleted)
                            || tick() - stateTick > 160) beginRun(1);
                }
                case "planning" -> pollPlan();
                case "submitted" -> {
                    if (cpuFinished) completeRun();
                    else if (tick() - submitTick > 10) throw new IllegalStateException("Job did not finish within ten ticks");
                }
                case "second_settling" -> {
                    if (tick() - stateTick >= 30) beginRun(2);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void bootNetwork() {
        var drive = (DriveBlockEntity) level.getBlockEntity(DRIVE);
        ripper = (CraftingRipperBlockEntity) level.getBlockEntity(RIPPER);
        var craftingBlock = (CraftingBlockEntity) level.getBlockEntity(CPU);
        var node = drive.getMainNode().getNode();
        if (node == null || craftingBlock.getCluster() == null) return;
        grid = node.getGrid();
        cpu = craftingBlock.getCluster();
        if (!powerInjected) {
            if (Core256MFixture.ENABLED) {
                double capacity = 0;
                for (int z = 0; z < ENERGY_CELLS; z++) {
                    if (!(level.getBlockEntity(ENERGY.offset(0, 0, z)) instanceof EnergyCellBlockEntity cell)) return;
                    var cellNode = cell.getMainNode().getNode();
                    if (cellNode == null || cellNode.getGrid() != grid) return;
                    capacity += cell.getAEMaxPower();
                }
                if (capacity < ENERGY_LOAD || grid.getEnergyService().injectPower(ENERGY_LOAD, Actionable.SIMULATE) > 0) return;
                report.put("connectedFiniteCellCapacityAE", capacity);
            }
            double accepted = ENERGY_LOAD - grid.getEnergyService().injectPower(ENERGY_LOAD, Actionable.MODULATE);
            if (accepted <= 0) return;
            if (Core256MFixture.ENABLED) check(accepted == ENERGY_LOAD, "Finite core fixture cells did not accept the full initial charge");
            powerInjected = true;
            event("power_loaded", Map.of("acceptedAE", accepted, "finiteDenseCells", ENERGY_CELLS));
        }
        if (!cpu.isActive() || !ripper.getMainNode().isActive()
                || grid.getActiveMachines(RecipeRingSolverTerminalBlockEntity.class).isEmpty()) return;
        if (cpu.getGrid() != grid || ripper.getMainNode().getGrid() != grid) {
            throw new IllegalStateException("Machines are not on the same physical grid");
        }
        if (Core256MFixture.ENABLED) {
            var optimizer = (SupercomputingCraftingOptimizerInterfaceBlockEntity) level.getBlockEntity(OPTIMIZER);
            var ring = (RecipeRingSolverTerminalBlockEntity) level.getBlockEntity(RING);
            var optimizerNode = optimizer.getGridNode(net.minecraft.core.Direction.UP);
            var ringNode = ring.getGridNode(net.minecraft.core.Direction.UP);
            if (optimizerNode == null || ringNode == null || !optimizerNode.isActive() || !ringNode.isActive()) return;
            check(optimizerNode.getGrid() == grid && ringNode.getGrid() == grid,
                    "Both planner services must be on the Ripper's real grid");
            report.put("bothPlannerServicesOnline", true);
        }
        check(cpu.getAvailableStorage() == CPU_CAPACITY, "Unexpected physical CPU capacity");
        check(ripper.getMainNode().getNode().getIdlePowerUsage() == 5.0, "Ripper's own idle draw differs from 5 AE/t");
        report.put("ripperConfiguredIdleAEPerTick", ripper.getMainNode().getNode().getIdlePowerUsage());
        if (ToolComponentFixture.ENABLED) {
            ToolComponentFixture.begin(level, grid, ripper, output);
            phase("tool_components");
            return;
        }
        if (PackRecipeReloadFixture.ENABLED) {
            PackRecipeReloadFixture.begin(level, grid, ripper, output);
            phase("pack_catalog");
            return;
        }
        if (ExactComponentFixture.ENABLED) {
            ExactComponentFixture.begin(level, grid, ripper, output);
            phase("exact_components");
            return;
        }
        if (NativeCatalogFixture.ENABLED) {
            NativeCatalogFixture.begin(level, grid, ripper, output);
            phase("native_catalog");
            return;
        }
        if (CatalogAuditFixture.ENABLED) {
            report.put("optionalSmithing", OptionalSmithingFixture.run(level));
            CatalogAuditFixture.begin(level, grid, ripper, output);
            phase("catalog_audit");
            return;
        }
        if (Core256MFixture.ENABLED) {
            report.put("coreFixture", Core256MFixture.prepare(grid, ripper));
            report.put("physicalCpuBytes", cpu.getAvailableStorage());
            event("network_ready", Map.of("patterns", ripper.getLogic().getAvailablePatterns().size(),
                    "stock", stock(grid.getStorageService().getInventory().getAvailableStacks()),
                    "networkIdleAEPerTick", grid.getEnergyService().getIdlePowerUsage(),
                    "ripperIdleAEPerTick", ripper.getMainNode().getNode().getIdlePowerUsage(),
                    "services", Map.of("ringTerminal", true, "optimizerInterface", true, "ripper", true)));
            phase("stock_settling");
            return;
        }
        var ingredients = new ItemStack[] {
                new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERRACK), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND) };
        var input = CraftingInput.of(3, 3, Arrays.asList(ingredients));
        var holder = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var result = holder.value().assemble(input);
        check(result.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) && result.getCount() == 2,
                "Native template growth recipe is different");
        var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, ingredients, result, false, false);
        ripper.getLogic().getPatternInv().setItemDirect(0, encoded);
        if (SMITHING_CHAIN) {
            var smithingInput = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_INGOT));
            var smithing = level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithingInput, level).orElseThrow();
            var smithingResult = smithing.value().assemble(smithingInput);
            check(smithingResult.is(Items.NETHERITE_PICKAXE) && smithingResult.getCount() == 1,
                    "Native netherite pickaxe smithing recipe differs");
            var smithingPattern = PatternDetailsHelper.encodeSmithingTablePattern(smithing,
                    AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), AEItemKey.of(Items.DIAMOND_PICKAXE),
                    AEItemKey.of(Items.NETHERITE_INGOT), AEItemKey.of(smithingResult), false);
            ripper.getLogic().getPatternInv().setItemDirect(1, smithingPattern);
            if (!LAYERED_CHAIN) insert(AEItemKey.of(Items.DIAMOND_PICKAXE), ORDER);
            insert(AEItemKey.of(Items.NETHERITE_INGOT), ORDER);
            report.put("nativeSmithingRecipe", smithing.id().toString());
        }
        if (LAYERED_CHAIN) {
            var nativeRecipes = new LinkedHashMap<String, String>();
            nativeRecipes.put("oak_planks", installCraftingPattern(2, new ItemStack(Items.OAK_PLANKS, 4),
                    new ItemStack(Items.OAK_LOG), ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
            nativeRecipes.put("sticks", installCraftingPattern(3, new ItemStack(Items.STICK, 4),
                    new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY,
                    new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
            nativeRecipes.put("diamond_pickaxe", installCraftingPattern(4, new ItemStack(Items.DIAMOND_PICKAXE),
                    new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND),
                    ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY,
                    ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY));
            insert(AEItemKey.of(Items.OAK_LOG), RAW);
            report.put("nativeLayeredCraftingRecipes", nativeRecipes);
        }
        insert(AEItemKey.of(Items.DIAMOND), RAW);
        insert(AEItemKey.of(Items.NETHERRACK), RAW);
        insert(AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 1);
        report.put("nativeRecipe", holder.id().toString());
        report.put("physicalCpuBytes", cpu.getAvailableStorage());
        event("network_ready", Map.of("patterns", ripper.getLogic().getAvailablePatterns().size(),
                "stock", stock(grid.getStorageService().getInventory().getAvailableStacks()),
                "networkIdleAEPerTick", grid.getEnergyService().getIdlePowerUsage(),
                "ripperIdleAEPerTick", ripper.getMainNode().getNode().getIdlePowerUsage()));
        phase("stock_settling");
    }

    private static void beginRun(int nextRun) {
        run = nextRun;
        cpuCalls = 0;
        ripCalls = 0;
        measuredFee = 0;
        executionNanos = 0;
        sameExecutionTick = false;
        cpuFinished = false;
        executionCompleted = false;
        beforeStock = stock(grid.getStorageService().getInventory().getAvailableStacks());
        if (Core256MFixture.ENABLED) Core256MFixture.verifyInitial(beforeStock);
        if (LAYERED_CHAIN) check(beforeStock.get("oak_planks") == 0 && beforeStock.get("stick") == 0
                        && beforeStock.get("diamond_pickaxe") == 0,
                "Layered fixture must start without any prepared intermediate products");
        var source = new MachineSource(grid::getPivot);
        future = grid.getCraftingService().beginCraftingCalculation(level, () -> source,
                targetKey(), ORDER, CalculationStrategy.REPORT_MISSING_ITEMS);
        phase("planning");
        event("planning", Map.of("run", run, "mode", mode(),
                "before", beforeStock));
    }

    private static void pollPlan() throws Exception {
        if (!future.isDone()) return;
        var plan = future.get();
        if (plan != null) event("plan_received", Map.of("run", run, "simulation", plan.simulation(),
                "bytes", plan.bytes(), "missing", stock(plan.missingItems()), "used", stock(plan.usedItems()),
                "missingAll", allStock(plan.missingItems()), "usedAll", allStock(plan.usedItems())));
        check(plan != null && !plan.simulation() && plan.missingItems().isEmpty(), "Plan reports missing materials");
        check(plan instanceof ScheduledCraftingPlan, "AE2 planning Mixin did not attach the schedule interface");
        var schedule = ((ScheduledCraftingPlan) plan).ae2lightoptimizer$getSchedule();
        check(schedule != null && schedule.owner() == CraftingExecutionSchedule.Owner.RING_TERMINAL,
                "Ring solver did not own this genuinely cyclic plan");
        check(plan.bytes() <= cpu.getAvailableStorage(), "Actual CPU too small: plan needs " + plan.bytes());
        growthApplications = 0;
        smithingApplications = 0;
        plankApplications = 0;
        stickApplications = 0;
        toolApplications = 0;
        if (Core256MFixture.ENABLED) {
            Core256MFixture.verifyPlan(plan);
        } else {
            for (var entry : plan.patternTimes().entrySet()) {
                var produced = entry.getKey().getPrimaryOutput().what();
                if (produced.equals(AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE))) growthApplications += entry.getValue();
                else if (produced.equals(AEItemKey.of(Items.NETHERITE_PICKAXE))) smithingApplications += entry.getValue();
                else if (LAYERED_CHAIN && produced.equals(AEItemKey.of(Items.OAK_PLANKS))) plankApplications += entry.getValue();
                else if (LAYERED_CHAIN && produced.equals(AEItemKey.of(Items.STICK))) stickApplications += entry.getValue();
                else if (LAYERED_CHAIN && produced.equals(AEItemKey.of(Items.DIAMOND_PICKAXE))) toolApplications += entry.getValue();
                else throw new IllegalStateException("Unexpected recipe selected in the minimal native fixture: " + produced);
            }
            if (SMITHING_CHAIN) {
                check(smithingApplications == ORDER && (growthApplications == ORDER - 1 || growthApplications == ORDER),
                        "Native smithing-chain material balance differs");
            } else check(growthApplications == ORDER && smithingApplications == 0,
                    "Growth plan does not contain the expected million recipe applications");
            if (LAYERED_CHAIN) check(plan.patternTimes().size() == 5 && growthApplications == ORDER - 1
                            && plankApplications == ORDER / 4 && stickApplications == ORDER / 2 && toolApplications == ORDER,
                    "Layered plan must contain all five native recipes with their exact bulk counts");
        }
        event("planned", Map.of("run", run, "bytes", plan.bytes(), "scheduleBatches", schedule.batches().size(),
                "reserve", schedule.finalOutputReserve(), "used", stock(plan.usedItems()),
                "growthApplications", growthApplications, "smithingApplications", smithingApplications,
                "recipeApplications", recipeApplications(), "scheduleOwner", schedule.owner().name()));
        report.put("scheduleOwner", schedule.owner().name());
        submitTick = tick();
        phase("submitted");
        var result = grid.getCraftingService().submitJob(plan, null, cpu, false, new MachineSource(grid::getPivot));
        check(result.successful(), "Native submit rejected: " + result.errorCode() + " " + result.errorDetail());
        event("submitted", Map.of("run", run, "tick", submitTick,
                "cpuStock", stock(cpu.craftingLogic.getInventory().list)));
    }

    public static void onCpuTick(CraftingCpuLogic logic) {
        ToolComponentFixture.onCpuTick(logic);
        ExactComponentFixture.onCpuTick(logic);
        if (cpu != null && logic == cpu.craftingLogic && "submitted".equals(ProbeState.phase) && logic.hasJob()) cpuCalls++;
    }

    public static void beforeRipper(IGrid executingGrid, IEnergyService energy, ListCraftingInventory inventory) {
        if (executingGrid != grid || !"submitted".equals(ProbeState.phase)) return;
        ripCalls++;
        energyBefore = availablePower(energy);
        executionTick = tick();
        event("ripper_enter", Map.of("run", run, "tick", executionTick,
                "energyAE", energyBefore, "cachedStoredPowerAE", energy.getStoredPower(),
                "cpuStock", stock(inventory.list)));
        executionStartedNanos = System.nanoTime();
    }

    public static void afterRipper(IGrid executingGrid, IEnergyService energy, ListCraftingInventory inventory,
                                   CraftingRipperExecutor.Result result) {
        if (executingGrid != grid || !"submitted".equals(ProbeState.phase)) return;
        executionNanos += System.nanoTime() - executionStartedNanos;
        double energyAfter = availablePower(energy);
        measuredFee += energyBefore - energyAfter;
        sameExecutionTick = tick() == executionTick;
        executionCompleted = result == CraftingRipperExecutor.Result.COMPLETED;
        event("ripper_return", Map.of("run", run, "tick", tick(), "result", result.name(),
                "isolatedExecutionAE", energyBefore - energyAfter, "energyAE", energyAfter,
                "cachedStoredPowerAE", energy.getStoredPower(), "cpuStock", stock(inventory.list),
                "executeNanos", executionNanos, "sameTick", sameExecutionTick));
    }

    public static void afterFinish(CraftingCpuLogic logic, boolean success) {
        if (cpu == null || logic != cpu.craftingLogic || !"submitted".equals(ProbeState.phase)) return;
        if (!success) {
            fail(new IllegalStateException("Native CPU cancelled the whole submitted job"));
            return;
        }
        cpuFinished = true;
        event("native_finish", Map.of("run", run, "tick", tick(), "cpuHasJob", logic.hasJob(),
                "networkStock", stock(grid.getStorageService().getInventory().getAvailableStacks())));
    }

    private static void completeRun() {
        var after = stock(grid.getStorageService().getInventory().getAvailableStacks());
        check(executionCompleted, "Native finish did not come from successful instant execution");
        check(cpuCalls == 1 && ripCalls == 1, "Expected exactly one native CPU tick and ripper call");
        check(executionTick - submitTick <= 1, "Execution exceeded one AE2 tick");
        check(sameExecutionTick, "Synchronous execution crossed a native game tick");
        check(Math.abs(measuredFee - 50.0) < 0.00001, "Extra execution fee differs from 50 AE: " + measuredFee);
        if (Core256MFixture.ENABLED) {
            Core256MFixture.verifyFinal(beforeStock, after);
        } else {
            check(after.get("template") == beforeStock.get("template") + growthApplications - smithingApplications,
                    "Actual template balance does not match the production plan");
            check(after.get("diamond") == beforeStock.get("diamond") - growthApplications * 7 - toolApplications * 3,
                    "Diamond long balance differs");
            check(after.get("netherrack") == beforeStock.get("netherrack") - growthApplications, "Netherrack long balance differs");
            check(after.get("diamond_pickaxe") == beforeStock.get("diamond_pickaxe") + toolApplications - smithingApplications,
                    "Diamond pickaxe input balance differs");
            check(after.get("netherite_ingot") == beforeStock.get("netherite_ingot") - smithingApplications,
                    "Netherite ingot input balance differs");
            check(after.get("netherite_pickaxe") == beforeStock.get("netherite_pickaxe") + smithingApplications,
                    "Final netherite pickaxe output differs");
            check(after.get("oak_log") == beforeStock.get("oak_log") - plankApplications, "Oak log long balance differs");
            check(after.get("oak_planks") == beforeStock.get("oak_planks") + 4 * plankApplications - 2 * stickApplications,
                    "Oak plank intermediate balance differs");
            check(after.get("stick") == beforeStock.get("stick") + 4 * stickApplications - 2 * toolApplications,
                    "Stick intermediate balance differs");
            if (LAYERED_CHAIN) check(after.get("template") == 0 && after.get("oak_planks") == 0
                            && after.get("stick") == 0 && after.get("diamond_pickaxe") == 0,
                    "Completed layered job must leave only the final products and unconsumed raw materials");
        }
        check(ripper.getMainNode().getNode().getIdlePowerUsage() == 5.0, "Ripper's own working base draw differs from 5 AE/t");
        check(!cpu.craftingLogic.hasJob() && cpu.craftingLogic.getInventory().list.isEmpty(), "CPU is not reusable");
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", true);
        result.put("mode", mode());
        result.put("before", beforeStock);
        result.put("after", after);
        result.put("growthApplications", growthApplications);
        result.put("smithingApplications", smithingApplications);
        result.put("recipeApplications", recipeApplications());
        result.put("ripperBaseAEPerTick", ripper.getMainNode().getNode().getIdlePowerUsage());
        result.put("nativeCpuTicks", cpuCalls);
        result.put("ripperCalls", ripCalls);
        result.put("submissionToExecutionTicks", executionTick - submitTick);
        result.put("isolatedExecutionAE", measuredFee);
        result.put("executeNanos", executionNanos);
        result.put("executeMillis", executionNanos / 1_000_000.0);
        result.put("sameExecutionTick", sameExecutionTick);
        report.put("run" + run, result);
        event("run_passed", result);
        if (run == 1 && !SMITHING_CHAIN && !Core256MFixture.ENABLED
                && Boolean.parseBoolean(System.getProperty("ae2lo.probe.loopCard", "true"))) {
            var source = new MachineSource(grid::getPivot);
            long extracted = grid.getStorageService().getInventory().extract(
                    AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), ORDER, Actionable.MODULATE, source);
            check(extracted == ORDER, "Could not isolate the retained seed for the second run");
            report.put("firstRunOutputRemovedByProbe", extracted);
            ripper.getUpgrades().setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
            check(ripper.isLoopCardInstalled(), "Loop card installation did not register");
            check(!ripper.getLogic().getPatternInv().isItemValid(1,
                    ripper.getLogic().getPatternInv().getStackInSlot(0)), "Loop card did not disable new patterns");
            phase("second_settling");
        } else {
            report.put("passed", true);
            ProbeState.status = Core256MFixture.ENABLED
                    ? "PASS: 3000 256M cores from all ten tiers and crystal growth, exact full-chain balances, one tick and 50 AE total"
                    : LAYERED_CHAIN
                    ? "PASS: whole five-recipe layered job, million netherite pickaxes, exact raw and intermediate balance, one tick and 50 AE total"
                    : SMITHING_CHAIN
                    ? "PASS: million netherite pickaxes through native growth ring and smithing, exact plan balance, one tick and 50 AE"
                    : "PASS: million-template growth, long stock, seed retained, one tick and 50 AE per run";
            ProbeState.phase = "passed";
            ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
            writeReport();
            ProbeState.finished = true;
            if (player != null) player.sendSystemMessage(Component.literal(ProbeState.status));
        }
    }

    private static String installCraftingPattern(int slot, ItemStack expected, ItemStack... ingredients) {
        check(ingredients.length == 9, "Native encoded pattern needs exactly nine sparse crafting slots");
        var input = CraftingInput.of(3, 3, Arrays.asList(ingredients));
        var holder = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var result = holder.value().assemble(input);
        check(ItemStack.isSameItemSameComponents(result, expected) && result.getCount() == expected.getCount(),
                "Native layered crafting recipe output differs for " + holder.id());
        ripper.getLogic().getPatternInv().setItemDirect(slot,
                PatternDetailsHelper.encodeCraftingPattern(holder, ingredients, result, false, false));
        return holder.id().toString();
    }

    private static Map<String, Long> recipeApplications() {
        if (Core256MFixture.ENABLED) return Core256MFixture.applications();
        return Map.of("template_growth", growthApplications, "smithing", smithingApplications,
                "oak_planks", plankApplications, "sticks", stickApplications, "diamond_pickaxe", toolApplications);
    }

    private static void insert(AEKey key, long amount) {
        long inserted = grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE,
                new MachineSource(grid::getPivot));
        check(inserted == amount, "Long network insertion truncated " + amount + " to " + inserted);
    }

    private static Map<String, Long> stock(KeyCounter values) {
        var result = new LinkedHashMap<String, Long>();
        result.put("template", values.get(AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
        result.put("diamond", values.get(AEItemKey.of(Items.DIAMOND)));
        result.put("netherrack", values.get(AEItemKey.of(Items.NETHERRACK)));
        result.put("diamond_pickaxe", values.get(AEItemKey.of(Items.DIAMOND_PICKAXE)));
        result.put("netherite_ingot", values.get(AEItemKey.of(Items.NETHERITE_INGOT)));
        result.put("netherite_pickaxe", values.get(AEItemKey.of(Items.NETHERITE_PICKAXE)));
        result.put("oak_log", values.get(AEItemKey.of(Items.OAK_LOG)));
        result.put("oak_planks", values.get(AEItemKey.of(Items.OAK_PLANKS)));
        result.put("stick", values.get(AEItemKey.of(Items.STICK)));
        if (Core256MFixture.ENABLED) Core256MFixture.addStock(values, result);
        return result;
    }

    private static java.util.List<Map<String, Object>> allStock(KeyCounter values) {
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (var entry : values) if (entry.getLongValue() != 0) {
            result.add(Map.of("key", entry.getKey().toString(), "amount", entry.getLongValue()));
        }
        return result;
    }

    private static double availablePower(IEnergyService energy) {
        // getStoredPower is an intentionally stale estimate; injection does not update its cache.
        // SIMULATE asks real providers and their grid buffer without changing any stored energy.
        return energy.extractAEPower(1_000_000_000D, Actionable.SIMULATE, PowerMultiplier.ONE);
    }
    private static AEItemKey targetKey() {
        if (Core256MFixture.ENABLED) return Core256MFixture.target();
        return AEItemKey.of(SMITHING_CHAIN ? Items.NETHERITE_PICKAXE : Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
    }
    private static String mode() { return Core256MFixture.ENABLED || run != 1 ? "loop_card_catalog" : "encoded_pattern"; }
    private static long tick() { return TickHandler.instance().getCurrentTick(); }
    private static void phase(String name) { ProbeState.phase = name; stateTick = tick(); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void event(String name, Map<String, ?> values) {
        try {
            var record = new LinkedHashMap<String, Object>();
            record.put("event", name); record.put("tick", tick()); record.putAll(values);
            Files.writeString(output.resolve("events.jsonl"), JSONL.toJson(record) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            LoggerFactory.getLogger("AE2LO-Runtime-Probe").info("{}", JSONL.toJson(record));
        } catch (Exception error) { throw new IllegalStateException("Cannot record runtime probe evidence", error); }
    }
    private static void fail(Throwable failure) {
        report.put("passed", false); report.put("failedPhase", ProbeState.phase);
        report.put("error", failure.toString());
        ProbeState.status = "FAIL: " + failure;
        ProbeState.phase = "failed";
        ProbeState.screenshotRequest = "ae2lo-ripper-after.png";
        ProbeState.finished = true;
        LoggerFactory.getLogger("AE2LO-Runtime-Probe").error("Runtime probe failed", failure);
        if (output != null) writeReport();
    }
    private static void writeReport() {
        try { Files.writeString(output.resolve("report.json"), GSON.toJson(report), StandardCharsets.UTF_8); }
        catch (Exception failure) { LoggerFactory.getLogger("AE2LO-Runtime-Probe").error("Cannot write report", failure); }
    }
}
