package com.example.ae2loprobe;

import net.minecraft.client.Minecraft;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import com.example.ae2lightoptimizer.client.CraftingRipperScreen;
import appeng.menu.SlotSemantics;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Files;
import java.util.LinkedHashMap;

/** Uses the production scroll handler and real client-to-server inventory-click packets. */
public final class DynamicPatternClient {
    private static int step, ticks;
    private static volatile String failure;
    private static final LinkedHashMap<String,Object> checks=new LinkedHashMap<>();
    static void observePatternAccess(Minecraft mc) {
        if(!DynamicPatternFixture.enabled || DynamicPatternFixture.highSlotObserved || !(mc.screen instanceof appeng.client.gui.me.patternaccess.PatternAccessTermScreen<?> screen))return;
        try {
            var field=appeng.client.gui.me.patternaccess.PatternAccessTermScreen.class.getDeclaredField("byId");field.setAccessible(true);
            for(var value:((java.util.Map<?,?>)field.get(screen)).values()) {
                var record=(appeng.client.gui.me.patternaccess.PatternContainerRecord)value;
                var inv=record.getInventory();
                if(inv.size()==40014 && !inv.getStackInSlot(40004).isEmpty() && !inv.getStackInSlot(377).isEmpty()) {
                    int count=0;for(var stack:inv)count+=stack.getCount();
                    DynamicPatternFixture.require(count==379,"Chunked native client mirror lost pattern entries");
                    DynamicPatternFixture.highSlotObserved=true;ClientBootstrap.capture("ae2lo-ripper-pattern-access-high-slot.png");
                }
            }
        } catch(Exception error){throw new IllegalStateException(error);}
    }
    static boolean tick(Minecraft mc) {
        if(!DynamicPatternFixture.enabled)return true;
        if(failure!=null)throw new IllegalStateException(failure);
        if(step==7)return true;
        if(!(mc.screen instanceof CraftingRipperScreen screen))return false;
        if(++ticks%25!=0)return false;
        if(ticks>700)throw new IllegalStateException("Dynamic UI timeout stage "+step);
        var menu=(CraftingRipperMenu)mc.player.containerMenu;
        var patterns=menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        switch(step) {
            case 0 -> {
                DynamicPatternFixture.require(patterns.size()==36 && menu.patternRows==43,"Native menu must expose only 36 window slots for 378 patterns");
                for(int i=0;i<55;i++)screen.mouseScrolled(screen.getGuiLeft()+30,screen.getGuiTop()+60,0,-1);
                step=1;
            }
            case 1 -> {
                if(menu.isScrollPending() || menu.firstPatternRow!=39)return false;
                DynamicPatternFixture.require(patterns.get(26).getItem().getHoverName().getString().equals("tail pattern"),"Bottom row maps to wrong global slot 377");
                checks.put("bottomRow",39);checks.put("slot377Sentinel",true);
                try {
                    var field=CraftingRipperScreen.class.getDeclaredField("patternScroll");field.setAccessible(true);
                    var scrollbar=(appeng.client.gui.widgets.Scrollbar)field.get(screen);
                    var style=appeng.client.gui.widgets.Scrollbar.class.getDeclaredField("style");style.setAccessible(true);
                    DynamicPatternFixture.require(style.get(scrollbar)==appeng.client.gui.widgets.Scrollbar.BIG,"Ripper must use AE2 pattern-terminal scrollbar skin");
                    var bounds=scrollbar.getBounds();
                    checks.put("nativeScrollbarStyle","BIG");checks.put("scrollbarBounds",java.util.List.of(bounds.getX(),bounds.getY(),bounds.getWidth(),bounds.getHeight()));
                    checks.put("guiScale",mc.getWindow().getGuiScale());
                } catch(Exception e){throw new IllegalStateException(e);}
                ClientBootstrap.capture("ae2lo-ripper-bottom.png");
                server(mc,p->{p.containerMenu.setCarried(DynamicPatternFixture.encoded.copy());p.containerMenu.broadcastFullState();});step=2;
            }
            case 2 -> {
                if(menu.getCarried().isEmpty())return false;
                mc.gameMode.handleInventoryMouseClick(menu.containerId,patterns.get(35).index,0,ClickType.PICKUP,mc.player);step=3;
            }
            case 3 -> {
                if(menu.patternRows!=44 || !menu.getCarried().isEmpty())return false;
                screen.mouseScrolled(screen.getGuiLeft()+30,screen.getGuiTop()+60,0,-1);step=4;
            }
            case 4 -> {
                if(menu.isScrollPending() || menu.firstPatternRow!=40)return false;
                mc.gameMode.handleInventoryMouseClick(menu.containerId,patterns.get(26).index,0,ClickType.QUICK_MOVE,mc.player);step=5;
            }
            case 5 -> {
                if(menu.isScrollPending() || menu.firstPatternRow!=39 || menu.patternRows!=43)return false;
                checks.put("cardInsertGrowsTo44Rows",true);checks.put("takeTailShrinksAndClamps",true);
                server(mc,p->{
                    var inv=DynamicPatternFixture.host.getLogic().getPatternInv();
                    DynamicPatternFixture.require(inv.nonEmptyStacks().size()==378,"Tail pickup resource count mismatch");
                    DynamicPatternFixture.require(p.getInventory().countItem(DynamicPatternFixture.encoded.getItem())==1,"Native shift pickup lost/duplicated pattern");
                    p.getInventory().setItem(8,DynamicPatternFixture.encoded.copyWithCount(64));p.containerMenu.broadcastFullState();
                });step=6;
            }
            case 6 -> {
                var hotbar=menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
                if(hotbar.get(8).getItem().getCount()==64){mc.gameMode.handleInventoryMouseClick(menu.containerId,hotbar.get(8).index,0,ClickType.QUICK_MOVE,mc.player);return false;}
                if(menu.patternRows!=51)return false;
                server(mc,p->{
                    DynamicPatternFixture.require(DynamicPatternFixture.host.getLogic().getPatternInv().nonEmptyStacks().size()==442,"Bulk quick move failed to cross dynamic row boundary");
                    DynamicPatternFixture.require(p.getInventory().getItem(8).isEmpty(),"Bulk quick move did not clear source");
                });
                checks.put("bulkQuickMove64",true);checks.put("patternsAfterBulk",442);checks.put("passed",true);
                try {Files.writeString(DynamicPatternFixture.output.resolve("dynamic-pattern-ui.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks));}
                catch(Exception e){throw new IllegalStateException(e);}
                ClientBootstrap.capture("ae2lo-ripper-after-bulk.png");step=7;
            }
        }
        return false;
    }
    private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var id=mc.player.getUUID();var server=mc.getSingleplayerServer();server.execute(()->{
            try {action.accept(server.getPlayerList().getPlayer(id));}catch(Throwable e){failure=e.toString();}
        });
    }
}
