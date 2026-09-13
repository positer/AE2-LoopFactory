package com.example.ae2lfprobe;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Native callback entrypoints, and the pinned NeoForge drag dispatch chain for unfocused hidden windows. */
final class NativeIdInput {
    static void move(Minecraft mc,double x,double y) {
        invoke(mc,"onMove",new Class<?>[]{long.class,double.class,double.class},mc.getWindow().handle(),
            x*mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth(),
            y*mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight());
        mc.screen.mouseMoved(x,y);mc.screen.afterMouseMove();
    }
    static void press(Minecraft mc,double x,double y,int button){move(mc,x,y);button(mc,button,1);}
    static void release(Minecraft mc,double x,double y,int button){move(mc,x,y);button(mc,button,0);}
    static void click(Minecraft mc,double x,double y,int button){press(mc,x,y,button);release(mc,x,y,button);}
    private static void button(Minecraft mc,int button,int action) {
        invoke(mc,"onButton",new Class<?>[]{long.class,net.minecraft.client.input.MouseButtonInfo.class,int.class},mc.getWindow().handle(),new net.minecraft.client.input.MouseButtonInfo(button,0),action);
    }
    static void drag(Minecraft mc,double x,double y,int button,double dx,double dy) {
        move(mc,x,y);var screen=mc.screen;
        // Matches MouseHandler.handleAccumulatedMovement's actual pre/screen/post ordering.
        // No fake focus, no direct insertion/resolver/ghost target calls, no real cursor movement.
        var event=new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,0));
        if(net.neoforged.neoforge.client.ClientHooks.onScreenMouseDragPre(screen,event,dx,dy))return;
        if(screen.mouseDragged(event,dx,dy))return;
        net.neoforged.neoforge.client.ClientHooks.onScreenMouseDragPost(screen,event,dx,dy);
    }
    static Map<String,Object> contents(ItemStack source) {
        if(source.isEmpty())return Map.of("empty",true);
        var stack=source.copy();var result=new LinkedHashMap<String,Object>();
        var access=net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack);
        var fluids=access.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
        result.put("fluidCapability",fluids!=null);var tanks=new ArrayList<Map<String,Object>>();
        if(fluids!=null)for(int i=0;i<fluids.size();i++) {
            var fluid=fluids.getResource(i);
            tanks.add(Map.of("index",i,"id",net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),"amount",fluids.getAmountAsLong(i),"capacity",fluids.getCapacityAsLong(i,fluid)));
        }
        result.put("tanks",tanks);
        var energy=access.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.ITEM);
        result.put("energyCapability",energy!=null);
        if(energy!=null) {result.put("energyStored",energy.getAmountAsLong());result.put("energyCapacity",energy.getCapacityAsLong());}
        var items=access.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.ITEM);
        result.put("itemCapability",items!=null);var itemSlots=new ArrayList<Map<String,Object>>();
        if(items!=null)for(int i=0;i<items.size();i++) {
            var item=items.getResource(i);
            itemSlots.add(Map.of("index",i,"id",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),"amount",items.getAmountAsLong(i)));
        }
        result.put("itemSlots",itemSlots);
        var cell=appeng.api.storage.StorageCells.getCellInventory(stack,null);
        result.put("aeCellInventory",cell!=null);
        var stored=new ArrayList<Map<String,Object>>();
        if(cell!=null)for(var entry:cell.getAvailableStacks()) {
            if(entry.getLongValue()>0)stored.add(Map.of("keyType",entry.getKey().getType().getId().toString(),
                    "id",entry.getKey().getId().toString(),"amount",entry.getLongValue()));
        }
        stored.sort(Comparator.comparing(s->s.get("keyType").toString()+"/"+s.get("id").toString()));
        result.put("aeCellContents",stored);
        return result;
    }
    static void capture(Minecraft mc,Path path) {
        net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{try(image){image.writeToFile(path);}catch(Exception e){throw new IllegalStateException("actual framebuffer capture",e);}});
    }
    private static void invoke(Minecraft mc,String name,Class<?>[] types,Object...args) {
        try {var method=net.minecraft.client.MouseHandler.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(mc.mouseHandler,args);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("native mouse callback "+name,error);}
    }
    private NativeIdInput(){}
}
