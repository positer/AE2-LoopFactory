package com.example.ae2lightoptimizer.mixin;

@org.spongepowered.asm.mixin.Mixin(value = appeng.me.GridConnection.class, remap = false)
public abstract class OmniConnectionEnergyMixin {
    @org.spongepowered.asm.mixin.Shadow private appeng.me.GridNode sideA;
    @org.spongepowered.asm.mixin.Shadow private appeng.me.GridNode sideB;
    @org.spongepowered.asm.mixin.injection.Inject(method = "propagateChannelsUpwards", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
    private void ae2lf$freeLink(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> result) {
        if (sideA.getOwner() instanceof com.example.ae2lightoptimizer.omni.OmniNetworkNode
                && sideB.getOwner() instanceof com.example.ae2lightoptimizer.omni.OmniNetworkNode) result.setReturnValue(0);
    }
}
