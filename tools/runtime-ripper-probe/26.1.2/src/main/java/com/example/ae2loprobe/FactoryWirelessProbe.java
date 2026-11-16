package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryContent;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlock;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlockEntity;
import com.example.ae2lightoptimizer.omni.OmniContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Acceptance only: the omni connector's wireless Loop Factory structure on real grids. */
public final class FactoryWirelessProbe {
 public static final boolean ENABLED = "factory_wireless".equals(System.getProperty("ae2lo.probe.chain"));
 static MinecraftServer server; static ServerPlayer player; static Path output;
 static ServerLevel level;
 static final BlockPos TERMINAL = new BlockPos(0, 100, 0), TERMINAL_LINK = new BlockPos(0, 101, 0);
 static final BlockPos PROVIDER = new BlockPos(6, 100, 0), PROVIDER_LINK = new BlockPos(6, 101, 0);
 static OmniConnectorBlockEntity terminalLink, providerLink; static FactoryBlockEntity terminal, provider;
 static int phase; static long since; static volatile boolean ready;
 static final Map<String, Object> report = new LinkedHashMap<>();
 static void check(boolean ok, String key) { if (!ok) throw new IllegalStateException(key); report.put(key, true); }
 static void save() { try { Files.writeString(output.resolve("report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); } catch (Exception ex) { throw new RuntimeException(ex); } }
 static void advance() { phase++; since = level.getGameTime(); report.put("phase", phase); save(); }
 static OmniConnectorBlockEntity connector(BlockPos pos) {
  level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
  level.setBlockAndUpdate(pos, OmniContent.BLOCK.get().defaultBlockState()
          .setValue(BlockStateProperties.FACING, Direction.UP));
  return (OmniConnectorBlockEntity) level.getBlockEntity(pos);
 }
 static FactoryBlockEntity machine(BlockPos pos, FactoryBlock.Kind kind, Direction facing) {
  var block = kind == FactoryBlock.Kind.TERMINAL ? FactoryContent.TERMINAL.get() : FactoryContent.PROVIDER.get();
  level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(BlockStateProperties.FACING, facing));
  return (FactoryBlockEntity) level.getBlockEntity(pos);
 }
 public static void start(MinecraftServer s, ServerPlayer p, Path o) {
  server = s; player = p; output = o; level = s.overworld();
  report.put("minecraft", "26.1.2"); report.put("world", s.getWorldData().getLevelName());
  report.put("chain", "factory_wireless"); report.put("passed", false);
  try { player.setGameMode(GameType.CREATIVE); since = level.getGameTime(); save(); }
  catch (Throwable ex) { fail(ex); }
 }
 public static void tick() {
  if (ready) return;
  try {
   if (level.getGameTime() - since < 60) return;
   switch (phase) {
    case 0 -> {
     terminal = machine(TERMINAL, FactoryBlock.Kind.TERMINAL, Direction.UP);
     terminalLink = connector(TERMINAL_LINK);
     advance();
    }
    case 1 -> {
     check(terminalLink != null && terminalLink.factoryLinked(), "terminal_structure_links_connector");
     check(level.getBlockState(TERMINAL_LINK).getValue(OmniConnectorBlock.WIRELESS), "terminal_structure_sets_wireless_state");
     check(terminalLink.getMainNode().getNode().getGrid() == terminal.getMainNode().getNode().getGrid(),
             "terminal_structure_merges_channel_and_terminal_grid");
     level.removeBlock(TERMINAL, false);
     advance();
    }
    case 2 -> {
     check(!terminalLink.factoryLinked(), "removed_terminal_drops_link");
     check(!level.getBlockState(TERMINAL_LINK).getValue(OmniConnectorBlock.WIRELESS), "removed_terminal_clears_wireless_state");
     level.removeBlock(TERMINAL_LINK, false);
     provider = machine(PROVIDER, FactoryBlock.Kind.PROVIDER, Direction.UP);
     providerLink = connector(PROVIDER_LINK);
     advance();
    }
    case 3 -> {
     report.put("providerFront", String.valueOf(provider.getFront()));
     if (provider.getFront() != Direction.UP) {
      // AE2's FRONT mapping is orientation-relative; flip the block so the subnet face points up.
      level.setBlockAndUpdate(PROVIDER, level.getBlockState(PROVIDER)
              .setValue(BlockStateProperties.FACING, Direction.DOWN));
      provider = (FactoryBlockEntity) level.getBlockEntity(PROVIDER);
      advance();
      return;
     }
     advance();
    }
    case 4 -> {
     check(provider.getFront() == Direction.UP, "provider_subnet_face_points_up");
     check(providerLink.factoryLinked(), "provider_structure_links_connector");
     check(providerLink.getMainNode().getNode().getGrid() == provider.getMainNode().getNode().getGrid(),
             "provider_structure_merges_main_network");
     check(providerLink.getMainNode().getNode().getGrid() != provider.factoryGrid(),
             "provider_subnet_is_not_merged");
     check(level.getBlockState(PROVIDER_LINK).getValue(OmniConnectorBlock.WIRELESS), "provider_structure_sets_wireless_state");
     level.removeBlock(PROVIDER, false);
     advance();
    }
    case 5 -> {
     check(!providerLink.factoryLinked(), "removed_provider_drops_link");
     check(!level.getBlockState(PROVIDER_LINK).getValue(OmniConnectorBlock.WIRELESS), "removed_provider_clears_wireless_state");
     finish();
    }
   }
  } catch (Throwable ex) { fail(ex); }
 }
 static void finish() { report.put("passed", true); save(); ProbeState.finished = true; ready = true; }
 static void fail(Throwable ex) { report.put("failure", ex.toString()); save(); ex.printStackTrace(); ProbeState.finished = true; ready = true; }
}