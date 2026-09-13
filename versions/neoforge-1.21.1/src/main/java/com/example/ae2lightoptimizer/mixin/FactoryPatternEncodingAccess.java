package com.example.ae2lightoptimizer.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PatternEncodingTermMenu.class)
public interface FactoryPatternEncodingAccess {
    @Invoker("encodePattern") ItemStack ae2lightoptimizer$encodeNativeRecipe();
}
