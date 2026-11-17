package com.example.ae2loprobe;

/** Drives the actual screen responders and native C2S menu packets, not server-side substitutes. */
public final class MachineAuditClient {
    public static void tick(net.minecraft.client.Minecraft minecraft) {
        if (!(minecraft.screen instanceof com.example.ae2lightoptimizer.client.LoopTickerScreen screen)) return;
        if (screen.getMenu().maxRate < 1) return;
        MachineAudit.uiLocale = minecraft.options.languageCode;
        MachineAudit.uiTitle = screen.getStyle().getText().get("dialog_title").getText().getString();
        try {
            if (MachineAudit.uiAction.equals("number")) {
                var field = screen.getClass().getDeclaredField("input"); field.setAccessible(true);
                var input = (net.minecraft.client.gui.components.EditBox)field.get(screen);
                input.setFocused(true); input.setValue("2");
                MachineAudit.uiAction = "number_sent";
            } else if (MachineAudit.uiAction.equals("slider")) {
                var field = screen.getClass().getDeclaredField("slider"); field.setAccessible(true);
                var slider = (com.example.ae2lightoptimizer.client.LoopRateSlider)field.get(screen);
                var bounds = slider.getBounds();
                var point = new appeng.client.Point(bounds.getX()+bounds.getWidth()-1,bounds.getY()+5);
                for (int i=0;i<64 && slider.getValue()<screen.getMenu().maxRate;i++) {
                    slider.onMouseDown(point,0); slider.onMouseUp(point,0);
                }
                var inputField=screen.getClass().getDeclaredField("input"); inputField.setAccessible(true);
                MachineAudit.sliderUpdatedInput = ((net.minecraft.client.gui.components.EditBox)inputField.get(screen))
                        .getValue().equals(Integer.toString(screen.getMenu().maxRate));
                MachineAudit.uiAction = "slider_sent";
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native machine UI driver failed",failure); }
    }
}
