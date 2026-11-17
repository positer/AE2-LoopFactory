package com.example.ae2lightoptimizer.mixin;

import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to the condenser's storage-core slot (its only template slot). */
@Mixin(value = CondenserBlockEntity.class, remap = false)
public interface CondenserStorageSlotAccessor {
    @Accessor("storageSlot")
    AppEngInternalInventory ae2lf$storageSlot();

    @Invoker("setStoredPower")
    void ae2lf$setStoredPower(double value);
}
