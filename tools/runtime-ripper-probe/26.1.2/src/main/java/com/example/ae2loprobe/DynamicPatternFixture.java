package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.me.helpers.MachineSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.block.RipperPatternInventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Future;

/** Native acceptance fixture. No planner/executor replacement or mocked inventories. */
public final class DynamicPatternFixture {
    static volatile boolean enabled;
    static volatile boolean highSlotObserved;
    static CraftingRipperBlockEntity host;
    static Path output;
    static ItemStack encoded;
    private static ServerLevel level;
    private static ServerPlayer player;
    private static appeng.parts.reporting.PatternAccessTerminalPart terminal;
    private static appeng.menu.implementations.PatternAccessTermMenu access;
    private static long accessId;
    private static long settled;
    private static IGrid grid;
    private static CraftingCPUCluster cpu;
    private static Future<ICraftingPlan> future;
    private static int phase;
    private static long began;
    private static final Map<String,Object> checks = new LinkedHashMap<>();
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    public static void start(ServerLevel l, ServerPlayer p, IGrid g, CraftingCPUCluster c,
            CraftingRipperBlockEntity h, Path out) {
        enabled = true; level=l; player=p; grid=g; cpu=c; host=h; output=out;
        var ingredients = new ItemStack[9]; Arrays.fill(ingredients, ItemStack.EMPTY);
        ingredients[0] = Items.OAK_LOG.getDefaultInstance();
        var input=CraftingInput.of(3,3,Arrays.asList(ingredients));
        var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
        encoded=PatternDetailsHelper.encodeCraftingPattern(recipe,ingredients,recipe.value().assemble(input),false,false);
        fillLibrary();
        var inv=host.getLogic().getPatternInv();
        require(inv.size()==387 && inv.nonEmptyStacks().size()==378,"378 patterns must leave one whole empty row");
        var restored=new RipperPatternInventory(s->true,()->{});
        var errors=new net.minecraft.util.ProblemReporter.Collector(); var tag=net.minecraft.world.level.storage.TagValueOutput.createWithContext(errors,level.registryAccess()); inv.write(tag); restored.read(net.minecraft.world.level.storage.TagValueInput.create(errors,level.registryAccess(),tag.buildResult())); require(errors.isEmpty(),"Pattern serialization errors: "+errors.getReport());
        require(restored.size()==387 && restored.nonEmptyStacks().size()==378,"Sparse NBT capacity/count mismatch");
        for(int i=0;i<378;i++) require(ItemStack.matches(inv.getStackInSlot(i),restored.getStackInSlot(i)),"Roundtrip lost stack components at "+i);
        var drops=new ArrayList<ItemStack>(); host.getLogic().addDrops(drops);
        require(drops.stream().filter(s->s.is(encoded.getItem())).mapToInt(ItemStack::getCount).sum()==378,"Drops lose/duplicate patterns");
        var settings=net.minecraft.core.component.DataComponentMap.builder(); host.getLogic().exportSettings(settings);
        require(settings.build().get(appeng.api.ids.AEComponents.EXPORTED_PATTERNS)==null,"Native 256-entry memory component must not truncate a larger library");
        inv.clear(); require(inv.size()==9,"Empty inventory must retain one insert row");
        require(inv.simulateAdd(encoded.copyWithCount(64)).isEmpty() && inv.isEmpty(),"Bulk simulation must include dynamic append capacity without mutation");
        inv.setItemDirect(35,encoded.copyWithCount(3));
        var oldTag=net.minecraft.world.level.storage.TagValueOutput.createWithContext(errors,level.registryAccess()); inv.write(oldTag); restored.read(net.minecraft.world.level.storage.TagValueInput.create(errors,level.registryAccess(),oldTag.buildResult()));
        require(restored.size()==45 && restored.getStackInSlot(35).getCount()==3,"Legacy fixed-slot index/count migration failed");
        inv.clear(); inv.setItemDirect(0,encoded.copy());
        require(host.isLoopCardInstalled() && inv.isItemValid(1,encoded),"Card must permit manual insertion");
        insert(Items.OAK_WOOD,4); insert(Items.BIRCH_LOG,4);
        checks.put("storedPatterns",378); checks.put("capacityWithEmptyRow",387);
        checks.put("componentRoundTrip",true); checks.put("legacySlot35Count3",true);
        checks.put("dropCount",378); checks.put("cardPermitsEditing",true);
        began=level.getGameTime(); phase=0;
    }
    private static long trackerId(appeng.menu.implementations.PatternAccessTermMenu menu,int expectedSize) throws Exception {
        var field=appeng.menu.implementations.PatternAccessTermMenu.class.getDeclaredField("diList");field.setAccessible(true);
        var trackers=(Map<?,?>)field.get(menu);var tracker=trackers.get(host);require(tracker!=null,"Pattern access did not discover ripper");
        var mirror=tracker.getClass().getDeclaredField("client");mirror.setAccessible(true);
        require(((appeng.api.inventories.InternalInventory)mirror.get(tracker)).size()==expectedSize,"Pattern access stale mirror size");
        var id=tracker.getClass().getDeclaredField("serverId");id.setAccessible(true);return id.getLong(tracker);
    }
    static void fillLibrary() {
        var inv=host.getLogic().getPatternInv();
        inv.batch(()->{inv.clear();for(int i=0;i<378;i++) inv.setItemDirect(i,encoded.copy());
            inv.getStackInSlot(377).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("tail pattern"));});
    }
    private static void insert(net.minecraft.world.item.Item item,long n) {
        require(grid.getStorageService().getInventory().insert(AEItemKey.of(item),n,Actionable.MODULATE,new MachineSource(grid::getPivot))==n,"Fixture insert failed");
    }
    private static long count(net.minecraft.world.item.Item item) {return grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(item));}
    private static void calculate(net.minecraft.world.item.Item item) {
        future=grid.getCraftingService().beginCraftingCalculation(level,()->new MachineSource(grid::getPivot),AEItemKey.of(item),4,CalculationStrategy.REPORT_MISSING_ITEMS);
    }
    private static void submit(ICraftingPlan plan) {
        require(!plan.simulation() && plan.missingItems().isEmpty(),"Unexpected missing materials");
        require(grid.getCraftingService().submitJob(plan,null,cpu,false,new MachineSource(grid::getPivot)).successful(),"Native CPU submission rejected");
    }
    public static boolean tick() throws Exception {
        if(level.getGameTime()-began>800) throw new IllegalStateException("Dynamic policy fixture timeout phase "+phase);
        switch(phase) {
            case 0 -> {if(level.getGameTime()-began<35)return false; calculate(Items.OAK_PLANKS);phase=1;}
            case 1 -> {if(!future.isDone())return false;var plan=future.get();
                require(plan.simulation() && plan.missingItems().get(AEItemKey.of(Items.OAK_LOG))>0,"Manual oak recipe must remain missing instead of using available oak wood");
                require(count(Items.OAK_WOOD)==4,"Missing plan consumed alternative input");
                checks.put("missingManualNeverFallsBack",true);calculate(Items.BIRCH_PLANKS);phase=2;}
            case 2 -> {if(!future.isDone())return false;submit(future.get());phase=3;}
            case 3 -> {if(count(Items.BIRCH_PLANKS)<4)return false;
                require(count(Items.BIRCH_LOG)==3,"Uncovered automatic recipe balance differs");
                checks.put("uncoveredAutoCpu",true);insert(Items.OAK_LOG,2);settled=level.getGameTime();phase=31;}
            case 31 -> {if(level.getGameTime()-settled<25)return false;calculate(Items.OAK_PLANKS);phase=4;}
            case 4 -> {if(!future.isDone())return false;var plan=future.get();
                require(plan.usedItems().get(AEItemKey.of(Items.OAK_LOG))==1 && plan.usedItems().get(AEItemKey.of(Items.OAK_WOOD))==0,"Native planner manual input differs: used="+plan.usedItems()+" missing="+plan.missingItems()+" simulation="+plan.simulation());submit(plan);phase=5;}
            case 5 -> {if(count(Items.OAK_PLANKS)<4)return false;
                require(count(Items.OAK_LOG)==1 && count(Items.OAK_WOOD)==4,"Manual CPU resource balance differs");
                checks.put("manualOnlyCpu",true);
                var source=new MachineSource(grid::getPivot);
                require(grid.getStorageService().getInventory().extract(AEItemKey.of(Items.OAK_PLANKS),4,Actionable.MODULATE,source)==4,"Output isolation failed");
                require(grid.getStorageService().getInventory().extract(AEItemKey.of(Items.OAK_LOG),1,Actionable.MODULATE,source)==1,"Input isolation failed");
                host.getLogic().getPatternInv().clear(); began=level.getGameTime();phase=6;}
            case 6 -> {if(level.getGameTime()-began<35)return false;calculate(Items.OAK_PLANKS);phase=7;}
            case 7 -> {if(!future.isDone())return false;var plan=future.get();
                require(plan.usedItems().get(AEItemKey.of(Items.OAK_WOOD))==1,"Removing manual pattern did not restore automatic alternative");submit(plan);phase=8;}
            case 8 -> {if(count(Items.OAK_PLANKS)<4)return false;
                require(count(Items.OAK_WOOD)==3,"Restored automatic CPU resource balance differs");
                checks.put("removedManualRestoresAutoCpu",true);fillLibrary();
                host.getLogic().getPatternInv().setItemDirect(40004,encoded.copy());
                var pos=host.getBlockPos().above();
                appeng.api.parts.PartHelper.setPart(level,pos,null,player,appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
                terminal=(appeng.parts.reporting.PatternAccessTerminalPart)appeng.api.parts.PartHelper.setPart(level,pos,net.minecraft.core.Direction.SOUTH,player,appeng.core.definitions.AEParts.PATTERN_ACCESS_TERMINAL.get());
                player.connection.teleport(-4.5,102,3.5,-145f,20f);settled=level.getGameTime();phase=9;}
            case 9 -> {if(level.getGameTime()-settled<30)return false;
                appeng.menu.MenuOpener.open(appeng.menu.implementations.PatternAccessTermMenu.TYPE,player,appeng.menu.locator.MenuLocators.forPart(terminal));
                access=(appeng.menu.implementations.PatternAccessTermMenu)player.containerMenu;
                settled=level.getGameTime();phase=10;}
            case 10 -> {if(level.getGameTime()-settled<25 || !highSlotObserved)return false;
                access.broadcastChanges();trackerId(access,40014);
                host.getLogic().getPatternInv().setItemDirect(40004,ItemStack.EMPTY);
                checks.put("patternAccessClientSlot40004",true);settled=level.getGameTime();phase=101;}
            case 101 -> {if(level.getGameTime()-settled<25)return false;
                access.broadcastChanges();accessId=trackerId(access,387);
                access.setCarried(encoded.copy());
                access.doAction(player,appeng.helpers.InventoryAction.PICKUP_OR_SET_DOWN,386,accessId);
                require(access.getCarried().isEmpty() && host.getLogic().getPatternInv().size()==396,"Pattern access tail insertion failed");
                settled=level.getGameTime();phase=11;}
            case 11 -> {if(level.getGameTime()-settled<25)return false;
                access.broadcastChanges();long grownId=trackerId(access,396);require(grownId!=accessId,"Pattern access did not discard stale tracker ID");
                access.doAction(player,appeng.helpers.InventoryAction.PICKUP_OR_SET_DOWN,386,accessId);
                require(host.getLogic().getPatternInv().size()==396 && access.getCarried().isEmpty(),"Stale pattern access ID mutated inventory");
                access.doAction(player,appeng.helpers.InventoryAction.PICKUP_OR_SET_DOWN,386,grownId);
                require(access.getCarried().getCount()==1 && host.getLogic().getPatternInv().size()==387,"Pattern access extraction failed");
                access.setCarried(ItemStack.EMPTY);access.broadcastChanges();trackerId(access,387);player.closeContainer();
                checks.put("patternAccessGrowShrink",true);checks.put("patternAccessStaleIdRejected",true);
                checks.put("passed",true);Files.writeString(output.resolve("dynamic-patterns-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks));return true;}
        }
        return false;
    }
}
