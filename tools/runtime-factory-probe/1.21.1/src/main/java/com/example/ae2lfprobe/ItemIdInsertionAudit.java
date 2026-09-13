package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.client.FactoryEditorScreen;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.mixin.MultiLineEditBoxAccess;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

/** Test-only synthetic native GUI events, real inventory/menu synchronization, and framebuffer evidence. */
public final class ItemIdInsertionAudit {
    public static volatile boolean finished, passed;
    private static Minecraft mc;
    private static final BlockPos POS = new BlockPos(14,101,4);
    private static final String SEED = "# alpha OMEGA\ndone";
    private record Case(String id,String stack,int button,String code,int start,int end,String token,
                        boolean viewer,boolean outside,boolean nativeCaret,String hand) {}
    private static final List<Case> cases = new ArrayList<>();
    private static final List<Map<String,Object>> rows = new ArrayList<>();
    private static final List<String> screenshots = new ArrayList<>();
    private static Path root;
    private static String viewer, expected, failure="";
    private static int index, phase, ticks, caseTicks, pause, expectedCursor, pressCount, releaseCount, dragCount;
    private static CompletableFuture<?> pending;
    private static Map<String,Object> before,serverBefore,row;
    private static double sourceX,sourceY,destX,destY;
    private static boolean seedCaptured;
    private static int originalGuiScale=-1;
    private static double inputGuiScale;

    public static int timeoutTicks(){return 16000;}
    public static void begin(Minecraft client) {
        if(mc!=null)throw new IllegalStateException("ID insertion audit cannot run twice in one client");
        mc=client;root=Path.of(System.getProperty("ae2lf.probe.reportDir"));
        viewer=System.getProperty("ae2lf.probe.viewerMode","none");
        try {
            Files.createDirectories(root.resolve("id-insertion-screenshots"));
            require(Set.of("none","jei","emi","both").contains(viewer),"unknown viewer mode");
            var mods=net.neoforged.fml.ModList.get();
            require(mods.isLoaded("jei")==Set.of("jei","both").contains(viewer),"JEI actual loaded state differs from requested mode");
            require(mods.isLoaded("emi")==Set.of("emi","both").contains(viewer),"EMI actual loaded state differs from requested mode");
            originalGuiScale=mc.options.guiScale().get();
            if(!viewer.equals("none")){mc.options.guiScale().set(1);mc.resizeDisplay();}
            inputGuiScale=mc.getWindow().getGuiScale();
            plan();
            pending=server(p->{
                p.closeContainer();p.connection.teleport(14.5,102,6.5,180,20);
                var level=p.serverLevel();
                for(int x=12;x<=16;x++)for(int z=2;z<=8;z++)level.setBlockAndUpdate(new BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(POS,FactoryContent.TERMINAL.get().defaultBlockState());
                var host=(FactoryBlockEntity)level.getBlockEntity(POS);
                var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(SEED,host.factoryId(),ItemStack.EMPTY));
                host.factoryPatterns().setItemDirect(0,pattern);host.setFactoryDraft(SEED);
                return true;
            });
            write();
        } catch(Throwable e){fail(e);}
    }
    private static void plan() {
        add("ordinary-left","stone",0,SEED,8,8,"minecraft:stone");
        add("ordinary-right","stone",1,SEED,8,8,"minecraft:stone");
        add("selection-replaced","stone",1,SEED,2,7,"minecraft:stone");
        add("unicode-caret","stone",0,"# 中😀 before AFTER\ndone",13,13,"minecraft:stone");
        add("water-container-left","water",0,SEED,8,8,"minecraft:water_bucket");
        add("water-container-right","water",1,SEED,8,8,"minecraft:water");
        add("lava-container-right","lava",1,SEED,8,8,"minecraft:lava");
        add("empty-container-left","emptyfluid",0,SEED,8,8,"minecraft:bucket");
        add("empty-container-right","emptyfluid",1,SEED,8,8,"");
        String energyId=BuiltInRegistries.ITEM.getKey(ModItems.PORTABLE_LOOP_STORAGE_CELL_1K.get()).toString();
        add("empty-fe-left","emptyfe",0,SEED,8,8,energyId);
        add("empty-fe-right","emptyfe",1,SEED,8,8,"");
        add("full-fe-left","fullfe",0,SEED,8,8,energyId);
        add("full-fe-right","fullfe",1,SEED,8,8,"neoforge::fe");
        String exact="#"+"x".repeat(65536-"minecraft:stone".length()-1);
        add("exact-length","stone",1,exact,2,2,"minecraft:stone");
        add("length-reject-atomic","stone",1,exact+"x",2,2,"minecraft:stone");
        add("full-length-selection","stone",1,"#"+"x".repeat(65535),2,40,"minecraft:stone");
        cases.add(new Case("empty-carried-main-encoder","none",0,SEED,13,13,"",false,false,true,"encoder"));
        cases.add(new Case("empty-carried-main-stone","none",0,SEED,13,13,"",false,false,true,"stone"));
        cases.add(new Case("empty-carried-offhand-water","none",0,SEED,13,13,"",false,false,true,"offhandwater"));
        add("empty-shulker-right","emptyshulker",1,SEED,8,8,"");
        add("filled-shulker-left","filledshulker",0,SEED,8,8,"minecraft:shulker_box");
        add("filled-shulker-right","filledshulker",1,SEED,8,8,"minecraft:stone & minecraft:gold_ingot");
        add("ae-cell-contents-right","filledcell",1,SEED,8,8,"minecraft:iron_ingot");
        if(!viewer.equals("none")) {
            cases.add(new Case("viewer-item","none",0,SEED,8,8,"minecraft:stone",true,false,false,"none"));
            cases.add(new Case("viewer-selection","none",0,SEED,2,7,"minecraft:stone",true,false,false,"none"));
            cases.add(new Case("viewer-outside-rejected","none",0,SEED,8,8,"minecraft:stone",true,true,false,"none"));
            cases.add(new Case("viewer-fluid","none",0,SEED,8,8,"minecraft:water",true,false,false,"none"));
        }
        require(cases.stream().map(Case::id).distinct().count()==cases.size(),"duplicate planned IDs");
    }
    private static void add(String id,String stack,int button,String code,int start,int end,String token) {
        cases.add(new Case(id,stack,button,code,start,end,token,false,false,false,"none"));
    }
    public static void tick(Minecraft client) {
        if(mc==null||finished)return;
        try {
            require(client==mc&&mc.player!=null&&mc.getSingleplayerServer()!=null,"lost native client/server");
            require(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==0,"window became visible");
            if(++ticks>timeoutTicks()||++caseTicks>500)throw new IllegalStateException("native action timeout phase="+phase+" case="+(index<cases.size()?cases.get(index).id:"complete"));
            if(pause>0){pause--;return;}
            if(phase==0) {
                if(!pending.isDone())return;pending.join();
                // AE2 resolves its advanced menu locator immediately on packet receipt.
                // Open only after the actual client has received the block entity.
                if(mc.level==null||!(mc.level.getBlockEntity(POS) instanceof FactoryBlockEntity))return;
                pending=server(p->{
                    var host=(FactoryBlockEntity)p.serverLevel().getBlockEntity(POS);
                    host.openMenu(p,appeng.menu.locator.MenuLocators.forBlockEntity(host));return true;
                });phase=-1;write();return;
            }
            if(phase==-1) {
                if(!pending.isDone())return;pending.join();
                if(!(mc.screen instanceof FactoryEditorScreen))return;
                caseTicks=0;phase=1;
            }
            if(index>=cases.size()) {
                require(rows.size()==cases.size()&&rows.stream().allMatch(r->Boolean.TRUE.equals(r.get("passed"))),"incomplete action results");
                for(String file:screenshots)require(Files.isRegularFile(root.resolve(file)),"missing actual screenshot "+file);
                passed=true;finished=true;restoreGuiScale();write();return;
            }
            var c=cases.get(index);require(mc.screen instanceof FactoryEditorScreen,"unexpected screen");
            if(phase==1) {
                row=new LinkedHashMap<>();row.put("id",c.id);row.put("syntheticNativeInput",true);
                row.put("initialCodeSha256",sha(c.code));row.put("initialLengthUtf16",c.code.length());
                row.put("initialSelection",List.of(c.start,c.end));row.put("requestedToken",c.token);
                row.put("viewer",c.viewer?viewer:"none");pressCount=releaseCount=dragCount=0;
                pending=server(p->{
                    p.containerMenu.setCarried(ItemStack.EMPTY);p.getInventory().setItem(9,stack(c.stack));
                    p.setItemInHand(InteractionHand.MAIN_HAND,c.hand.equals("encoder")?FactoryContent.ENCODER.get().getDefaultInstance():c.hand.equals("stone")?new ItemStack(Items.STONE):ItemStack.EMPTY);
                    p.setItemInHand(InteractionHand.OFF_HAND,c.hand.equals("offhandwater")?new ItemStack(Items.WATER_BUCKET):ItemStack.EMPTY);
                    p.inventoryMenu.broadcastChanges();
                    p.containerMenu.broadcastChanges();return true;
                });phase=2;pause=8;return;
            }
            if(phase==2) {
                if(!pending.isDone())return;pending.join();
                if(!mc.player.containerMenu.getCarried().isEmpty())return;
                if(!same(mc.player.getInventory().getItem(9),stack(c.stack)))return;
                var expectedMain=c.hand.equals("encoder")?FactoryContent.ENCODER.get().getDefaultInstance():c.hand.equals("stone")?new ItemStack(Items.STONE):ItemStack.EMPTY;
                var expectedOff=c.hand.equals("offhandwater")?new ItemStack(Items.WATER_BUCKET):ItemStack.EMPTY;
                if(!same(mc.player.getMainHandItem(),expectedMain)||!same(mc.player.getOffhandItem(),expectedOff))return;
                var edit=editor();edit.setValue(c.code);edit.setFocused(true);screen().setFocused(edit);
                var f=field();f.seekCursor(Whence.ABSOLUTE,c.start);f.setSelecting(true);f.seekCursor(Whence.ABSOLUTE,c.end);f.setSelecting(false);
                // Only a real slot click takes the server-provided inventory stack onto the GUI cursor.
                if(!c.stack.equals("none")) {
                    var slot=mc.player.containerMenu.slots.stream().filter(s->s.container==mc.player.getInventory()&&s.getContainerSlot()==9).findFirst().orElseThrow();
                    NativeIdInput.click(mc,screen().getGuiLeft()+slot.x+8,screen().getGuiTop()+slot.y+8,0);
                }
                phase=3;pause=8;return;
            }
            if(phase==3) {
                if(!same(mc.player.containerMenu.getCarried(),stack(c.stack)))return;
                require(field().value().equals(c.code),"server seed overwrote local editor");
                require(selectionIndex("beginIndex")==c.start&&selectionIndex("endIndex")==c.end,"pickup disturbed caret/selection");
                var capability=NativeIdInput.contents(mc.player.containerMenu.getCarried());
                if(c.stack.equals("emptyfe")||c.stack.equals("fullfe")) {
                    require(Boolean.TRUE.equals(capability.get("energyCapability")),"FE fixture must expose actual native item capability");
                    long stored=((Number)capability.get("energyStored")).longValue();
                    long capacity=((Number)capability.get("energyCapacity")).longValue();
                    require(capacity>0&&(c.stack.equals("emptyfe")?stored==0:stored==capacity),"FE fixture is not actually empty/full");
                }
                if(Set.of("water","lava","emptyfluid").contains(c.stack))require(Boolean.TRUE.equals(capability.get("fluidCapability")),"fluid fixture has no native item capability");
                if(c.stack.equals("emptyshulker")||c.stack.equals("filledshulker")) {
                    require(Boolean.TRUE.equals(capability.get("itemCapability")),"shulker must expose the actual native item capability");
                    @SuppressWarnings("unchecked") var slots=(List<Map<String,Object>>)capability.get("itemSlots");
                    require(slots.size()==27,"shulker must expose all 27 native slots");
                    var positive=slots.stream().filter(s->((Number)s.get("amount")).longValue()>0).toList();
                    var expectedSlots=c.stack.equals("emptyshulker")?List.of():List.of(
                            Map.of("index",0,"id","minecraft:stone","amount",9L),
                            Map.of("index",1,"id","minecraft:stone","amount",13L),
                            Map.of("index",2,"id","minecraft:gold_ingot","amount",5L));
                    require(positive.equals(expectedSlots),"shulker physical contents differ from the independently specified duplicate fixture");
                    row.put("nativeContainerFixtureValidated",true);
                }
                if(c.stack.equals("filledcell")) {
                    require(Boolean.TRUE.equals(capability.get("aeCellInventory")),"AE contents case must expose a real StorageCells inventory");
                    require(capability.get("aeCellContents").equals(List.of(Map.of(
                            "keyType",appeng.api.stacks.AEKeyType.items().getId().toString(),
                            "id","minecraft:iron_ingot","amount",37L))),"AE cell physical contents must be exactly 37 iron ingots");
                    row.put("nativeContainerFixtureValidated",true);
                }
                before=clientSnapshot();row.put("clientBefore",before);
                pending=server(ItemIdInsertionAudit::serverSnapshot);phase=4;return;
            }
            if(phase==4) {
                if(!pending.isDone())return;
                @SuppressWarnings("unchecked") var snap=(Map<String,Object>)pending.join();serverBefore=snap;row.put("serverBefore",snap);
                var area=screen().getCodeDropArea();destX=area.getX()+area.getWidth()-18;destY=area.getY()+16;
                boolean fits=(long)c.code.length()-(c.end-c.start)+c.token.length()<=65536;
                boolean changes=!c.token.isEmpty()&&fits&&!c.outside&&!c.nativeCaret;
                expected=changes?c.code.substring(0,c.start)+c.token+c.code.substring(c.end):c.code;
                expectedCursor=changes?c.start+c.token.length():c.end;
                row.put("expectedLengthUtf16",expected.length());row.put("expectedCodeSha256",sha(expected));
                row.put("expectedCaret",expectedCursor);row.put("atomicInsertionAccepted",changes);
                if(c.viewer) {ViewerIdInput.search(viewer,c.token.equals("minecraft:water")?"water":"stone");phase=5;pause=30;return;}
                if(c.nativeCaret){destX=area.getX()+5+mc.font.width("# al");destY=area.getY()+5;}
                capture(c.id+"-before");
                NativeIdInput.press(mc,destX,destY,c.button);pressCount++;phase=6;pause=3;return;
            }
            if(phase==5) {
                double[] source=ViewerIdInput.find(mc,viewer,screen().getCodeDropArea(),c.token);
                if(source==null)return;
                sourceX=source[0];sourceY=source[1];row.put("actualViewerSource",Map.of("x",sourceX,"y",sourceY,"registryId",c.token));
                row.put("dropArea",List.of(screen().getCodeDropArea().getX(),screen().getCodeDropArea().getY(),screen().getCodeDropArea().getWidth(),screen().getCodeDropArea().getHeight()));
                if(c.outside){destX=screen().getGuiLeft()+4;destY=screen().getGuiTop()+4;}
                capture(c.id+"-before");NativeIdInput.press(mc,sourceX,sourceY,c.button);pressCount++;
                phase=6;pause=3;return;
            }
            if(phase==6) {
                if(c.viewer) {
                    NativeIdInput.drag(mc,sourceX+(destX-sourceX)*.5,sourceY+(destY-sourceY)*.5,c.button,(destX-sourceX)*.5,(destY-sourceY)*.5);dragCount++;
                    phase=60;pause=3;return;
                }
                // Matching drag/release must not cause a second insertion or native inventory action.
                NativeIdInput.drag(mc,destX+2,destY,c.button,2,0);dragCount++;
                NativeIdInput.release(mc,destX+2,destY,c.button);releaseCount++;
                phase=7;pause=8;return;
            }
            if(phase==60) {
                NativeIdInput.drag(mc,destX,destY,c.button,(destX-sourceX)*.5,(destY-sourceY)*.5);dragCount++;
                row.put("viewerDragObserved",ViewerIdInput.dragState(viewer));capture(c.id+"-drag");
                phase=61;pause=3;return;
            }
            if(phase==61) {
                NativeIdInput.release(mc,destX,destY,c.button);releaseCount++;
                phase=7;pause=8;return;
            }
            if(phase==7) {
                require(field().characterLimit()==65536,"native source character limit was not restored");
                row.put("characterLimitRestored",true);
                require(field().value().equals(expected),"native input produced incorrect code (whole-value SHA expected="+sha(expected)+" actual="+sha(field().value())+")");
                if(c.nativeCaret)require(field().cursor()!=c.end&&field().cursor()>0&&field().cursor()<c.code.length(),"empty carried did not preserve native caret movement");
                else {
                    require(field().cursor()==expectedCursor,"caret is not exact expected offset");
                    if(Boolean.TRUE.equals(row.get("atomicInsertionAccepted")))require(field().getSelectedText().isEmpty(),"selection not collapsed after insertion");
                    if(expected.equals(c.code))require(selectionIndex("beginIndex")==c.start&&selectionIndex("endIndex")==c.end,"rejected input disturbed selection");
                }
                var after=clientSnapshot();require(before.equals(after),"client carried/inventory/resource changed during code input");row.put("clientAfter",after);
                pending=server(ItemIdInsertionAudit::serverSnapshot);phase=8;return;
            }
            if(phase==8) {
                if(!pending.isDone())return;
                @SuppressWarnings("unchecked") var snap=(Map<String,Object>)pending.join();
                require(serverBefore.equals(snap),"server carried/inventory/resource changed during code input");row.put("serverAfter",snap);
                row.put("actualCaret",field().cursor());row.put("actualSelection",List.of(selectionIndex("beginIndex"),selectionIndex("endIndex")));
                row.put("actualCodeSha256",sha(field().value()));row.put("actualLengthUtf16",field().value().length());
                row.put("events",Map.of("press",pressCount,"drag",dragCount,"release",releaseCount));
                require(pressCount==1&&releaseCount==1&&dragCount>=1,"unpaired input event");
                capture(c.id+"-after");
                var save=(AbstractWidget)read(screen(),"saveButton");require(save.active,"real save button disabled");
                NativeIdInput.click(mc,save.getX()+save.getWidth()/2d,save.getY()+save.getHeight()/2d,0);
                pending=null;phase=9;pause=8;return;
            }
            if(phase==9) {
                if(pending==null) {pending=server(p->{
                    var menu=(FactoryEditorMenu)p.containerMenu;
                    return menu.status.equals("Saved")&&menu.code().equals(expected)
                        &&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals(expected);
                });return;}
                if(!pending.isDone())return;
                if(!Boolean.TRUE.equals(pending.join())){pending=null;pause=5;return;}
                if(!((FactoryEditorMenu)mc.player.containerMenu).code().equals(expected))return;
                require(editor().getValue().equals(expected),"save ACK overwrote local result");
                row.put("saveRoundTrip",true);row.put("passed",true);rows.add(row);index++;caseTicks=0;phase=1;pause=8;write();
            }
        } catch(Throwable e){fail(e);}
    }
    private static ItemStack stack(String kind) {
        return switch(kind) {
            case "none" -> ItemStack.EMPTY;
            case "stone" -> new ItemStack(Items.STONE,17);
            case "water" -> new ItemStack(Items.WATER_BUCKET);
            case "lava" -> new ItemStack(Items.LAVA_BUCKET);
            case "emptyfluid" -> new ItemStack(Items.BUCKET);
            case "emptyshulker","filledshulker" -> {
                var box = new ItemStack(Items.SHULKER_BOX);
                var contents = kind.equals("filledshulker")
                        ? List.of(new ItemStack(Items.STONE,9),new ItemStack(Items.STONE,13),new ItemStack(Items.GOLD_INGOT,5))
                        : List.<ItemStack>of();
                box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));
                yield box;
            }
            case "filledcell" -> {
                // Explicit finite fixture supply through the actual AE cell API; this is not a player action claim.
                var cellStack = ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance();
                var cell = appeng.api.storage.StorageCells.getCellInventory(cellStack,null);
                require(cell != null,"AE fixture must expose real StorageCells inventory");
                var key = appeng.api.stacks.AEItemKey.of(Items.IRON_INGOT);
                require(cell.insert(key,37,appeng.api.config.Actionable.MODULATE,
                        appeng.api.networking.security.IActionSource.empty()) == 37,"AE fixture must accept exactly 37 iron ingots");
                cell.persist();
                var restored = appeng.api.storage.StorageCells.getCellInventory(cellStack.copy(),null);
                require(restored != null && restored.getAvailableStacks().get(key) == 37,"AE fixture supply must persist in the carried stack");
                yield cellStack;
            }
            case "emptyfe","fullfe" -> {
                var item=ModItems.PORTABLE_LOOP_STORAGE_CELL_1K.get();var stack=item.getDefaultInstance();
                if(kind.equals("fullfe"))item.injectAEPower(stack,item.getAEMaxPower(stack),appeng.api.config.Actionable.MODULATE);
                yield stack;
            }
            default -> throw new IllegalArgumentException(kind);
        };
    }
    private static Map<String,Object> clientSnapshot() {return snapshot(mc.player);}
    private static Map<String,Object> serverSnapshot(ServerPlayer p) {return snapshot(p);}
    private static Map<String,Object> snapshot(net.minecraft.world.entity.player.Player p) {
        var inv=new ArrayList<String>();for(int i=0;i<p.getInventory().getContainerSize();i++)inv.add(stackHash(p.getInventory().getItem(i),p));
        var slots=p.containerMenu.slots.stream().map(s->stackHash(s.getItem(),p)).toList();
        return Map.of("inventory",inv,"menuSlots",slots,"carried",stackHash(p.containerMenu.getCarried(),p),
            "main",stackHash(p.getMainHandItem(),p),"off",stackHash(p.getOffhandItem(),p),
            "capability",NativeIdInput.contents(p.containerMenu.getCarried()));
    }
    private static String stackHash(ItemStack stack,net.minecraft.world.entity.player.Player p) {
        if(stack.isEmpty())return "empty";
        return sha(ItemStack.CODEC.encodeStart(p.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow().toString());
    }
    private static boolean same(ItemStack a,ItemStack b){return a.isEmpty()?b.isEmpty():a.getCount()==b.getCount()&&ItemStack.isSameItemSameComponents(a,b);}
    private static FactoryEditorScreen screen(){return (FactoryEditorScreen)mc.screen;}
    private static MultiLineEditBox editor(){return (MultiLineEditBox)read(screen(),"editor");}
    private static MultilineTextField field(){return ((MultiLineEditBoxAccess)editor()).ae2lf$textField();}
    // StringView is protected in pinned Minecraft; observe its exact immutable range without changing it.
    private static int selectionIndex(String accessor) {
        try {
            Object selection=field().getSelected();
            var method=selection.getClass().getDeclaredMethod(accessor);method.setAccessible(true);
            return ((Number)method.invoke(selection)).intValue();
        }catch(ReflectiveOperationException error){throw new IllegalStateException("read native selection "+accessor,error);}
    }
    private static Object read(Object object,String field){try{var f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new IllegalStateException(e);}}
    private static <T> CompletableFuture<T> server(Function<ServerPlayer,T> action) {
        var result=new CompletableFuture<T>();mc.getSingleplayerServer().execute(()->{try{result.complete(action.apply(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));}catch(Throwable e){result.completeExceptionally(e);}});return result;
    }
    private static String sha(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    private static void capture(String name){String rel="id-insertion-screenshots/"+name+".png";screenshots.add(rel);NativeIdInput.capture(mc,root.resolve(rel));}
    private static void restoreGuiScale(){if(originalGuiScale>=0&&mc.options.guiScale().get()!=originalGuiScale){mc.options.guiScale().set(originalGuiScale);mc.resizeDisplay();}}
    private static void fail(Throwable error){failure=error.toString();if(row!=null){row.put("passed",false);row.put("failure",failure);rows.add(row);}finished=true;passed=false;restoreGuiScale();write();}
    private static void write() {
        try {
            var data=new LinkedHashMap<String,Object>();data.put("status",finished?(passed?"passed":"failed"):"running");
            data.put("inputMode","synthetic_native_gui_events");data.put("dispatchMethod","native MouseHandler button callbacks plus pinned NeoForge pre/screen/post drag chain");data.put("syntheticNativeEvents",true);data.put("scope","Actual native FactoryEditorScreen/menu/carried stacks; mouse callbacks and NeoForge pre/screen/post drag chain; actual viewer sidebar drag; not OS physical input");
            data.put("viewerMode",viewer);data.put("loadedJei",net.neoforged.fml.ModList.get().isLoaded("jei"));data.put("loadedEmi",net.neoforged.fml.ModList.get().isLoaded("emi"));data.put("generation","1.21.1");data.put("phase",phase);data.put("index",index);data.put("ticks",ticks);data.put("failure",failure);
            data.put("planned",cases.stream().map(Case::id).toList());data.put("cases",rows);data.put("screenshots",screenshots);
            data.put("originalGuiScaleOption",originalGuiScale);data.put("inputGuiScale",inputGuiScale);data.put("guiScaleRestored",finished&&mc.options.guiScale().get()==originalGuiScale);
            data.put("limitations",List.of("No OS physical mouse/focus automation","This candidate covers terminal host only; panel/provider/handheld host smoke remains separate","Native shulker duplicates and finite AE cell contents are covered; custom multitank/chemical containers remain pending","No slotClick packet spy; resource/inventory equality is independently checked on client and server"));
            Files.writeString(root.resolve("item-id-insertion-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
