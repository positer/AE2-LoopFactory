package com.example.ae2lightoptimizer.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.menu.implementations.CondenserMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity;
import com.example.ae2lightoptimizer.storage.LoopStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve the native menu; only the loop machine accepts loop cells as its template. */
@Mixin(value = CondenserMenu.class, remap = false)
public abstract class LoopCondenserMenuMixin {
    @Shadow @Final private CondenserBlockEntity condenser;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "appeng/menu/slot/RestrictedInputSlot"))
    private RestrictedInputSlot ae2lf$template(RestrictedInputSlot.PlacableItemType type,
                                               InternalInventory inventory, int slot) {
        if (condenser instanceof LoopCondenserBlockEntity
                && type == RestrictedInputSlot.PlacableItemType.STORAGE_COMPONENT) {
            return new RestrictedInputSlot(type, inventory, slot) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof LoopStorageCellItem || super.mayPlace(stack);
                }
            };
        }
        return new RestrictedInputSlot(type, inventory, slot);
    }
}
