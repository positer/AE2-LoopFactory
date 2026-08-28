package com.example.ae2lightoptimizer.mixin;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void ae2lightoptimizer$decrementItems(long amount, AEKeyType keyType);
}
