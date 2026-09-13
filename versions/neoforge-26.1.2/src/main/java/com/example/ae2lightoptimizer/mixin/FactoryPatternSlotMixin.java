package com.example.ae2lightoptimizer.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.AppEngSlot;
import com.example.ae2lightoptimizer.factory.FactoryPatternItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RestrictedInputSlot.class)
public abstract class FactoryPatternSlotMixin extends AppEngSlot {
    @Shadow @Final private RestrictedInputSlot.PlacableItemType which;
    @Shadow private boolean allowEdit;
    protected FactoryPatternSlotMixin(appeng.api.inventories.InternalInventory inventory, int slot) { super(inventory, slot); }
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void ae2lightoptimizer$acceptFactoryPattern(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.getItem() instanceof FactoryPatternItem && getMenu() instanceof PatternEncodingTermMenu
                && (which == RestrictedInputSlot.PlacableItemType.BLANK_PATTERN
                || which == RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN))
            callback.setReturnValue(allowEdit && isSlotEnabled());
    }
}
