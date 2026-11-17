package com.example.ae2lightoptimizer.mixin;

@org.spongepowered.asm.mixin.Mixin(value = appeng.me.service.PathingService.class, remap = false)
public abstract class OmniAdHocEnergyMixin {
    @org.spongepowered.asm.mixin.injection.Redirect(method = "onServerEndTick", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lappeng/me/Grid;size()I"))
    private int ae2lf$freeAdHocNodes(appeng.me.Grid grid) {
        int count = 0;
        for (var node : grid.getNodes()) if (!(node.getOwner() instanceof com.example.ae2lightoptimizer.omni.OmniNetworkNode)
                && !(node.getOwner() instanceof com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity)) count++;
        return count; // Native uses this local count only for channelsByBlocks, never for channel admission.
    }
}
