package com.example.ae2lfprobe;

import appeng.core.definitions.AEItems;
import appeng.hooks.WrenchHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exercises the actual AE2 hook, rather than calling a dismantling method directly. */
final class MachineWrenchAudit {
    static final String[] IDS = {"recipe_ring_solver_terminal", "supercomputing_crafting_optimizer_interface",
            "crafting_ripper", "loop_factory_pattern_provider", "loop_factory_network_terminal", "loop_factory_interface_cable"};
    static boolean done;
    static BlockPos pos(int index) { return new BlockPos(1536 + index * 4, 100, 640); }
    static void place(ServerLevel level) {
        for (int i = 0; i < IDS.length; i++) {
            var p = pos(i);
            level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
            level.setBlockAndUpdate(p, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2lightoptimizer:" + IDS[i])).defaultBlockState());
        }
    }
    static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    static long count(ServerPlayer player, net.minecraft.world.item.Item item) {
        long total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
    static void verify(ServerLevel level, ServerPlayer player) throws Exception {
        if (done) return;
        var saved = player.getMainHandItem().copy();
        boolean shift = player.isShiftKeyDown();
        var rows = new ArrayList<String>();
        try {
            player.setShiftKeyDown(true);
            var certus = AEItems.CERTUS_QUARTZ_CRYSTAL.stack();
            var fragment = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2lightoptimizer:loop_crystal_fragment")).getDefaultInstance();
            var input = CraftingInput.of(3, 3, List.of(certus.copy(), fragment.copy(), certus.copy(),
                    fragment.copy(), ItemStack.EMPTY, fragment.copy(), certus.copy(), fragment.copy(), certus.copy()));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            var output = recipe.value().assemble(input, level.registryAccess());
            check(output.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2lightoptimizer:loop_crystal"))) && output.getCount() == 4,
                    "Native empty-centre growth recipe differs");
            var encoded = appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(recipe,
                    input.items().toArray(ItemStack[]::new), output, false, false);
            var loopCard = com.example.ae2lightoptimizer.item.ModItems.LOOP_CARD.get();
            long patternsBefore = count(player, encoded.getItem()), cardsBefore = count(player, loopCard);
            for (int i = 0; i < IDS.length; i++) {
                var p = pos(i); var block = level.getBlockState(p).getBlock();
                var hit = new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
                player.setItemInHand(InteractionHand.MAIN_HAND, Items.STICK.getDefaultInstance());
                WrenchHook.onPlayerUseBlock(player, level, InteractionHand.MAIN_HAND, hit);
                check(level.getBlockState(p).getBlock() == block, "A stick dismantled " + IDS[i]);
                player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
                WrenchHook.onPlayerUseBlock(player, level, InteractionHand.OFF_HAND, hit);
                check(level.getBlockState(p).getBlock() == block, "Offhand dismantled " + IDS[i]);
                if (level.getBlockEntity(p) instanceof com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity ripper) {
                    ripper.getLogic().getPatternInv().setItemDirect(0, encoded.copy());
                    ripper.getLogic().getPatternInv().setItemDirect(40_003, encoded.copy());
                    ripper.getUpgrades().setItemDirect(0, loopCard.getDefaultInstance());
                }
                long before = count(player, block.asItem());
                var result = WrenchHook.onPlayerUseBlock(player, level, InteractionHand.MAIN_HAND, hit);
                check(result.consumesAction() && level.getBlockState(p).isAir(), "AE wrench did not dismantle " + IDS[i]);
                check(count(player, block.asItem()) == before + 1, "Machine drop count differs for " + IDS[i]);
                if (IDS[i].equals("crafting_ripper")) {
                    check(count(player, encoded.getItem()) == patternsBefore + 2 && count(player, loopCard) == cardsBefore + 1,
                            "Sparse ripper patterns or loop card lost/duplicated");
                }
                ItemStack carrier = ItemStack.EMPTY;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    var stack = player.getInventory().getItem(slot);
                    if (stack.is(block.asItem())) { carrier = stack.copyWithCount(1); stack.shrink(1); break; }
                }
                check(!carrier.isEmpty(), "No real returned machine available for replacement");
                var placement = ((net.minecraft.world.item.BlockItem) carrier.getItem()).place(
                        new net.minecraft.world.item.context.DirectionalPlaceContext(level, p, Direction.NORTH, carrier, Direction.SOUTH));
                check(placement.consumesAction() && carrier.isEmpty() && level.getBlockState(p).getBlock() == block,
                        "Native replacement failed for " + IDS[i]);
                check(WrenchHook.onPlayerUseBlock(player, level, InteractionHand.MAIN_HAND, hit).consumesAction()
                        && level.getBlockState(p).isAir() && count(player, block.asItem()) == before + 1,
                        "Second dismantle duplicated or lost " + IDS[i]);
                rows.add(IDS[i]);
            }
            Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir"), "machine-wrench-report.json"),
                    new com.google.gson.Gson().toJson(java.util.Map.of("status", "passed", "machines", rows,
                            "stickRejected", true, "offhandRejected", true, "nativeGrowthOutput", output.getCount(),
                            "nativeReplacement", true, "sparsePatternSlots", List.of(0, 40003), "ripperPatternsReturned", 2, "loopCardsReturned", 1)));
            done = true;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, saved);
            player.setShiftKeyDown(shift);
        }
    }
}
