package com.example.ae2lightoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface CraftingTaskProgressAccessor {
    @Accessor("value")
    long ae2lightoptimizer$getRemaining();
}
