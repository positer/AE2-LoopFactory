package com.example.ae2loprobe;

import java.util.function.BiConsumer;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Loaded by the runtime probe only when Crystal Science is installed. */
final class CrystalScienceAudit {
    private CrystalScienceAudit() {}

    static void run(ServerLevel level, BiConsumer<String, Boolean> check) {
        var seed = com.example.ae2lightoptimizer.compat.CrystalScienceCompat.SEED.get();
        var pure = com.example.ae2lightoptimizer.compat.CrystalScienceCompat.PURE.get();
        var stack = new ItemStack(seed, 3);
        var half = io.github.lounode.ae2cs.common.item.CrystalSeedItem.grow(stack, seed.getOvergrowTick() / 2);
        check.accept("seed_half_grown", half.is(seed) && seed.getGrowProcess(half) >= 0.49f);
        var mature = io.github.lounode.ae2cs.common.item.CrystalSeedItem.grow(half, seed.getOvergrowTick());
        check.accept("seed_matures_to_three_pure", mature.is(pure) && mature.getCount() == 3);
        var blockRecipe = (net.minecraft.world.item.crafting.CraftingRecipe) level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ae2lpt:loop_crystal_block")).orElseThrow().value();
        for (int mask = 0; mask < 16; mask++) {
            var stacks = new java.util.ArrayList<ItemStack>();
            for (int i = 0; i < 4; i++) stacks.add(new ItemStack((mask & (1 << i)) == 0 ? pure : ModItems.LOOP_CRYSTAL.get()));
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, stacks);
            check.accept("mixed_crystal_block_" + mask, blockRecipe.matches(input, level)
                    && blockRecipe.assemble(input, level.registryAccess()).is(ModItems.LOOP_CRYSTAL_BLOCK.get()));
        }
    }
}
