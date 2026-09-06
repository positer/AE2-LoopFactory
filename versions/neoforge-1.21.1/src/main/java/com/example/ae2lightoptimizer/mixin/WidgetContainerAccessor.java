package com.example.ae2lightoptimizer.mixin;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import java.util.Map;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only presence checks preserve upgrade panels supplied by other AE2 extensions. */
@Mixin(value = WidgetContainer.class, remap = false)
public interface WidgetContainerAccessor {
    @Accessor("widgets")
    Map<String, AbstractWidget> ae2lightoptimizer$getWidgets();

    @Accessor("compositeWidgets")
    Map<String, ICompositeWidget> ae2lightoptimizer$getCompositeWidgets();
}
