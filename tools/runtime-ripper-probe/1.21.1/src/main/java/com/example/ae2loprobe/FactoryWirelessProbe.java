package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import com.example.ae2lightoptimizer.factory.FactoryContent;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlock;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlockEntity;
import com.example.ae2lightoptimizer.omni.OmniContent;
import com.example.ae2lightoptimizer.omni.OmniFactoryNetworks;
import com.example.ae2lightoptimizer.omni.OmniLinkSavedData;
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

/**
 * Acceptance only: the connector's wireless Loop Factory structure.
 * Factory membership registers the machine's factory side (a provider's subnet); the AE transport joins
 * only the machine's MAIN network to the channel. Both require a selected channel.
 */
public final class FactoryWirelessProbe {
 public static final boolean ENABLED = "factory_wireless".equals(System.getProperty("ae2lo.probe.chain"));
 static MinecraftServer server; static ServerPlayer player; static Path output;
 static ServerLevel level; static String link;
 static final BlockPos TERMINAL = new BlockPos(0, 100, 0), TERMINAL_LINK = new BlockPos(0, 101, 0);
 static final BlockPos PROVIDER = new BlockPos(6, 100, 0), PROVIDER_LINK = new BlockPos(6, 101, 0);
 static OmniConnectorBlockEntity terminalLink, providerLink; static FactoryBlockEntity terminal, provider;
 static final BlockPos A1 = new BlockPos(0, 100, 0), CA1 = new BlockPos(0, 101, 0);
 static final BlockPos B1 = new BlockPos(6, 100, 0), CB1 = new BlockPos(6, 101, 0);
 static final BlockPos B2 = new BlockPos(20, 100, 0), CB2 = new BlockPos(20, 101, 0);
 static FactoryBlockEntity ta, tb, tb2; static OmniConnectorBlockEntity ca, cb, cb2; static String alpha, beta;
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
 static boolean reachable(appeng.api.networking.IGrid grid, BlockPos pos) {
  return OmniFactoryNetworks.reachable(grid, level, pos);
 }
 public static void start(MinecraftServer s, ServerPlayer p, Path o) {
  server = s; player = p; output = o; level = s.overworld();
  report.put("minecraft", "1.21.1"); report.put("world", s.getWorldData().getLevelName());
  report.put("chain", "factory_wireless"); report.put("passed", false);
  try { player.setGameMode(GameType.CREATIVE); link = OmniLinkSavedData.get(server).create("wireless factory").id(); since = level.getGameTime(); save(); }
  catch (Throwable ex) { fail(ex); }
 }
 public static void tick() {
  if (ready) return;
  try {
   if (level.getGameTime() - since < 60) return;
   switch (phase) {
    case 0 -> {
     terminalLink = connector(TERMINAL_LINK);
     terminalLink.selectLink(link);
     advance();
    }
    case 1 -> {
     terminal = machine(TERMINAL, FactoryBlock.Kind.TERMINAL, Direction.UP);
     advance();
    }
    case 2 -> {
     check(terminalLink != null && terminalLink.factoryLinked(), "terminal_structure_joins_channel_factory_network");
     check(level.getBlockState(TERMINAL_LINK).getValue(OmniConnectorBlock.WIRELESS), "terminal_structure_sets_wireless_state");
     check(terminalLink.getMainNode().getNode().getGrid() == terminal.getMainNode().getNode().getGrid(),
             "terminal_structure_merges_ae_main_grid");
     check(reachable(terminal.factoryGrid(), TERMINAL_LINK), "terminal_chunk_is_factory_network_member");
     level.removeBlock(TERMINAL, false);
     advance();
    }
    case 3 -> {
     check(!terminalLink.factoryLinked(), "removed_terminal_drops_membership");
     check(!level.getBlockState(TERMINAL_LINK).getValue(OmniConnectorBlock.WIRELESS), "removed_terminal_clears_wireless_state");
     check(!reachable(terminal.factoryGrid(), TERMINAL_LINK), "removed_terminal_clears_membership");
     level.removeBlock(TERMINAL_LINK, false);
     providerLink = connector(PROVIDER_LINK);
     providerLink.selectLink(link);
     advance();
    }
    case 4 -> {
     provider = machine(PROVIDER, FactoryBlock.Kind.PROVIDER, Direction.UP);
     advance();
    }
    case 5 -> {
     report.put("providerFront", String.valueOf(provider.getFront()));
     if (provider.getFront() != Direction.UP) {
      level.setBlockAndUpdate(PROVIDER, level.getBlockState(PROVIDER)
              .setValue(BlockStateProperties.FACING, Direction.DOWN));
      provider = (FactoryBlockEntity) level.getBlockEntity(PROVIDER);
      advance();
      return;
     }
     advance();
    }
    case 6 -> {
     check(provider.getFront() == Direction.UP, "provider_subnet_face_points_up");
     check(providerLink.factoryLinked(), "provider_structure_joins_channel_factory_network");
     check(level.getBlockState(PROVIDER_LINK).getValue(OmniConnectorBlock.WIRELESS), "provider_structure_sets_wireless_state");
     check(providerLink.getMainNode().getNode().getGrid() == provider.getMainNode().getNode().getGrid(),
             "provider_structure_merges_ae_main_grid");
     check(reachable(provider.factoryGrid(), PROVIDER_LINK), "provider_subnet_is_factory_network_member");
     check(providerLink.getMainNode().getNode().getGrid() != provider.factoryGrid(),
             "provider_subnet_is_not_on_the_ae_channel");
     check(!reachable(provider.getMainNode().getGrid(), PROVIDER_LINK), "provider_main_network_is_not_broadcast");
     level.removeBlock(PROVIDER, false);
     advance();
    }
    case 7 -> {
     check(!providerLink.factoryLinked(), "removed_provider_drops_membership");
     check(!level.getBlockState(PROVIDER_LINK).getValue(OmniConnectorBlock.WIRELESS), "removed_provider_clears_wireless_state");
     alpha = OmniLinkSavedData.get(server).create("alpha network").id();
     beta = OmniLinkSavedData.get(server).create("beta network").id();
     ta = machine(A1, FactoryBlock.Kind.TERMINAL, Direction.UP);
     tb = machine(B1, FactoryBlock.Kind.TERMINAL, Direction.UP);
     tb2 = machine(B2, FactoryBlock.Kind.TERMINAL, Direction.UP);
     advance();
    }
    case 8 -> {
     ca = connector(CA1); ca.selectLink(alpha);
     cb = connector(CB1); cb.selectLink(beta);
     cb2 = connector(CB2); cb2.selectLink(beta);
     advance();
    }
    case 9 -> {
     // Chunk 0 holds machines of both networks: a shared member of two factory networks.
     check(reachable(ta.factoryGrid(), CA1), "network_a_reaches_its_own_chunk");
     check(reachable(tb.factoryGrid(), CB1), "network_b_reaches_the_shared_chunk");
     check(reachable(ta.factoryGrid(), B1), "shared_machine_is_a_member_of_network_a");
     check(reachable(tb.factoryGrid(), A1), "shared_machine_is_a_member_of_network_b");
     check(!reachable(ta.factoryGrid(), CB2), "network_a_does_not_reach_network_b_only_chunk");
     check(reachable(tb.factoryGrid(), CB2), "network_b_reaches_its_second_chunk");
     advance();
    }
    case 10 -> {
     // A wireless member is inert until a tag binds it: tagging is refused outside the network,
     // and tagging alone moves nothing.
     var chest = new BlockPos(7, 100, 0);
     level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
     var chestEntity = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chest);
     chestEntity.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 5));
     var belongsA = (java.util.function.Predicate<Long>) pos -> com.example.ae2lightoptimizer.factory.FactoryServer.contains(ta.factoryGrid(), net.minecraft.core.BlockPos.of(pos));
     var belongsB = (java.util.function.Predicate<Long>) pos -> com.example.ae2lightoptimizer.factory.FactoryServer.contains(tb.factoryGrid(), net.minecraft.core.BlockPos.of(pos));
     ta.tags.reconcile(java.util.List.of("in", "out"));
     tb.tags.reconcile(java.util.List.of("in"));
     check(ta.tags.tag("in", chest.asLong(), belongsA), "network_a_tag_binds_the_shared_member");
     check(tb.tags.tag("in", chest.asLong(), belongsB), "network_b_tag_binds_the_same_member");
     check(!ta.tags.tag("out", CB2.asLong(), belongsA), "network_a_cannot_tag_network_b_only_chunk");
     check(chestEntity.getItem(0).getCount() == 5, "tagging_alone_moves_nothing");
     check(com.example.ae2lightoptimizer.factory.FactoryServer.members(ta.factoryGrid(), ta.tags.positions("in")).contains(chest),
             "network_a_resolves_its_tagged_member");
     check(com.example.ae2lightoptimizer.factory.FactoryServer.members(tb.factoryGrid(), tb.tags.positions("in")).contains(chest),
             "network_b_resolves_its_tagged_member");
     check(!ta.tags.names().contains("bonly") && !tb.tags.names().contains("aonly"), "tag_stores_are_per_network");
     ta.tags.reconcile(java.util.List.of("in", "out", "aonly"));
     ta.tags.tag("aonly", A1.asLong(), belongsA);
     check(ta.tags.names().contains("aonly") && !tb.tags.names().contains("aonly"), "one_network_tag_does_not_leak");
     level.removeBlock(CB1, false);
     advance();
    }
    case 11 -> {
     check(!reachable(tb.factoryGrid(), A1), "removed_connector_drops_that_network_membership");
     check(reachable(ta.factoryGrid(), B1), "other_network_membership_is_untouched");
     finish();
    }
   }
  } catch (Throwable ex) { fail(ex); }
 }
 static void finish() { report.put("passed", true); save(); ProbeState.finished = true; ready = true; }
 static void fail(Throwable ex) { report.put("failure", ex.toString()); save(); ex.printStackTrace(); ProbeState.finished = true; ready = true; }
}
