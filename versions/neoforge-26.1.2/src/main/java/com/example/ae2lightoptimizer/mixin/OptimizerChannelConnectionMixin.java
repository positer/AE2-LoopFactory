package com.example.ae2lightoptimizer.mixin;

@org.spongepowered.asm.mixin.Mixin(value = appeng.me.GridConnection.class, remap = false)
public abstract class OptimizerChannelConnectionMixin {
    @org.spongepowered.asm.mixin.Shadow private appeng.me.GridNode sideB;
    @org.spongepowered.asm.mixin.injection.Inject(method = "getMaxChannels", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
    private void ae2lf$faceCapacity(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> result) {
        int capacity = result.getReturnValue();
        if (capacity > 0 && capacity < Integer.MAX_VALUE) result.setReturnValue(
                com.example.ae2lightoptimizer.network.ChannelCapacityPolicy.effective(capacity,
                        com.example.ae2lightoptimizer.integration.OptimizerChannelCapacity.enabled(sideB.getGrid())));
    }
}
