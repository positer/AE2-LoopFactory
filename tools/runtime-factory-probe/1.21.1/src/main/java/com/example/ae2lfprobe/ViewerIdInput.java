package com.example.ae2lfprobe;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Actual viewer discovery and read-only drag observations; never seeds a dragged ingredient. */
final class ViewerIdInput {
    static void search(String viewer,String text) {
        if(viewer.equals("jei")){IdInsertionJeiObserver.search(text);return;}
        call("dev.emi.emi.api.EmiApi","setSearchText",new Class<?>[]{String.class},text);
    }
    static double[] find(Minecraft mc,String viewer,Rect2i area,String id) {
        if(viewer.equals("jei"))return IdInsertionJeiObserver.find(mc,area,id);
        try {
            var api=Class.forName("dev.emi.emi.api.EmiApi");var hover=api.getMethod("getHoveredStack",int.class,int.class,boolean.class);
            for(int y=12;y<mc.screen.height-24;y+=6)for(int x=6;x<mc.screen.width-6;x+=6) {
                if(x>=area.getX()&&x<area.getX()+area.getWidth()&&y>=area.getY()&&y<area.getY()+area.getHeight())continue;
                Object interaction=hover.invoke(null,x,y,false);
                Object ingredient=interaction.getClass().getMethod("getStack").invoke(interaction);
                var stacks=(List<?>)Class.forName("dev.emi.emi.api.stack.EmiIngredient").getMethod("getEmiStacks").invoke(ingredient);
                if(stacks.size()!=1)continue;
                var item=(ItemStack)Class.forName("dev.emi.emi.api.stack.EmiStack").getMethod("getItemStack").invoke(stacks.getFirst());
                if(id.equals("minecraft:stone")&&item.is(Items.STONE))return new double[]{x,y};
                if(id.equals("minecraft:water")) {
                    var fluid=Class.forName("dev.emi.emi.api.stack.EmiStack").getMethod("getKeyOfType",Class.class).invoke(stacks.getFirst(),net.minecraft.world.level.material.Fluid.class);
                    if(fluid==net.minecraft.world.level.material.Fluids.WATER)return new double[]{x,y};
                }
            }
            return null;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("actual EMI sidebar inspection",e);}
    }
    static Map<String,Object> dragState(String viewer) {
        if(viewer.equals("jei"))return Map.of("observedAt","actual JEI input route","directGhostAcceptCalled",false);
        try {
            var dragged=Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("draggedStack").get(null);
            boolean nonempty=dragged!=null&&!(boolean)Class.forName("dev.emi.emi.api.stack.EmiIngredient").getMethod("isEmpty").invoke(dragged);
            if(!nonempty)throw new IllegalStateException("EMI mouse events did not create an actual dragged stack");
            return Map.of("actualEmiDraggedStackNonempty",true,"draggedStackWasNeverAssignedByProbe",true);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static Object call(String owner,String name,Class<?>[] types,Object...args) {
        try{return Class.forName(owner).getMethod(name,types).invoke(null,args);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private ViewerIdInput(){}
}
