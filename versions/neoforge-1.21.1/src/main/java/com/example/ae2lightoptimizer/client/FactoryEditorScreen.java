package com.example.ae2lightoptimizer.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import com.example.ae2lightoptimizer.factory.FactoryCompletion;
import com.example.ae2lightoptimizer.factory.FactoryCompiler;
import com.example.ae2lightoptimizer.factory.FactoryEditorMenu;
import com.example.ae2lightoptimizer.mixin.MultiLineEditBoxAccess;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class FactoryEditorScreen extends AEBaseScreen<FactoryEditorMenu> {
    private MultiLineEditBox editor;
    private net.minecraft.client.gui.components.AbstractWidget statusHover;
    private FactoryIconButton saveButton, uploadButton;
    private String synced = "";
    private int resourceClickButton = -1;

    public FactoryEditorScreen(FactoryEditorMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        if(menu.isPanel()) {
            widgets.add("recipePage", new FactoryIconButton(appeng.client.gui.Icon.TAB_CRAFTING, "gui.ae2lightoptimizer.factory.recipe_page", menu::recipePage));
            widgets.add("upload", uploadButton = new FactoryIconButton(appeng.client.gui.Icon.ARROW_UP, "gui.ae2lightoptimizer.factory.upload", () -> {
                if(editor!=null) menu.saveCode(editor.getValue());
                minecraft.setScreen(new FactoryUploadScreen(this,menu));
            }));
        }
        widgets.add("save", saveButton = new FactoryIconButton(appeng.client.gui.Icon.ENTER, "gui.ae2lightoptimizer.factory.save", () -> {
            if (editor != null) menu.saveCode(editor.getValue());
        }));
    }

    @Override protected void init() {
        String previous = editor == null ? menu.code() : editor.getValue();
        super.init();
        editor = new MultiLineEditBox(font, leftPos + 8, topPos + 30, 344, 96,
                Component.literal("import A\nget minecraft::item from A"), Component.translatable("gui.ae2lightoptimizer.factory.code_page"));
        editor.setCharacterLimit(FactoryCompiler.MAX_SOURCE_LENGTH);
        editor.setValue(previous);
        addRenderableWidget(editor);
        statusHover=new net.minecraft.client.gui.components.AbstractWidget(leftPos+8,topPos+128,260,14,Component.empty()) {
            @Override protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float delta) {}
            @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){defaultButtonNarrationText(output);}
        };
        addRenderableWidget(statusHover);
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (editor != null && !synced.equals(menu.code())) {
            // Server synchronisation may seed an untouched editor but must never overwrite local typing.
            if (editor.getValue().equals(synced)) editor.setValue(menu.code());
            synced = menu.code();
        }
        saveButton.active = menu.hasFactoryPattern();
        if(uploadButton!=null) uploadButton.active = menu.hasRecipePattern();
        setTextContent("dialog_title", Component.translatable(menu.titleKey));
        var fullStatus=FactoryMessages.status(menu.displayStatus());
        String text=fullStatus.getString();String shown=font.plainSubstrByWidth(text,250);
        if(!shown.equals(text))shown=font.plainSubstrByWidth(text,240)+"...";
        setTextContent("status",Component.literal(shown).withStyle(menu.displayStatus().startsWith("Line ")?net.minecraft.ChatFormatting.RED:net.minecraft.ChatFormatting.DARK_GRAY));
        if(statusHover!=null){statusHover.setMessage(fullStatus);statusHover.setTooltip(text.isEmpty()?null:net.minecraft.client.gui.components.Tooltip.create(fullStatus));}
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editor != null && editor.isFocused()) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                applyIndent(shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                insertIndentedNewline();
                return true;
            }
        }
        // The code editor owns the inventory key as text. Escape remains the only close key.
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void drawBG(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int mx, int my, float delta) {
        super.drawBG(graphics, x, y, mx, my, delta);
        for (var slot : menu.slots) if (slot.isActive())
            appeng.client.gui.Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
    }

    /** The single code editor shared by terminal and encoder-panel code pages. */
    public net.minecraft.client.renderer.Rect2i getCodeDropArea() {
        return editor == null ? new net.minecraft.client.renderer.Rect2i(0, 0, 0, 0)
                : new net.minecraft.client.renderer.Rect2i(editor.getX(), editor.getY(), editor.getWidth(), editor.getHeight());
    }

    public boolean canInsertCode(String text) {
        if (editor == null || !editor.visible || !editor.active || text.isEmpty() || text.length() > 4096) return false;
        var field = textField();
        return (long) field.value().length() - field.getSelectedText().length() + text.length() <= field.characterLimit();
    }

    /** Insert the complete identifier at the existing caret, replacing only the native selection. */
    public boolean insertCodeAtCursor(String text) {
        if (!canInsertCode(text)) return false;
        var field = textField();
        int limit = field.characterLimit();
        int selected = field.getSelectedText().length();
        // Native insertion truncation does not credit the selected characters before replacing them.
        // Preflight the final size, then allow that replacement without truncating an identifier.
        field.setCharacterLimit((int) Math.min(Integer.MAX_VALUE, (long) limit + selected));
        try {
            field.insertText(text);
        } finally {
            field.setCharacterLimit(limit);
        }
        setFocused(editor);
        editor.setFocused(true);
        return true;
    }

    private boolean insertCarriedResource(double mouseX, double mouseY, int button) {
        if (editor == null || !editor.visible || !editor.active || !editor.isMouseOver(mouseX, mouseY)
                || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) return false;
        // Only the cursor-held stack participates; an encoder in the player's hand must not hijack caret clicks.
        var carried = menu.getCarried();
        if (carried.isEmpty()) return false;
        String text;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            text = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        } else {
            var view = ContainerResourceIds.inspect(carried);
            text = view.container() ? String.join(" & ", new java.util.LinkedHashSet<>(view.contents()))
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        }
        // Empty containers and insufficient text capacity consume the UI click without altering text or items.
        insertCodeAtCursor(text);
        resourceClickButton = button;
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insertCarriedResource(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == resourceClickButton) { resourceClickButton = -1; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == resourceClickButton) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    private MultilineTextField textField() {
        return ((MultiLineEditBoxAccess) editor).ae2lf$textField();
    }

    private void applyIndent(boolean outdent) {
        var field = textField();
        int cursor = field.cursor();
        var edit = FactoryCompletion.indent(field.value(), cursor, cursor, outdent);
        field.setValue(edit.text());
        field.seekCursor(Whence.ABSOLUTE, edit.cursor());
    }

    private void insertIndentedNewline() {
        var field = textField();
        int cursor = field.cursor();
        var edit = FactoryCompletion.newline(field.value(), cursor, cursor);
        field.setValue(edit.text());
        field.seekCursor(Whence.ABSOLUTE, edit.cursor());
    }
}
