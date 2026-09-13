package com.example.ae2lightoptimizer.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Native AE2 button chrome, distinct action glyphs and matching accessible tooltip labels. */
public final class FactoryIconButton extends IconButton {
    private final Icon icon;
    private final Component label;
    public FactoryIconButton(Icon icon, String labelKey, Runnable action) {
        super(button -> action.run());this.icon=icon;this.label=Component.translatable(labelKey);setMessage(label);
    }
    @Override protected Icon getIcon(){return icon;}
    @Override public List<Component> getTooltipMessage(){return List.of(label);}
}
