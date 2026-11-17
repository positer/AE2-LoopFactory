package com.example.ae2lightoptimizer.mixin;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.misc.CondenserBlockEntity;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the Matter Condenser a grid node so it can join the network as a one-channel member while the
 * Loop Card is installed. The node only exists to reach the network; the condenser logic stays native.
 */
@Mixin(value = CondenserBlockEntity.class, remap = false)
public abstract class CondenserLoopMixin implements IInWorldGridNodeHost {
    @Unique
    private IManagedGridNode ae2lf$node;

    @Inject(method = "onLoad", at = @At("TAIL"))
    private void ae2lf$createNode(CallbackInfo callback) {
        var self = (CondenserBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) return;
        ae2lf$node = GridHelper.createManagedNode(self, new IGridNodeListener<CondenserBlockEntity>() {
            @Override
            public void onSaveChanges(CondenserBlockEntity owner, IGridNode node) {
                owner.saveChanges();
            }
        }).setInWorldNode(true).setTagName("ae2lfCondenserNode")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setVisualRepresentation(self.getBlockState().getBlock());
        ae2lf$node.create(self.getLevel(), self.getBlockPos());
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void ae2lf$destroyNode(CallbackInfo callback) {
        if (ae2lf$node != null) {
            ae2lf$node.destroy();
            ae2lf$node = null;
        }
    }

    @Override
    @Nullable
    public IGridNode getGridNode(Direction side) {
        return ae2lf$node == null ? null : ae2lf$node.getNode();
    }

    /** The managed node, for the server tick driver. */
    @Unique
    public IManagedGridNode ae2lf$node() {
        return ae2lf$node;
    }
}
