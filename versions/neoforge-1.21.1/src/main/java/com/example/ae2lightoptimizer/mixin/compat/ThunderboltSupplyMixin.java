package com.example.ae2lightoptimizer.mixin.compat;

import appeng.api.networking.IGrid;
import com.example.ae2lightoptimizer.compat.OptimizerFlowContext;
import com.example.ae2lightoptimizer.integration.OptimizerChannelCapacity;
import com.example.ae2lightoptimizer.network.ChannelCapacityPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies AE2LF's single capacity doubling to Thunderbolt's overloaded-controller network. Its max-flow
 * takes per-controller supply from a static helper, so doubling the native node and connection capacities
 * (what AE2's own BFS needs) would leave an overloaded network unchanged; the supply is the quantity that
 * has to grow for one more overloaded controller's worth of channels to become usable.
 */
@Mixin(targets = "com.moakiee.thunderbolt.core.channel.HighCapacityChannelSupport", remap = false)
public abstract class ThunderboltSupplyMixin {
    @Inject(method = "supplyPerController", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ae2lf$doubleSupply(int cableCapacityFactor, CallbackInfoReturnable<Integer> cir) {
        if (OptimizerFlowContext.doubled()) cir.setReturnValue(ChannelCapacityPolicy.effective(cir.getReturnValue(), true));
    }
    @Inject(method = "calculateHighCapacityNetworkCapacity", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ae2lf$doubleNetworkTotal(IGrid grid, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChannelCapacityPolicy.effective(cir.getReturnValue(), OptimizerChannelCapacity.enabled(grid)));
    }
}
