package com.example.ae2lightoptimizer.mixin;

import net.minecraft.client.gui.components.AbstractScrollWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractScrollWidget.class)
public interface AbstractScrollWidgetAccess {
    @Accessor("scrollAmount")
    double ae2lf$scrollAmount();

    @Invoker("innerPadding")
    int ae2lf$innerPadding();
}
