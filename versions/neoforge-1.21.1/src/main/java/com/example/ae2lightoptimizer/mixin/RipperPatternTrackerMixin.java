package com.example.ae2lightoptimizer.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.menu.RipperPatternTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker", remap = false)
public abstract class RipperPatternTrackerMixin implements RipperPatternTracker {
    @Shadow @Final private PatternContainer container;
    @Shadow @Final private long serverId;
    @Override public long ae2lf$inventoryId() { return serverId; }
    @Shadow @Final private InternalInventory server;
    @Shadow @Final private InternalInventory client;
    @Override public boolean ae2lf$patternSizeChanged() {
        return container instanceof CraftingRipperBlockEntity && server.size() != client.size();
    }
}
