package com.example.ae2lightoptimizer.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import com.example.ae2lightoptimizer.omni.OmniConnectorMenu;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OmniConnectorScreen extends AEBaseScreen<OmniConnectorMenu> {
    private EditBox name;
    private final AE2Button[] choices = new AE2Button[OmniConnectorMenu.PAGE_SIZE];
    private final FactoryIconButton previous, next, unlink;
    public OmniConnectorScreen(OmniConnectorMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("createLink", new FactoryIconButton(appeng.util.Icon.ENTER, "gui.ae2lightoptimizer.omni.create", () -> {
            if (name != null) menu.create(name.getValue());
        }));
        widgets.add("previous", previous = new FactoryIconButton(appeng.util.Icon.ARROW_LEFT, "gui.ae2lightoptimizer.omni.previous", () -> menu.setPage(menu.page - 1)));
        widgets.add("next", next = new FactoryIconButton(appeng.util.Icon.ARROW_RIGHT, "gui.ae2lightoptimizer.omni.next", () -> menu.setPage(menu.page + 1)));
        widgets.add("unlink", unlink = new FactoryIconButton(appeng.util.Icon.CLEAR, "gui.ae2lightoptimizer.omni.unlink", () -> menu.select("")));
        for (int i = 0; i < choices.length; i++) {
            int row = i;
            choices[i] = new AE2Button(Component.empty(), button -> {
                var entries = menu.entries(); if (row < entries.size()) menu.select(entries.get(row).id());
            });
            widgets.add("channel" + i, choices[i]);
        }
    }
    @Override protected void init() {
        String value = name == null ? "" : name.getValue();
        super.init();
        name = new EditBox(font, leftPos + 8, topPos + 40, 208, 16, Component.translatable("gui.ae2lightoptimizer.omni.name"));
        name.setMaxLength(com.example.ae2lightoptimizer.network.OmniLinkDirectory.MAX_NAME_LENGTH);
        name.setHint(Component.translatable("gui.ae2lightoptimizer.omni.name"));
        name.setValue(value); addRenderableWidget(name);
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        var entries = menu.entries();
        for (int i = 0; i < choices.length; i++) {
            var button = choices[i]; button.visible = i < entries.size();
            if (!button.visible) continue;
            var entry = entries.get(i);
            button.active = !entry.id().equals(menu.selectedId);
            String label = entry.name() + " (" + entry.endpoints() + ")";
            String shortened = font.plainSubstrByWidth(label, 210);
            if (!shortened.equals(label)) shortened = font.plainSubstrByWidth(label, 202) + "...";
            button.setMessage(Component.literal(shortened)); button.setTooltip(Tooltip.create(Component.literal(label)));
        }
        previous.active = menu.page > 0; next.active = menu.page + 1 < menu.pages; unlink.active = !menu.selectedId.isEmpty();
        var current = menu.selectedName.isEmpty() ? Component.translatable("gui.ae2lightoptimizer.omni.none") : Component.literal(menu.selectedName);
        setTextContent("selected", Component.literal(font.plainSubstrByWidth(Component.translatable("gui.ae2lightoptimizer.omni.selected", current).getString(), 230)));
        setTextContent("page", Component.translatable(menu.invalidName ? "gui.ae2lightoptimizer.omni.invalid" : "gui.ae2lightoptimizer.omni.page", menu.page + 1, menu.pages));
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (name != null && name.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) { menu.create(name.getValue()); return true; }
            if (name.keyPressed(event)) return true;
            if (minecraft.options.keyInventory.matches(event)) return true;
        }
        return super.keyPressed(event);
    }
}
