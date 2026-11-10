package com.example.ae2lightoptimizer.mixin;

@org.spongepowered.asm.mixin.Mixin(value = appeng.me.GridNode.class, remap = false)
public abstract class OptimizerChannelNodeMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "getMaxChannels", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
    private void ae2lf$nodeCapacity(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> result) {
        int capacity = result.getReturnValue();
        if (capacity > 0 && capacity < Integer.MAX_VALUE) result.setReturnValue(
                com.example.ae2lightoptimizer.network.ChannelCapacityPolicy.effective(capacity,
                        com.example.ae2lightoptimizer.integration.OptimizerChannelCapacity.enabled(((appeng.api.networking.IGridNode)(Object)this).getGrid())));
    }
}
