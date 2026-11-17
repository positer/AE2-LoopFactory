package com.example.ae2lightoptimizer.client;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.widgets.Scrollbar;
import java.util.function.IntConsumer;
import net.minecraft.client.renderer.Rect2i;

/** Rotates AE2's real scrollbar and track; coordinates are mapped back for its native input logic. */
public final class LoopRateSlider implements ICompositeWidget {
    private final Scrollbar nativeSlider = new Scrollbar(Scrollbar.BIG);
    private final IntConsumer onChanged;
    private int x, y, width = 80;

    public LoopRateSlider(IntConsumer onChanged) { this.onChanged=onChanged; nativeSlider.setPosition(new Point(0,0)); }
    public void setRange(int min, int max) { nativeSlider.setRange(min,max,1); }
    public void setValue(int value) { nativeSlider.setCurrentScroll(value); }
    public int getValue() { return nativeSlider.getCurrentScroll(); }
    @Override public void setPosition(Point position) { x=position.getX(); y=position.getY(); }
    @Override public void setSize(int width, int height) { this.width=width; nativeSlider.setSize(12,width); }
    @Override public Rect2i getBounds() { return new Rect2i(x,y,width,12); }
    private Point vertical(Point p) { return new Point(12-(p.getY()-y),p.getX()-x); }
    private void changed(int before) { if (before!=getValue()) onChanged.accept(getValue()); }
    @Override public boolean onMouseDown(Point p,int button) { int before=getValue();boolean result=nativeSlider.onMouseDown(vertical(p),button);changed(before);return result; }
    @Override public boolean onMouseDrag(Point p,int button) { int before=getValue();boolean result=nativeSlider.onMouseDrag(vertical(p),button);changed(before);return result; }
    @Override public boolean onMouseUp(Point p,int button) { return nativeSlider.onMouseUp(vertical(p),button); }
    @Override public boolean wantsAllMouseUpEvents() { return true; }
    @Override public boolean onMouseWheel(Point p,double delta) { int before=getValue();boolean result=nativeSlider.onMouseWheel(vertical(p),delta);changed(before);return result; }
    @Override public void tick() { int before=getValue();nativeSlider.tick();changed(before); }

    @Override public void drawBackgroundLayer(net.minecraft.client.gui.GuiGraphics graphics,Rect2i bounds,Point mouse) {
        var pose=graphics.pose();
        pose.pushPose();
        try {
            pose.translate(bounds.getX()+x,bounds.getY()+y+12,0); pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
            var track=appeng.client.gui.style.Blitter.texture(appeng.core.AppEng.makeId("textures/guis/patternaccessterminal.png"));
            track.src(178,17,6,3).dest(3,-1,6,3).blit(graphics);
            track.src(178,53,6,1).dest(3,2,6,width-2).blit(graphics);
            track.src(178,106,6,1).dest(3,width,6,1).blit(graphics);
        } finally { pose.popPose(); }
    }

    @Override public void drawForegroundLayer(net.minecraft.client.gui.GuiGraphics graphics,Rect2i bounds,Point mouse) {
        var pose=graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x,y+12,0); pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
            nativeSlider.drawForegroundLayer(graphics,bounds,vertical(mouse));
        } finally { pose.popPose(); }
    }
}
