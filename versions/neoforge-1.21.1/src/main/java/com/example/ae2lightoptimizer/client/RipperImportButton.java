package com.example.ae2lightoptimizer.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Uses AE2's native button chrome, disabled rendering, hover state and arrow sprite. */
public final class RipperImportButton extends IconButton {
    private final CraftingRipperMenu menu;
    public RipperImportButton(CraftingRipperMenu menu) {
        super(button -> menu.importPatterns());
        this.menu = menu;
        setMessage(Component.translatable("gui.ae2lpt.ripper.import"));
    }
    @Override protected Icon getIcon() { return Icon.ARROW_DOWN; }
    @Override public List<Component> getTooltipMessage() {
        var lines = new ArrayList<Component>();
        lines.add(getMessage());
        lines.add(Component.translatable("gui.ae2lpt.ripper.import.sources"));
        lines.add(Component.translatable("gui.ae2lpt.ripper.import.types"));
        lines.add(Component.translatable("gui.ae2lpt.ripper.import.close"));
        if (!menu.networkAvailable) lines.add(Component.translatable("gui.ae2lpt.ripper.import.offline"));
        else if (menu.importingPatterns) lines.add(Component.translatable("gui.ae2lpt.ripper.import.running", menu.importedPatterns));
        else lines.add(Component.translatable("gui.ae2lpt.ripper.import.result", menu.importedPatterns));
        return lines;
    }
}
