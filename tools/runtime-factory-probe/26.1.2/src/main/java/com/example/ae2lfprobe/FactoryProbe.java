package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.core.definitions.AEBlocks;
import appeng.api.config.Actionable;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real world/capabilities and production jobs; does not replace inventories or bypass transfer code. */
@Mod("ae2lf_runtime_probe")
public final class FactoryProbe {
    private static final int SHARED_BATTERY_COUNT = 8;
    private static final double SHARED_BATTERY_CHARGE = 1_000_000;
    private int phase, ticks, wait;
    private FactoryBlockEntity host;
    private BarrelBlockEntity from, to;
    private FactoryJob job;
    private ServerLevel level;
    private NativeCraftingFixture nativeCrafting;
    private final java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>();
    public FactoryProbe(net.neoforged.bus.api.IEventBus bus) { NativeTransactionalAudit.register(bus);NeoForge.EVENT_BUS.addListener(this::tick); }
    private void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
        evidence.put(message, true);
    }
    private void report(String status) {
        evidence.put("status", status);
        if (level != null) evidence.put("sharedNetwork", sharedNetworkState(level));
        evidence.put("phase", phase);
        evidence.put("ticks", ticks);
        evidence.put("scope", "Real world production FactoryJob and external capability routing; not native CPU submission or full restart");
        try {
            var path = java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir"));
            java.nio.file.Files.createDirectories(path);
            java.nio.file.Files.writeString(path.resolve("factory-report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
    }
    private void tick(ServerTickEvent.Post event) {
        HugeQuantityAudit.tick(); RecoveryAudit.tick(); ChunkLifecycleAudit.tick();
        if (nativeCrafting != null) nativeCrafting.tick();
        ParallelOrderAudit.tick(); NativeTransactionalAudit.tick(); StressAudit.tick(); TopologyAudit.tick(); TerminalAudit.tick(); InductionAudit.tick(); MultiTagAudit.tick(); ComplexFlowAudit.tick(); RecipeSetAudit.tick();
        MinimalRedstoneAudit.tick(event);
        InspectRedstoneAudit.tick(event);
        UserWorldTagAudit.tick(event);
        TagAndHasAudit.tick(event);
        EncoderBindingAudit.tick(event);
        ChannelAudit.tick(event);
        if (!Boolean.getBoolean("ae2lf.probe") || phase == 99 || Boolean.getBoolean("ae2lf.probe.skipMainFixture")) return;
        level = event.getServer().overworld();
        if (!event.getServer().getWorldData().getLevelName().startsWith("AE2LF-Factory-Probe-")
                || event.getServer().getPlayerList().getPlayerCount() == 0) return;
        if (RestartState.RESUME) {
            var restored = level.getBlockEntity(new BlockPos(0,100,0));
            if (restored instanceof FactoryBlockEntity provider) {
                try { nativeCrafting = new NativeCraftingFixture(level, provider); }
                catch(Throwable failure) { evidence.put("resume_failure",failure.toString()); report("failed"); RestartState.exitRequested=true; }
                phase = 99;
            }
            return;
        }
        try {
            if (++ticks > 1800) throw new IllegalStateException("Fixture timeout");
            if (phase == 0) {
                var p = new BlockPos(0, 100, 0);
                for (int x = -4; x <= 8; x++) for (int z = -4; z <= 8; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(p, FactoryContent.PROVIDER.get().defaultBlockState());
                host = (FactoryBlockEntity) level.getBlockEntity(p);
                Direction front = host.getFront();
                var power = p.relative(front.getOpposite());
                // The shared UI/compatibility grid remains alive throughout the complete campaign.
                // Charge native finite cells once at construction; never top them up during a run.
                var batteries = new java.util.ArrayList<java.util.Map<String, Object>>();
                double acceptedTotal = 0;
                for (int index = 0; index < SHARED_BATTERY_COUNT; index++) {
                    var batteryPos = power.below(index);
                    level.setBlockAndUpdate(batteryPos, AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
                    var battery = (EnergyCellBlockEntity) level.getBlockEntity(batteryPos);
                    double rejected = battery.injectAEPower(SHARED_BATTERY_CHARGE, Actionable.MODULATE);
                    double accepted = SHARED_BATTERY_CHARGE - rejected;
                    acceptedTotal += accepted;
                    batteries.add(java.util.Map.of("position", batteryPos.toShortString(),
                            "requestedAE", SHARED_BATTERY_CHARGE, "acceptedAE", accepted,
                            "storedAE", battery.getAECurrentPower(), "maximumAE", battery.getAEMaxPower()));
                    check(accepted == SHARED_BATTERY_CHARGE, "finite_battery_charged_once_" + index);
                }
                evidence.put("finiteBatteryInitialCharge", batteries);
                evidence.put("finiteBatteryTotalAcceptedAE", acceptedTotal);
                evidence.put("finiteBatteryChargePolicy", "Eight native dense cells, one initial charge each; no runtime refills");
                var cable = p.relative(front);
                level.setBlockAndUpdate(cable, FactoryContent.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(cable.above(), Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(cable.below(), Blocks.BARREL.defaultBlockState());
                from = (BarrelBlockEntity) level.getBlockEntity(cable.above());
                to = (BarrelBlockEntity) level.getBlockEntity(cable.below());
                from.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
                from.setItem(1, new ItemStack(Items.GOLD_INGOT, 7));
                for (int i = 0; i < to.getContainerSize(); i++) to.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                to.setItem(0, new ItemStack(Items.IRON_INGOT, 60));
                var player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.connection.teleport(3.5, 102, 4.5, 140f, 25f);
                evidence.put("front", front.name());
                phase = 1; report("running"); return;
            }
            if (phase == 1) {
                if (!host.getMainNode().isActive() || host.factoryGrid() == null) return;
                if (!finiteBatteriesReady(host)) return;
                check(true, "eight_finite_battery_nodes_join_real_main_grid");
                check(host.factoryGrid() != host.getMainNode().getGrid(), "physical_main_subnet_distinct");
                check(FactoryServer.owner(host.factoryGrid()) == host, "provider_owns_subnet");
                check(FactoryServer.contains(host.factoryGrid(), from.getBlockPos()), "adjacent_barrel_is_member");
                host.tags.reconcile(java.util.List.of("A", "B"));
                host.tags.tag("A", from.getBlockPos().asLong(), p -> FactoryServer.contains(host.factoryGrid(), BlockPos.of(p)));
                host.tags.tag("B", to.getBlockPos().asLong(), p -> FactoryServer.contains(host.factoryGrid(), BlockPos.of(p)));
                String code = "import A,B\nget 64 minecraft::item!(minecraft:gold_ingot) from A\nwait 20 tick\nput minecraft::item into B\nwait 20 tick\nput minecraft::item into B\ndone";
                job = new FactoryJob(host, new FactoryPatternData(code, host.factoryId(), ItemStack.EMPTY));
                job.tick();
                check(from.getItem(0).getCount() == 64 && to.getItem(0).getCount() == 60, "get_does_not_extract");
                check(job.error().isEmpty(), "source_declaration_valid");
                job = FactoryJob.restore(host, roundtrip(job.save()));
                phase = 2; wait = 0; report("running"); return;
            }
            if (phase == 2) {
                job.tick();
                check(job.error().isEmpty(), "transfer_has_no_error");
                if (++wait < 20) {
                    check(from.getItem(0).getCount() == 64, "wait_retains_physical_source");
                    return;
                }
                check(from.getItem(0).getCount() == 60 && to.getItem(0).getCount() == 64, "partial_destination_moves_only_four");
                check(from.getItem(1).getCount() == 7, "exclusion_preserves_gold");
                job = FactoryJob.restore(host, roundtrip(job.save()));
                to.setItem(0, new ItemStack(Items.IRON_INGOT, 59)); // Explicitly make five slots of capacity.
                evidence.put("fixture_removed_destination_items", 5);
                phase = 3; wait = 0; report("running"); return;
            }
            if (phase == 3) {
                job.tick();
                if (++wait < 20) return;
                check(job.error().isEmpty() && job.finished(), "restored_job_finishes");
                check(from.getItem(0).getCount() == 55 && to.getItem(0).getCount() == 64, "resume_moves_exactly_five_without_replay");
                check(from.getItem(1).getCount() == 7, "unselected_resource_unchanged");
                host.pulse("A", 3);
                check(level.hasNeighborSignal(from.getBlockPos()) && level.getBestNeighborSignal(from.getBlockPos()) == 15,
                        "tagged_machine_receives_signal");
                phase = 4; wait = 0; return;
            }
            if (phase == 4 && ++wait >= 3) {
                check(!level.hasNeighborSignal(from.getBlockPos()), "pulse_expires");
                var terminalPos = new BlockPos(4, 100, 4);
                level.setBlockAndUpdate(terminalPos, FactoryContent.TERMINAL.get().defaultBlockState());
                var terminal = (FactoryBlockEntity) level.getBlockEntity(terminalPos);
                var stack = com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                String code = "import input,output\nget minecraft::item from input\nput minecraft::item into output\ndone";
                stack.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(code, terminal.factoryId(), ItemStack.EMPTY));
                terminal.factoryPatterns().setItemDirect(0, stack);
                terminal.setFactoryDraft(code);
                var player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.connection.teleport(4.5, 101, 6.5, 180f, 20f);
                nativeCrafting = new NativeCraftingFixture(level, host);
                phase = 5; wait = 0; report("running"); return;
            }
            if (phase == 5 && ++wait >= 40) {
                var terminal = (FactoryBlockEntity) level.getBlockEntity(new BlockPos(4,100,4));
                var player = event.getServer().getPlayerList().getPlayers().getFirst();
                terminal.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(terminal));
                evidence.put("editor_open_requested_after_chunk_sync", true);
                report("passed"); phase = 99;
            }
        } catch (Throwable failure) {
            evidence.put("failure", failure.toString());
            evidence.put("stack", java.util.Arrays.toString(failure.getStackTrace()));
            report("failed"); phase = 99; RestartState.exitRequested=true;
            org.slf4j.LoggerFactory.getLogger(FactoryProbe.class).error("Factory probe failed", failure);
        }
    }
    private static boolean finiteBatteriesReady(FactoryBlockEntity provider) {
        var power = provider.getBlockPos().relative(provider.getFront().getOpposite());
        var grid = provider.getMainNode().getGrid();
        if (grid == null) return false;
        for (int index = 0; index < SHARED_BATTERY_COUNT; index++) {
            if (!(provider.getLevel().getBlockEntity(power.below(index)) instanceof EnergyCellBlockEntity battery)
                    || !battery.getMainNode().isReady() || battery.getMainNode().getGrid() != grid) return false;
        }
        return true;
    }

    static java.util.Map<String, Object> sharedNetworkState(ServerLevel world) {
        var state = new java.util.LinkedHashMap<String, Object>();
        state.put("gameTime", world.getGameTime());
        if (!(world.getBlockEntity(new BlockPos(0, 100, 0)) instanceof FactoryBlockEntity provider)) {
            state.put("providerPresent", false);
            return state;
        }
        state.put("providerPresent", true);
        state.put("providerId", provider.factoryId());
        state.put("providerNodeReady", provider.getMainNode().isReady());
        state.put("providerActive", provider.getMainNode().isActive());
        var main = provider.getMainNode().getGrid();
        var subnet = provider.factoryGrid();
        state.put("physicalMainSubnetDistinct", main != null && subnet != null && main != subnet);
        state.put("providerOwnsSubnet", subnet != null && FactoryServer.owner(subnet) == provider);
        state.put("allFiniteBatteryNodesOnMainGrid", finiteBatteriesReady(provider));
        var batteries = new java.util.ArrayList<java.util.Map<String, Object>>();
        var power = provider.getBlockPos().relative(provider.getFront().getOpposite());
        double stored = 0;
        for (int index = 0; index < SHARED_BATTERY_COUNT; index++) {
            var pos = power.below(index);
            var row = new java.util.LinkedHashMap<String, Object>();
            row.put("position", pos.toShortString());
            row.put("chunkLoaded", world.hasChunkAt(pos));
            if (world.getBlockEntity(pos) instanceof EnergyCellBlockEntity battery) {
                row.put("storedAE", battery.getAECurrentPower());
                row.put("nodeReady", battery.getMainNode().isReady());
                row.put("onMainGrid", main != null && battery.getMainNode().getGrid() == main);
                stored += battery.getAECurrentPower();
            } else row.put("missing", true);
            batteries.add(row);
        }
        state.put("finiteBatteries", batteries);
        state.put("finiteBatteryRemainingAE", stored);
        double drain = 0;
        if (main != null) {
            var energy = main.getEnergyService();
            state.put("mainPowered", energy.isNetworkPowered());
            state.put("mainAvailableAE", energy.extractAEPower(1_000_000_000, Actionable.SIMULATE,
                    appeng.api.config.PowerMultiplier.ONE));
            state.put("mainIdleAEPerTick", energy.getIdlePowerUsage());
            state.put("mainChannelAEPerTick", energy.getChannelPowerUsage());
            drain += energy.getIdlePowerUsage() + energy.getChannelPowerUsage();
        }
        if (subnet != null) {
            var energy = subnet.getEnergyService();
            state.put("subnetPowered", energy.isNetworkPowered());
            state.put("subnetAvailableAE", energy.extractAEPower(1_000_000_000, Actionable.SIMULATE,
                    appeng.api.config.PowerMultiplier.ONE));
            state.put("subnetIdleAEPerTick", energy.getIdlePowerUsage());
            state.put("subnetChannelAEPerTick", energy.getChannelPowerUsage());
            drain += energy.getIdlePowerUsage() + energy.getChannelPowerUsage();
        }
        state.put("observedIdleAndChannelAEPerTick", drain);
        if (drain > 0) {
            state.put("initialChargeTicksAtObservedDrain", SHARED_BATTERY_COUNT * SHARED_BATTERY_CHARGE / drain);
            state.put("remainingTicksAtObservedDrain", stored / drain);
        }
        for (var pos : java.util.List.of(new BlockPos(2, 100, 0), new BlockPos(0, 100, -2))) {
            String name = pos.getX() == 2 ? "mainDrive" : "subnetDrive";
            boolean active = world.getBlockEntity(pos) instanceof appeng.blockentity.storage.DriveBlockEntity drive
                    && drive.getMainNode().isActive()
                    && drive.getMainNode().getGrid() == (pos.getX() == 2 ? main : subnet);
            state.put(name + "ActiveOnExpectedGrid", active);
        }
        return state;
    }

    static void requirePoweredSharedNetwork(ServerLevel world, java.util.Map<String, Object> evidence, String stage) {
        var state = sharedNetworkState(world);
        evidence.put(stage, state);
        for (String key : java.util.List.of("providerPresent", "providerNodeReady", "providerActive",
                "physicalMainSubnetDistinct", "providerOwnsSubnet", "allFiniteBatteryNodesOnMainGrid",
                "mainPowered", "subnetPowered", "mainDriveActiveOnExpectedGrid", "subnetDriveActiveOnExpectedGrid")) {
            if (!Boolean.TRUE.equals(state.get(key)))
                throw new IllegalStateException("Shared fixture prerequisite " + key + " failed at " + stage);
        }
        if (!(state.get("finiteBatteryRemainingAE") instanceof Number remaining) || remaining.doubleValue() <= 0
                || !(state.get("mainAvailableAE") instanceof Number available) || available.doubleValue() <= 0)
            throw new IllegalStateException("Shared finite battery reserve is exhausted at " + stage);
        if (!(state.get("initialChargeTicksAtObservedDrain") instanceof Number capacity)
                || capacity.doubleValue() <= 120_000)
            throw new IllegalStateException("Shared finite charge cannot cover 120000 ticks at the observed drain");
    }

    private FactoryJob.Saved roundtrip(FactoryJob.Saved saved) {
        var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = FactoryJob.Saved.CODEC.encodeStart(ops, saved).getOrThrow();
        evidence.put("last_saved_job", encoded.toString());
        return FactoryJob.Saved.CODEC.parse(ops, com.google.gson.JsonParser.parseString(encoded.toString())).getOrThrow();
    }
}
