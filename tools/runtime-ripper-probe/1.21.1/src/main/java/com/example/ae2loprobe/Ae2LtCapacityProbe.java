package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Acceptance only: the supercomputer's single channel doubling on an AE2 Lightning Tech overloaded
 * network. Thunderbolt's max-flow replaces AE2's per-node BFS, so the doubling has to reach its
 * per-controller supply; this fixture measures real channel assignment with real overloaded controllers.
 */
public final class Ae2LtCapacityProbe {
 public static final boolean ENABLED = "ae2lt_capacity".equals(System.getProperty("ae2lo.probe.chain"));
 static final int CONSUMERS = 200;
 static MinecraftServer server; static ServerPlayer player; static Path output;
 // Real blocks join a grid by adjacency: the cell and the optimizer sit next to the controller.
 static ServerLevel level; static BlockPos controllerPos = new BlockPos(0, 100, 0), optimizerPos = new BlockPos(1, 100, 0), cellPos = new BlockPos(0, 100, 1);
 static final List<IManagedGridNode> loads = new ArrayList<>();
 static int phase; static long since; static volatile boolean ready;
 static final Map<String, Object> report = new LinkedHashMap<>();
 static void check(boolean ok, String key) { if (!ok) throw new IllegalStateException(key); report.put(key, true); }
 static void save() { try { Files.writeString(output.resolve("report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); } catch (Exception ex) { throw new RuntimeException(ex); } }
 static int active() { return (int) loads.stream().filter(IManagedGridNode::isActive).count(); }
 static int overloadedCapacity(IGrid grid) {
  try {
   var type = Class.forName("com.moakiee.thunderbolt.core.channel.HighCapacityChannelSupport");
   return (int) type.getMethod("calculateHighCapacityNetworkCapacity", IGrid.class).invoke(null, grid);
  } catch (Throwable missing) { return -1; }
 }
 static IManagedGridNode load(IGridNode parent) {
  var node = GridHelper.createManagedNode(new Object(), new IGridNodeListener<Object>() {
   public void onSaveChanges(Object owner, IGridNode node) {}
  }).setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0).setInWorldNode(false);
  node.create(level, BlockPos.ZERO);
  GridHelper.createConnection(parent, node.getNode());
  return node;
 }
 public static void start(MinecraftServer s, ServerPlayer p, Path o) {
  server = s; player = p; output = o; level = s.overworld();
  report.put("minecraft", "1.21.1"); report.put("world", s.getWorldData().getLevelName());
  report.put("chain", "ae2lt_capacity"); report.put("passed", false); report.put("consumers", CONSUMERS);
  try {
   player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
   var controller = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2lt:overloaded_controller"));
   if (controller == AEBlocks.CONTROLLER.block() || !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("ae2lt:overloaded_controller")))
    throw new IllegalStateException("AE2 Lightning Tech overloaded controller is not installed");
   level.setChunkForced(controllerPos.getX() >> 4, controllerPos.getZ() >> 4, true);
   level.setBlockAndUpdate(controllerPos, controller.defaultBlockState());
   level.setBlockAndUpdate(cellPos, AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
   ((EnergyCellBlockEntity) level.getBlockEntity(cellPos)).injectAEPower(4_000_000, Actionable.MODULATE);
   since = level.getGameTime(); save();
  } catch (Throwable ex) { fail(ex); }
 }
 public static void tick() {
  if (ready) return;
  try {
   if (level.getGameTime() - since < 80) return;
   switch (phase) {
    case 0 -> {
     var be = level.getBlockEntity(controllerPos);
     check(be instanceof appeng.blockentity.networking.ControllerBlockEntity, "overloaded_controller_present");
     var node = ((appeng.blockentity.networking.ControllerBlockEntity) be).getMainNode().getNode();
     check(node != null && node.isActive(), "overloaded_controller_active");
     for (int i = 0; i < CONSUMERS; i++) loads.add(load(node));
     advance();
    }
    case 1 -> {
     int baseline = active();
     var grid = ((appeng.blockentity.networking.ControllerBlockEntity) level.getBlockEntity(controllerPos)).getMainNode().getNode().getGrid();
     report.put("baselineActive", baseline);
     report.put("baselineCapacity", overloadedCapacity(grid));
     report.put("baselineUsedChannels", grid.getPathingService().getUsedChannels());
     check(baseline > 0 && baseline < CONSUMERS, "overloaded_network_is_limited_before_optimizer");
     check(overloadedCapacity(grid) > 0, "thunderbolt_max_flow_capacity_readable");
     level.setBlockAndUpdate(optimizerPos, ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get().defaultBlockState());
     advance();
    }
    case 2 -> {
     var grid = ((appeng.blockentity.networking.ControllerBlockEntity) level.getBlockEntity(controllerPos)).getMainNode().getNode().getGrid();
     int withOptimizer = active();
     report.put("optimizerActive", withOptimizer);
     report.put("optimizerCapacity", overloadedCapacity(grid));
     check(withOptimizer == CONSUMERS, "supercomputer_doubles_overloaded_network_capacity");
     check(overloadedCapacity(grid) >= 2 * ((Number) report.get("baselineCapacity")).intValue(), "reported_capacity_doubled");
     level.removeBlock(optimizerPos, false);
     advance();
    }
    case 3 -> {
     var grid = ((appeng.blockentity.networking.ControllerBlockEntity) level.getBlockEntity(controllerPos)).getMainNode().getNode().getGrid();
     report.put("restoredActive", active());
     report.put("restoredCapacity", overloadedCapacity(grid));
     check(active() == ((Number) report.get("baselineActive")).intValue(), "removing_optimizer_restores_baseline");
     finish();
    }
   }
  } catch (Throwable ex) { fail(ex); }
 }
 static void advance() { phase++; since = level.getGameTime(); report.put("phase", phase); save(); }
 static void finish() { report.put("passed", true); save(); ProbeState.finished = true; ready = true; }
 static void fail(Throwable ex) { report.put("failure", ex.toString()); save(); ex.printStackTrace(); ProbeState.finished = true; ready = true; }
}
