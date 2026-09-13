package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.client.FactoryEditorScreen;
import com.example.ae2lightoptimizer.factory.FactorySyntaxHighlighter;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiLineEditBox.class)
public abstract class FactoryEditorHighlightMixin {
    @Shadow @Final private Font font;
    @Shadow @Final private MultilineTextField textField;
    @Unique private String ae2lf$highlightSource;
    @Unique private java.util.List<FactorySyntaxHighlighter.Span> ae2lf$highlightSpans = java.util.List.of();

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void ae2lf$highlight(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                 CallbackInfo callback) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof FactoryEditorScreen)) return;
        String value = textField.value();
        var self = (MultiLineEditBox) (Object) this;
        if (value.isEmpty() && !self.isFocused()) return;
        callback.cancel();
        if (!value.equals(ae2lf$highlightSource)) {
            ae2lf$highlightSource = value;
            ae2lf$highlightSpans = FactorySyntaxHighlighter.highlight(value);
        }
        int padding = 4;
        double scroll = ((AbstractScrollArea) (Object) this).scrollAmount();
        int x = self.getX() + padding;
        // The native parent already translates by -scroll and owns the viewport scissor.
        // Draw in content coordinates; subtract scroll only for visibility checks.
        int y = self.getY() + padding;
        Object selected = textField.getSelected();
        int selectionStart = lineIndex(selected, "beginIndex");
        int selectionEnd = lineIndex(selected, "endIndex");
        for (int lineIndex = 0; lineIndex < textField.getLineCount(); lineIndex++) {
            if (y + font.lineHeight - scroll >= self.getY()
                    && y - scroll <= self.getY() + self.getHeight()) {
                Object line = textField.getLineView(lineIndex);
                int begin = lineIndex(line, "beginIndex");
                int end = lineIndex(line, "endIndex");
                String text = value.substring(begin, end);
                int selectionLeft = Math.max(begin, selectionStart);
                int selectionRight = Math.min(end, selectionEnd);
                if (selectionRight > selectionLeft) {
                    int selectionX = x + font.width(value.substring(begin, selectionLeft));
                    int selectionWidth = font.width(value.substring(selectionLeft, selectionRight));
                    graphics.fill(selectionX, y - 1, selectionX + selectionWidth, y + font.lineHeight,
                            0x803A5A88);
                }
                int cursorX = x;
                for (var span : FactorySyntaxHighlighter.slice(ae2lf$highlightSpans, begin, end)) {
                    String token = text.substring(span.start(), span.end());
                    graphics.text(font, token, cursorX, y, syntaxColor(span.kind()), false);
                    cursorX += font.width(token);
                }
                int cursor = textField.cursor();
                if (self.isFocused() && textField.getLineAtCursor() == lineIndex) {
                    int caretX = x + font.width(value.substring(begin, cursor));
                    graphics.fill(caretX, y - 1, caretX + 1, y + font.lineHeight, 0xFFD0D0D0);
                }
            }
            y += font.lineHeight;
        }
    }

    private static int lineIndex(Object line, String method) {
        try {
            return ((Number) line.getClass().getMethod(method).invoke(line)).intValue();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static int syntaxColor(FactorySyntaxHighlighter.Kind kind) {
        return switch (kind) {
            case KEYWORD -> 0xFF5CC8FF;
            case STRING -> 0xFFFFC857;
            case NUMBER -> 0xFFFF9E64;
            case COMMENT -> 0xFF6A9955;
            case TAG -> 0xFF4FC1B0;
            case FUNCTION -> 0xFFC586C0;
            case OPERATOR -> 0xFFFFD866;
            case RESOURCE -> 0xFF9CDCFE;
            case REFERENCE -> 0xFFFF6B9A;
            case PLAIN -> 0xFFE0E0E0;
        };
    }
}
