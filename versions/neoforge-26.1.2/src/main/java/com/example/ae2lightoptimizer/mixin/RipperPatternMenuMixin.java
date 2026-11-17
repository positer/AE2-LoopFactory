package com.example.ae2lightoptimizer.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternProviderMenu;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the Ripper uses a bounded menu window; every other provider keeps AE2's exact inventory. */
@Mixin(value = PatternProviderMenu.class, remap = false)
public abstract class RipperPatternMenuMixin {
    // Soft: addons such as ExtendedAE rewrite this constructor, so a missing call site must not
    // abort mod loading; the ripper then falls back to AE2's own pattern inventory.
    @Redirect(method = "<init>", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderLogic;getPatternInv()Lappeng/api/inventories/InternalInventory;"))
    private InternalInventory ae2lf$patternViewport(PatternProviderLogic logic) {
        return (Object)this instanceof CraftingRipperMenu menu ? menu.createPatternViewport(logic) : logic.getPatternInv();
    }
}
