package com.example.ae2lightoptimizer.mixin;

@org.spongepowered.asm.mixin.Mixin(value = appeng.me.GridNode.class, remap = false)
public abstract class OmniNodeEnergyMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "propagateChannelsUpwards", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
    private void ae2lf$freeNode(boolean consumes, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> result) {
        if (((appeng.api.networking.IGridNode) (Object) this).getOwner() instanceof com.example.ae2lightoptimizer.omni.OmniNetworkNode)
            result.setReturnValue(0); // Only the energy tally; native usedChannels and allocation stay untouched.
        else if (consumes && ((appeng.api.networking.IGridNode)(Object)this).getOwner()
                instanceof com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity)
            result.setReturnValue(Math.max(0, result.getReturnValue() - 1)); // Keep downstream routing costs.
    }
}
