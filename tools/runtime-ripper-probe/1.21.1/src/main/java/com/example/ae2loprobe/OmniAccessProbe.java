package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.*;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.network.OmniLinkDirectory;
import com.example.ae2lightoptimizer.omni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Acceptance only: channel visibility and the ripper's power/upload surface on real AE2 grids. */
public final class OmniAccessProbe {
 public static final boolean ENABLED = "omni_access".equals(System.getProperty("ae2lo.probe.chain"));
 static final String OTHER_PLAYER = "00000000-0000-0000-0000-00000000b0b0";
 static MinecraftServer server; static ServerPlayer player; static Path output;
 static ServerLevel level; static OmniConnectorBlockEntity first, second; static CraftingRipperBlockEntity ripper;
 static String open; static String secret; static String otherSecret; static String otherSharedName;
 static AEItemKey expectedOutput;
 static int phase; static long since; static volatile boolean ready;
 static final BlockPos A = new BlockPos(0, 100, 0), B = new BlockPos(4, 100, 0), R = new BlockPos(-6, 100, 0);
 // Adjacent to the ripper: a diagonal energy cell is not a grid connection.
 static final BlockPos CELL = new BlockPos(-6, 100, 1);
 static final Map<String, Object> report = new LinkedHashMap<>();
 static void check(boolean ok, String key) { if (!ok) throw new IllegalStateException(key); report.put(key, true); }
 static void save() { try { Files.writeString(output.resolve("report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); } catch (Exception ex) { throw new RuntimeException(ex); } }
 static String me() { return player.getUUID().toString(); }
 static OmniLinkDirectory directory() { return OmniLinkSavedData.get(server).directory(); }
 static OmniConnectorBlockEntity place(ServerLevel l, BlockPos pos) {
  l.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
  l.setBlockAndUpdate(pos, OmniContent.BLOCK.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
  return (OmniConnectorBlockEntity) l.getBlockEntity(pos);
 }
 static void advance() { phase++; since = level.getGameTime(); report.put("phase", phase); save(); }
 public static void start(MinecraftServer s, ServerPlayer p, Path o) {
  server = s; player = p; output = o; level = s.overworld();
  report.put("minecraft", "1.21.1"); report.put("world", s.getWorldData().getLevelName()); report.put("chain", "omni_access"); report.put("passed", false);
  try {
   player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
   player.connection.teleport(1.5, 102, 2.5, 180, 20);
   since = level.getGameTime(); save();
  } catch (Throwable ex) { fail(ex); }
 }
 static ItemStack encodedPattern() {
  // The same native template-growth recipe the other ripper fixtures use: a proven ripper-supported chain.
  var ingredients = new ItemStack[] {
   new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND),
   new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERRACK), new ItemStack(Items.DIAMOND),
   new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND) };
  var input = CraftingInput.of(3, 3, Arrays.asList(ingredients));
  var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
  var result = recipe.value().assemble(input, level.registryAccess());
  expectedOutput = AEItemKey.of(result);
  return PatternDetailsHelper.encodeCraftingPattern(recipe, ingredients, result, false, false);
 }
 public static void tick() {
  if (ready) return;
  try {
   if (level.getGameTime() - since < 60) return;
   switch (phase) {
    case 0 -> {
     open = directory().createOrGet("Open access", OmniLinkDirectory.Visibility.PUBLIC, "").id();
     secret = directory().createOrGet("Secret plan", OmniLinkDirectory.Visibility.PRIVATE, me()).id();
     otherSecret = directory().createOrGet("Secret plan", OmniLinkDirectory.Visibility.PRIVATE, OTHER_PLAYER).id();
     otherSharedName = directory().createOrGet("Other private", OmniLinkDirectory.Visibility.PRIVATE, OTHER_PLAYER).id();
     check(!open.equals(secret), "public_and_private_ids_differ");
     check(!secret.equals(otherSecret), "same_private_name_is_per_owner");
     report.put("visibleToMe", directory().sortedVisible(me()).stream().map(OmniLinkDirectory.Link::name).toList());
     report.put("visibleToOther", directory().sortedVisible(OTHER_PLAYER).stream().map(OmniLinkDirectory.Link::name).toList());
     check(directory().sortedVisible(me()).size() == 2, "owner_sees_public_and_own_private");
     check(directory().sortedVisible(OTHER_PLAYER).size() == 3, "other_sees_public_and_their_private");
     check(!directory().canSee(secret, OTHER_PLAYER), "private_link_invisible_to_other_player");
     check(directory().canSee(open, OTHER_PLAYER), "public_link_visible_to_other_player");
     check(directory().canSee(otherSecret, OTHER_PLAYER), "other_owner_sees_own_private_link");
     advance();
    }
    case 1 -> {
     // Native block entities create their grid node in onReady; bind one tick after placement.
     first = place(level, A);
     second = place(level, B);
     advance();
    }
    case 2 -> {
     first.selectLink(secret);
     second.selectLink(secret);
     check(first.linked(), "private_link_binds_owner_connector");
     check(OmniNetworks.get(server).count(secret) == 2, "owner_second_endpoint_joins_private_link");
     check(first.getMainNode().getNode().getGrid() == second.getMainNode().getNode().getGrid(), "private_link_is_one_native_grid");
     // The menu guard is what stops another player from binding a link they cannot see.
     first.open(player);
     check(player.containerMenu instanceof OmniConnectorMenu, "connector_menu_opened");
     advance();
    }
    case 3 -> {
     var menu = (OmniConnectorMenu) player.containerMenu;
     menu.select(otherSecret);
     check(first.linkId().equals(secret), "menu_refuses_invisible_private_link");
     menu.select(otherSharedName);
     check(first.linkId().equals(secret), "menu_refuses_other_private_link");
     menu.select(open);
     check(first.linkId().equals(open), "menu_accepts_public_link");
     menu.select(secret);
     check(first.linkId().equals(secret), "menu_restores_own_private_link");
     menu.setCreatePrivate(true);
     menu.create("Fresh private");
     var created = directory().sortedVisible(me()).stream().filter(l -> l.name().equals("Fresh private")).findFirst().orElseThrow();
     check(created.visibility() == OmniLinkDirectory.Visibility.PRIVATE, "menu_creates_private_link");
     check(created.owner().equals(me()), "created_private_link_owner_is_player");
     check(!directory().canSee(created.id(), OTHER_PLAYER), "created_private_link_invisible_to_other");
     menu.setCreatePrivate(false);
     menu.create("Open access");
     check(directory().sortedVisible(me()).stream().filter(l -> l.name().equals("Open access")).count() == 1, "public_name_join_is_idempotent");
     player.closeContainer();
     advance();
    }
    case 4 -> {
     level.setBlockAndUpdate(R, ModBlocks.CRAFTING_RIPPER.get().defaultBlockState());
     ripper = (CraftingRipperBlockEntity) level.getBlockEntity(R);
     check(ripper != null, "ripper_placed");
     level.setBlockAndUpdate(CELL, AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
     ((EnergyCellBlockEntity) level.getBlockEntity(CELL)).injectAEPower(1_000_000, Actionable.MODULATE);
     advance();
    }
    case 5 -> {
     check(ripper.getMainNode().getNode() != null, "ripper_node_ready");
     report.put("ripperIdlePower", ((appeng.me.GridNode) ripper.getMainNode().getNode()).getIdlePowerUsage());
     check(((appeng.me.GridNode) ripper.getMainNode().getNode()).getIdlePowerUsage() == 0, "ripper_has_no_idle_power");
     check(ripper.getMainNode().isActive(), "ripper_online_without_own_power");
     var pattern = encodedPattern();
     check(ripper.getLogic().acceptsPattern(pattern), "probe_pattern_is_supported");
     var inventory = ripper.getLogic().getPatternInv();
     int before = inventory.size();
     // Third-party upload and migration keep the slot index they read; 40 sits past the empty tail row.
     var remainder = inventory.insertItem(40, pattern.copy(), false);
     check(remainder.isEmpty(), "third_party_insert_accepted");
     check(!inventory.getStackInSlot(40).isEmpty(), "third_party_insert_landed_in_requested_slot");
     check(inventory.size() > before, "insert_grew_the_pattern_tail");
     ripper.getLogic().updatePatterns();
     check(ripper.getLogic().getAvailablePatterns().stream().anyMatch(p -> p.getPrimaryOutput().what().equals(expectedOutput)),
             "inserted_pattern_is_advertised");
     var rejected = inventory.insertItem(41, new ItemStack(Items.DIRT), false);
     check(rejected.getCount() == 1, "unsupported_item_returned_not_dropped");
     check(inventory.getStackInSlot(41).isEmpty(), "unsupported_item_not_stored");
     report.put("patternSlotAfterInsert", 40);
     report.put("patternInventorySize", inventory.size());
     player.connection.teleport(1.5, 102, 2.5, 180, 20);
     advance();
    }
    case 6 -> {
     // A second open uses the same native menu the client probe will drive.
     first.open(player);
     ready = true; save();
    }
   }
  } catch (Throwable ex) { fail(ex); }
 }
 static void finish() { report.put("passed", true); save(); ProbeState.finished = true; }
 static void fail(Throwable ex) { report.put("failure", ex.toString()); save(); ex.printStackTrace(); ProbeState.finished = true; ready = true; }
}
