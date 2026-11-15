package com.example.ae2lightoptimizer.mixin.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import com.example.ae2lightoptimizer.compat.OptimizerFlowContext;
import com.example.ae2lightoptimizer.integration.OptimizerChannelCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks Thunderbolt's max-flow solve with this network's optimizer decision. */
@Mixin(targets = "com.moakiee.thunderbolt.core.channel.BorrowedCapacityCalculator", remap = false)
public abstract class ThunderboltBorrowedCapacityMixin {
    // solve() returns Thunderbolt's Result record, so the callback must be returnable.
    @Inject(method = "solve", at = @At("HEAD"), require = 0)
    private static void ae2lf$markSolve(IGrid grid, java.util.List<?> sources, java.util.Set<?> network,
            ChannelMode mode, CallbackInfoReturnable<Object> cir) {
        OptimizerFlowContext.mark(OptimizerChannelCapacity.enabled(grid));
    }
}
