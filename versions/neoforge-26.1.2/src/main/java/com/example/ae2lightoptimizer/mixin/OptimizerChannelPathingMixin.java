package com.example.ae2lightoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.me.service.PathingService;
import com.example.ae2lightoptimizer.block.SupercomputingCraftingOptimizerInterfaceBlockEntity;
import com.example.ae2lightoptimizer.integration.OptimizerChannelCapacity;
import com.example.ae2lightoptimizer.network.ChannelCapacityPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathingService.class, remap = false)
public abstract class OptimizerChannelPathingMixin implements OptimizerChannelCapacity {
    @Unique private final java.util.Set<IGridNode> ae2lf$optimizers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    @Override public boolean ae2lf$hasOptimizer() { return !ae2lf$optimizers.isEmpty(); }
    // Native add/remove both call repath afterwards. Update membership before that rebuild.
    @Inject(method = "addNode", at = @At("HEAD"))
    private void ae2lf$attach(IGridNode node, net.minecraft.nbt.CompoundTag savedData, CallbackInfo ci) {
        if (node.getOwner() instanceof SupercomputingCraftingOptimizerInterfaceBlockEntity) ae2lf$optimizers.add(node);
    }
    @Inject(method = "removeNode", at = @At("HEAD"))
    private void ae2lf$detach(IGridNode node, CallbackInfo ci) { ae2lf$optimizers.remove(node); }
    @Redirect(method = "calculateAdHocChannels", at = @At(value = "INVOKE", target = "Lappeng/api/networking/pathing/ChannelMode;getAdHocNetworkChannels()I"))
    private int ae2lf$adHocCapacity(ChannelMode mode) { return ChannelCapacityPolicy.effective(mode.getAdHocNetworkChannels(), ae2lf$hasOptimizer()); }
}
