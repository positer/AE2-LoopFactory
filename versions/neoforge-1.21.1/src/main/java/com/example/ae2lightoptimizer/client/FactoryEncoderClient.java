package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.factory.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** Input emits server-validated actions; render only the server-filtered nearby block snapshot. */
public final class FactoryEncoderClient {
    private record ScreenLabel(float x, float y, List<String> tags) {}
    private static final List<ScreenLabel> SCREEN_LABELS = new ArrayList<>();

    private static boolean key(int key) {return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(),key)==GLFW.GLFW_PRESS;}
    public static void scroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        var mc=Minecraft.getInstance();
        if(mc.screen!=null || mc.player==null || !key(GLFW.GLFW_KEY_TAB))return;
        var hand=mc.player.getMainHandItem().getItem() instanceof FactoryEncoderItem?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        if(!(mc.player.getItemInHand(hand).getItem() instanceof FactoryEncoderItem))return;
        event.setCanceled(true);
        if(event.getScrollDeltaY()!=0)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new FactoryEncoderAction(event.getScrollDeltaY()>0?-1:1,BlockPos.ZERO,false,false,hand==InteractionHand.OFF_HAND));
    }
    public static void use(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if(!event.isUseItem() || mc.player==null || mc.screen!=null || !(mc.player.getItemInHand(event.getHand()).getItem() instanceof FactoryEncoderItem)
            || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit))return;
        if(FactoryPatternData.get(mc.player.getItemInHand(event.getHand())).factoryId().isEmpty())return;
        boolean control=key(GLFW.GLFW_KEY_LEFT_CONTROL)||key(GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shift=mc.player.isShiftKeyDown();
        if(!control && !shift)return;
        // A shift click on a non-interactive block means "clear the binding"; leave that to the
        // normal item use path instead of turning it into a tag removal packet.
        if(shift && !control && !mc.level.getBlockState(hit.getBlockPos()).hasBlockEntity())return;
        event.setCanceled(true);event.setSwingHand(true);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new FactoryEncoderAction(0,hit.getBlockPos(),true,control,event.getHand()==InteractionHand.OFF_HAND,shift));
    }
    public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if(event.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null)return;
        SCREEN_LABELS.clear();
        var stack=mc.player.getMainHandItem().getItem() instanceof FactoryEncoderItem?mc.player.getMainHandItem():mc.player.getOffhandItem();
        if(!(stack.getItem() instanceof FactoryEncoderItem))return;
        var view=stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
        if(!view.dimension().equals(mc.level.dimension().toString()))return;
        if(view.selectedPositions().isEmpty() && view.otherPositions().isEmpty() && view.machineTags().isEmpty())return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();var type=net.minecraft.client.renderer.RenderType.lines();var vertices=buffers.getBuffer(type);
        for(boolean selected:new boolean[]{false,true})for(long value:selected?view.selectedPositions():view.otherPositions()) {
            var pos=BlockPos.of(value);if(!mc.level.hasChunkAt(pos))continue;
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose,vertices, new net.minecraft.world.phys.AABB(pos).inflate(.004).move(-camera.x,-camera.y,-camera.z), selected?.2f:.4f,selected?.8f:.5f,1f,selected?1f:.35f);
        }
        var modelView=event.getModelViewMatrix();
        var projection=event.getProjectionMatrix();
        int guiWidth=mc.getWindow().getGuiScaledWidth();
        int guiHeight=mc.getWindow().getGuiScaledHeight();
        for(var machine:view.machineTags()) {
            var pos=BlockPos.of(machine.position());if(!mc.level.hasChunkAt(pos) || machine.tags().isEmpty())continue;
            var projected=new org.joml.Vector4f(
                (float)(pos.getX()+0.5-camera.x),
                (float)(pos.getY()+0.5-camera.y),
                (float)(pos.getZ()+0.5-camera.z),1f);
            projected.mul(modelView).mul(projection);
            if(projected.w<=0)continue;
            projected.div(projected.w);
            if(projected.x < -1 || projected.x > 1 || projected.y < -1 || projected.y > 1)continue;
            SCREEN_LABELS.add(new ScreenLabel(
                (projected.x*0.5f+0.5f)*guiWidth,
                (1-(projected.y*0.5f+0.5f))*guiHeight,
                machine.tags()));
        }
        buffers.endBatch(type);
    }
    public static void renderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.screen!=null)return;
        if(mc.player==null || !(mc.player.getMainHandItem().getItem() instanceof FactoryEncoderItem
                || mc.player.getOffhandItem().getItem() instanceof FactoryEncoderItem)) {
            SCREEN_LABELS.clear();
            return;
        }
        if(SCREEN_LABELS.isEmpty())return;
        var graphics=event.getGuiGraphics();
        var font=mc.font;
        var occupied=new java.util.ArrayList<int[]>();
        var merged=mergeLabels(SCREEN_LABELS);
        for(var label:merged) {
            int width=0;
            for(String tag:label.tags())width=Math.max(width,font.width(tag));
            int height=label.tags().size()*font.lineHeight;
            int left=Math.round(label.x())-width/2-3;
            int top=Math.round(label.y())-height/2-2;
            while(overlaps(occupied,left,top,left+width+6,top+height+4))top+=height+4;
            occupied.add(new int[]{left,top,left+width+6,top+height+4});
            graphics.fill(left,top,left+width+6,top+height+4,0x99000000);
            int y=top+2;
            for(String tag:label.tags()) {
                int color=tag.equals(selectedTag())?0xFF33CCFF:0xFFFFFFFF;
                graphics.drawString(font,tag,Math.round(label.x())-font.width(tag)/2,y,color,true);
                y+=font.lineHeight;
            }
        }
    }
    private static List<ScreenLabel> mergeLabels(List<ScreenLabel> labels) {
        var result=new java.util.ArrayList<ScreenLabel>();
        for(var label:labels) {
            ScreenLabel target=null;
            for(var existing:result)if(Math.abs(existing.x()-label.x())<32&&Math.abs(existing.y()-label.y())<32){target=existing;break;}
            if(target==null){result.add(new ScreenLabel(label.x(),label.y(),new java.util.ArrayList<>(label.tags())));continue;}
            var tags=new java.util.ArrayList<>(target.tags());
            for(String tag:label.tags())if(!tags.contains(tag))tags.add(tag);
            result.set(result.indexOf(target),new ScreenLabel(target.x(),target.y(),tags));
        }
        return result;
    }
    private static boolean overlaps(List<int[]> occupied,int left,int top,int right,int bottom) {
        for(int[] rect:occupied)if(left<rect[2]&&right>rect[0]&&top<rect[3]&&bottom>rect[1])return true;
        return false;
    }
    private static String selectedTag() {
        var mc=Minecraft.getInstance();
        if(mc.player==null)return "";
        var stack=mc.player.getMainHandItem().getItem() instanceof FactoryEncoderItem
            ?mc.player.getMainHandItem():mc.player.getOffhandItem();
        return stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY).selected();
    }
    private FactoryEncoderClient(){}
}
