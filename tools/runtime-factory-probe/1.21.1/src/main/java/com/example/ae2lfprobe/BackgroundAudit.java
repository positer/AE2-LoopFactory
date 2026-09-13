package com.example.ae2lfprobe;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;

/** Test-only native frame catalogue and real client/server menu actions. */
public final class BackgroundAudit {
    private record Step(String id, Runnable start, String screen, BooleanSupplier check) {}
    private static final List<Step> steps = new ArrayList<>();
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static int index, elapsed;
    private static boolean started, captured;
    private static Path root;
    private static Minecraft mc;
    private static double cameraX=30.5,cameraY=102,cameraZ=8;
    private static float cameraYaw=180,cameraPitch=15;
    private static java.util.concurrent.CompletableFuture<Void> languageReload;
    private static String expectedSelection;
    private static Screen uploadParent;
    private static volatile String serverError = "";
    private static volatile boolean minimalNetworkRan;
    private static volatile String minimalNetworkDebug="";
    private static final String VALID = "import input,output\nname \"Background QA\"\nwait 1 tick\ndone";
    private static void add(String id, Runnable start, String screen, BooleanSupplier check) {
        steps.add(new Step(id,start,screen,check));
    }
    private static void server(Consumer<net.minecraft.server.level.ServerPlayer> operation) {
        mc.getSingleplayerServer().execute(()->{
            try { operation.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()); }
            catch(Throwable error){serverError=error.toString();}
        });
    }
    private static FactoryBlockEntity factory(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        return (FactoryBlockEntity) player.serverLevel().getBlockEntity(pos);
    }
    private static void openFactory(BlockPos pos) {
        server(p->{p.closeContainer();p.connection.teleport(pos.getX()+.5,pos.getY()+1,pos.getZ()+2.5,180,20);
            var host=factory(p,pos);host.openMenu(p,appeng.menu.locator.MenuLocators.forBlockEntity(host));});
    }
    private static void edit(String code) {
        try {
            var field=mc.screen.getClass().getDeclaredField("editor");field.setAccessible(true);
            ((net.minecraft.client.gui.components.MultiLineEditBox)field.get(mc.screen)).setValue(code);
            ((FactoryEditorMenu)mc.player.containerMenu).saveCode(code);
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static net.minecraft.client.gui.components.MultiLineEditBox codeEditor() {
        try {
            var field=mc.screen.getClass().getDeclaredField("editor");field.setAccessible(true);
            return (net.minecraft.client.gui.components.MultiLineEditBox)field.get(mc.screen);
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static net.minecraft.client.gui.components.MultilineTextField codeField() {
        return ((com.example.ae2lightoptimizer.mixin.MultiLineEditBoxAccess)codeEditor()).ae2lf$textField();
    }
    private static void language(String code) {
        mc.getLanguageManager().setSelected(code);mc.options.languageCode=code;languageReload=mc.reloadResourcePacks();
    }
    private static void setup() {
        root=Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("screenshots");
        try {Files.createDirectories(root);}catch(Exception e){throw new RuntimeException(e);}
        add("ui-factory-editor",()->openFactory(new BlockPos(4,101,4)),"FactoryEditorScreen",()->mc.player.containerMenu instanceof FactoryEditorMenu);
        add("minimal-cable-terminal-redstone",()->server(p->{
            var level=p.serverLevel();
            var terminalPos=new BlockPos(12,101,12);
            var cablePos=new BlockPos(12,101,11);
            level.setBlockAndUpdate(cablePos,FactoryContent.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(terminalPos,FactoryContent.TERMINAL.get().defaultBlockState());
            var terminal=(FactoryBlockEntity)level.getBlockEntity(terminalPos);
            var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("wait 100 tick",terminal.factoryId(),ItemStack.EMPTY));
            terminal.factoryPatterns().setItemDirect(0,pattern);
            level.setBlockAndUpdate(new BlockPos(12,101,13),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        }),"",()->true);
        add("minimal-cable-terminal-redstone-check",()->server(p->{
            var terminal=(FactoryBlockEntity)p.serverLevel().getBlockEntity(new BlockPos(12,101,12));
            minimalNetworkRan=terminal!=null&&terminal.activeJobCount()>0;
            if(!minimalNetworkRan)minimalNetworkDebug=terminal==null?"missing terminal":
                "active="+terminal.getMainNode().isActive()+" grid="+(terminal.getMainNode().getGrid()!=null)
                +" owner="+(FactoryServer.owner(terminal.factoryGrid())==terminal)
                +" jobs="+terminal.activeJobCount()+" error="+terminal.executionError()
                +" redstone="+p.serverLevel().hasNeighborSignal(new BlockPos(12,101,12))
                +" code="+FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0)).code()
                +" id="+FactoryPatternData.get(terminal.factoryPatterns().getStackInSlot(0)).factoryId()
                +" terminalId="+terminal.factoryId();
        }),"",()->{if(!minimalNetworkRan)throw new IllegalStateException(minimalNetworkDebug);return true;});
        add("minimal-cable-terminal-redstone-cleanup",()->server(p->{
            for(var pos:new BlockPos[]{new BlockPos(12,101,12),new BlockPos(12,101,11),new BlockPos(12,101,13)})
                p.serverLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            minimalNetworkRan=false;
        }),"",()->true);
        add("ui-code-tab-indent",()->{
            var editor=codeEditor();editor.setFocused(true);editor.setValue("get minecraft:item from A");
            codeField().seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,0);
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,0);
        },"FactoryEditorScreen",()->codeEditor().getValue().startsWith("    get")&&codeField().cursor()==4);
        add("ui-code-shift-tab-outdent",()->{
            var editor=codeEditor();editor.setFocused(true);editor.setValue("    get minecraft:item from A");
            codeField().seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,0);
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT);
        },"FactoryEditorScreen",()->codeEditor().getValue().startsWith("get"));
        add("ui-code-newline-keeps-indent",()->{
            var editor=codeEditor();editor.setFocused(true);editor.setValue("    get minecraft:item from A");
            codeField().seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,editor.getValue().length());
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0);
        },"FactoryEditorScreen",()->codeEditor().getValue().endsWith("\n    "));
        add("ui-code-syntax-highlight",()->{
            var editor=codeEditor();editor.setFocused(true);
            editor.setValue("import A\nget 64 minecraft:item from A on up # note\nput O1 into source\nwait 1 tick");
        },"FactoryEditorScreen",()->true);
        add("ui-code-editor-inventory-key",()->mc.screen.keyPressed(69,0,0),"FactoryEditorScreen",
            ()->mc.screen instanceof com.example.ae2lightoptimizer.client.FactoryEditorScreen && mc.player.containerMenu instanceof FactoryEditorMenu);
        add("legacy-terminal-facing-up",()->server(p->{
            var block=FactoryContent.TERMINAL.get();
            var property=(net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction>)block.getStateDefinition().getProperty("facing");
            if(property==null)throw new IllegalStateException("Terminal lost the legacy facing property");
            var pos=new BlockPos(8,101,8);
            p.serverLevel().setBlockAndUpdate(pos,block.defaultBlockState().setValue(property,net.minecraft.core.Direction.UP));
            if(p.serverLevel().getBlockEntity(pos)==null)throw new IllegalStateException("Legacy facing=up state could not create its block entity");
            p.serverLevel().removeBlock(pos,false);
        }),"FactoryEditorScreen",()->true);
        add("ui-factory-editor-saved",()->edit(VALID),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).status.contains("Saved"));
                add("ui-code-maximum-unicode",()->edit("#"+"中😀".repeat(21843)+"x\ndone"),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).code().length()==65536 && ((FactoryEditorMenu)mc.player.containerMenu).status.equals("Saved"));
        add("check-code-maximum-binary-nbt",()->server(p->{
            var menu=(FactoryEditorMenu)p.containerMenu;
            var pattern=menu.slots.stream().map(slot->slot.getItem()).filter(stack->stack.getItem() instanceof FactoryPatternItem).findFirst().orElseThrow();
            var encoded=ItemStack.CODEC.encodeStart(p.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),pattern).getOrThrow();
            try {
                var bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.NbtIo.write((net.minecraft.nbt.CompoundTag)encoded,new java.io.DataOutputStream(bytes));
                var decoded=net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
                var restored=ItemStack.CODEC.parse(p.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),decoded).getOrThrow();
                if(!FactoryPatternData.get(restored).code().equals("#"+"中😀".repeat(21843)+"x\ndone"))throw new IllegalStateException("Maximum Unicode code changed in binary NBT");
            }catch(java.io.IOException failure){throw new RuntimeException(failure);}
        }),"FactoryEditorScreen",()->true);
        add("ui-code-after-maximum",()->edit(VALID),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).code().equals(VALID));
        var invalidCodes=java.util.Map.of(
            "unknown-command","definitely_not_a_function",
            "indentation","  done",
            "missing-source","get minecraft:iron_ingot",
            "invalid-selector","get minecraft::item!( from source",
            "recipe-parameter","get P1 from source",
            "zero-wait","wait 0 tick",
            "must-quantity","put must minecraft:stone into storage",
            "output-parameter","get O1 from storage");
        for(var invalid:new java.util.TreeMap<>(invalidCodes).entrySet())
            add("ui-invalid-"+invalid.getKey(),()->edit(invalid.getValue()),"FactoryEditorScreen",()->{
                var menu=(FactoryEditorMenu)mc.player.containerMenu;
                return menu.status.startsWith("Line ")&&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals(VALID);
            });
        add("ui-runtime-ordinary-skip",()->edit("EVERY TICK DO INPUT 1 minecraft:copper_ingot FROM storage OUTPUT TO storage END"),"FactoryEditorScreen",
            ()->{var menu=(FactoryEditorMenu)mc.player.containerMenu;return menu.status.equals("Saved")&&menu.runtimeError.isEmpty()&&!menu.displayStatus().equals("Waiting for transfer resources or destination capacity");});
        add("ui-runtime-wait",()->edit("EVERY TICK DO INPUT MUST 1 minecraft:copper_ingot FROM storage OUTPUT TO storage END"),"FactoryEditorScreen",
            ()->((FactoryEditorMenu)mc.player.containerMenu).displayStatus().equals("Waiting for transfer resources or destination capacity"));
        add("ui-runtime-code-saved",()->edit("func recurse\n    recurse\nend\nrecurse"),"FactoryEditorScreen",
            ()->((FactoryEditorMenu)mc.player.containerMenu).status.equals("Saved"));
        add("ui-runtime-recursion",()->{
            server(p->p.serverLevel().setBlockAndUpdate(new BlockPos(4,101,4).north(),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()));
        },"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).displayStatus().contains("recursion exceeds 64"));
        add("ui-after-runtime-error",()->{
            server(p->p.serverLevel().setBlockAndUpdate(new BlockPos(4,101,4).north(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));edit(VALID);
        },"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).runtimeError.isEmpty()&&((FactoryEditorMenu)mc.player.containerMenu).status.equals("Saved"));
        add("ui-factory-editor-error",()->edit("get"),"FactoryEditorScreen",()->!((FactoryEditorMenu)mc.player.containerMenu).status.isEmpty() && !((FactoryEditorMenu)mc.player.containerMenu).status.contains("Saved"));
        add("ui-factory-provider-blocking",()->openFactory(new BlockPos(0,100,0)),"PatternProviderScreen",()->true);
        add("ui-factory-provider-nonblocking",()->server(p->factory(p,new BlockPos(0,100,0)).getLogic().getConfigManager().putSetting(appeng.api.config.Settings.BLOCKING_MODE,appeng.api.config.YesNo.NO)),"PatternProviderScreen",()->true);
        add("ui-provider-priority",()->server(p->{var host=factory(p,new BlockPos(0,100,0));
            appeng.menu.MenuOpener.open(appeng.menu.implementations.PriorityMenu.TYPE,p,appeng.menu.locator.MenuLocators.forBlockEntity(host));}),"PriorityScreen",()->true);
        add("ui-native-recipe-crafting",()->server(p->{
            p.closeContainer();p.connection.teleport(3.5,101,2.5,180,20);
            var part=(appeng.parts.encoding.PatternEncodingTerminalPart)appeng.api.parts.PartHelper.getPart(p.serverLevel(),new BlockPos(3,100,0),net.minecraft.core.Direction.SOUTH);
            appeng.menu.MenuOpener.open(appeng.menu.me.items.PatternEncodingTermMenu.TYPE,p,appeng.menu.locator.MenuLocators.forPart((appeng.parts.AEBasePart)part));
        }),"PatternEncodingTermScreen",()->true);
        add("ui-native-recipe-slot-prepare",()->server(p->{
            p.closeContainer();p.connection.teleport(3.5,101,2.5,180,20);
            var part=(appeng.parts.encoding.PatternEncodingTerminalPart)appeng.api.parts.PartHelper.getPart(p.serverLevel(),new BlockPos(3,100,0),net.minecraft.core.Direction.SOUTH);
            part.getLogic().getBlankPatternInv().setItemDirect(0,ItemStack.EMPTY);
            part.getLogic().getEncodedPatternInv().setItemDirect(0,ItemStack.EMPTY);
            var blank=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            blank.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("done","background-qa",ItemStack.EMPTY));
            p.getInventory().setItem(0,blank);p.inventoryMenu.broadcastChanges();
            appeng.menu.MenuOpener.open(appeng.menu.me.items.PatternEncodingTermMenu.TYPE,p,appeng.menu.locator.MenuLocators.forPart((appeng.parts.AEBasePart)part));
        }),"PatternEncodingTermScreen",()->mc.player.containerMenu instanceof appeng.menu.me.items.PatternEncodingTermMenu);
        add("ui-native-recipe-slot-place",()->{
            var menu=(appeng.menu.me.items.PatternEncodingTermMenu)mc.player.containerMenu;
            var source=menu.slots.stream().filter(slot->slot.container==mc.player.getInventory()&&slot.getContainerSlot()==0).findFirst().orElseThrow();
            mc.gameMode.handleInventoryMouseClick(menu.containerId,source.index,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player);
        },"PatternEncodingTermScreen",()->{
            var menu=(appeng.menu.me.items.PatternEncodingTermMenu)mc.player.containerMenu;
            return menu.slots.stream().anyMatch(slot->slot.getItem().getItem() instanceof FactoryPatternItem
                    && FactoryPatternData.get(slot.getItem()).code().equals("done"));
        });
        add("ui-native-recipe-encoded",()->((appeng.menu.me.items.PatternEncodingTermMenu)mc.player.containerMenu).encode(),"PatternEncodingTermScreen",
            ()->mc.player.containerMenu.slots.stream().anyMatch(slot->FactoryPatternData.get(slot.getItem()).hasRecipe()));
        for(var mode:appeng.parts.encoding.EncodingMode.values()) if(mode!=appeng.parts.encoding.EncodingMode.CRAFTING)
            add("ui-native-recipe-"+mode.name().toLowerCase(java.util.Locale.ROOT),()->((appeng.menu.me.items.PatternEncodingTermMenu)mc.player.containerMenu).setMode(mode),"PatternEncodingTermScreen",
                ()->((appeng.menu.me.items.PatternEncodingTermMenu)mc.player.containerMenu).getMode()==mode);
        add("ui-crafting-ripper",()->server(p->{
            p.closeContainer();var pos=new BlockPos(1,101,0);
            p.connection.teleport(1.5,102,2.5,180,20);
            var host=(com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity)p.serverLevel().getBlockEntity(pos);
            host.openMenu(p,appeng.menu.locator.MenuLocators.forBlockEntity(host));
        }),"CraftingRipperScreen",()->true);
        for(var deferred:ModItems.PORTABLE_LOOP_STORAGE_CELLS) {
            var item=deferred.get();var id=BuiltInRegistries.ITEM.getKey(item).getPath();
            add("prepare-"+id,()->server(p->{p.closeContainer();var stack=item.getDefaultInstance();
                item.injectAEPower(stack,10000,appeng.api.config.Actionable.MODULATE);
                p.getInventory().setItem(0,stack);p.inventoryMenu.broadcastChanges();
            }),"",()->mc.player.getInventory().getItem(0).getItem()==item);
            add("ui-"+id,()->server(p->item.openFromInventory(p,appeng.menu.locator.MenuLocators.forInventorySlot(0))),"",()->mc.screen instanceof appeng.client.gui.AEBaseScreen);
        }
        add("ui-factory-panel-recipe",()->server(p->{
            p.closeContainer();p.connection.teleport(3.5,101,3.5,180,20);
            var part=(FactoryEncodingPanel)appeng.api.parts.PartHelper.getPart(p.serverLevel(),new BlockPos(2,100,1),net.minecraft.core.Direction.SOUTH);
            appeng.menu.MenuOpener.open(FactoryPanelRecipeMenu.TYPE.get(),p,appeng.menu.locator.MenuLocators.forPart(part));
        }),"FactoryPanelRecipeScreen",()->((FactoryPanelRecipeMenu)mc.player.containerMenu).getMode()==appeng.parts.encoding.EncodingMode.PROCESSING);
        add("ui-factory-panel-code-tab-native",()->{},"FactoryPanelRecipeScreen",()->{
            var label=Component.translatable("gui.ae2lightoptimizer.factory.code_page").getString();
            return mc.screen.children().stream().anyMatch(widget->widget instanceof appeng.client.gui.widgets.TabButton tab
                    &&tab.getMessage().getString().equals(label)
                    &&tab.getStyle()==appeng.client.gui.widgets.TabButton.Style.HORIZONTAL
                    &&tab.getWidth()==22&&tab.getHeight()==22);
        });
        add("ui-factory-panel-processing-only",()->{},"FactoryPanelRecipeScreen",()->{
            var menu=(FactoryPanelRecipeMenu)mc.player.containerMenu;
            try{var diagnostic=root.resolve("processing-diagnostics.txt");if(!Files.exists(diagnostic)){var text=new StringBuilder("mode="+menu.getMode()+"\n");for(var slot:menu.slots)if(slot.isActive())text.append(menu.getSlotSemantic(slot)).append(" ").append(slot.getClass().getName()).append(" active\n");for(var slot:menu.getSlots(appeng.menu.SlotSemantics.SMITHING_TABLE_RESULT))text.append("smithing-result-list ").append(slot.getClass().getName()).append(" active=").append(slot.isActive()).append("\n");Files.writeString(diagnostic,text);}}catch(Exception ignored){}
            if(menu.getMode()!=appeng.parts.encoding.EncodingMode.PROCESSING)return false;
            for(var slot:menu.slots)if(slot.isActive()){
                var semantic=menu.getSlotSemantic(slot);
                if(semantic==appeng.menu.SlotSemantics.CRAFTING_GRID||semantic==appeng.menu.SlotSemantics.CRAFTING_RESULT
                        ||semantic==appeng.menu.SlotSemantics.SMITHING_TABLE_TEMPLATE||semantic==appeng.menu.SlotSemantics.SMITHING_TABLE_BASE
                        ||semantic==appeng.menu.SlotSemantics.SMITHING_TABLE_ADDITION||semantic==appeng.menu.SlotSemantics.SMITHING_TABLE_RESULT
                        ||semantic==appeng.menu.SlotSemantics.STONECUTTING_INPUT) {
                    if(slot.x>=0&&slot.y>=0)return false;
                }
            }
            return true;
        });
        add("ui-factory-panel-encoded",()->((FactoryPanelRecipeMenu)mc.player.containerMenu).encode(),"FactoryPanelRecipeScreen",
            ()->mc.player.containerMenu.slots.stream().anyMatch(slot->FactoryPatternData.get(slot.getItem()).hasRecipe()));
        add("ui-factory-panel-code",()->((FactoryPanelRecipeMenu)mc.player.containerMenu).codePage(),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).isPanel());
        add("ui-factory-panel-code-saved",()->edit("done"),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).status.contains("Saved"));
        add("ui-invalid-output-reference",()->edit("put O2 into source"),"FactoryEditorScreen",()->{
            var menu=(FactoryEditorMenu)mc.player.containerMenu;
            return menu.status.startsWith("Line ")&&menu.status.contains("outputs")&&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals("done");
        });
        add("ui-invalid-recipe-loop",()->edit("while true do\n    wait 1 tick"),"FactoryEditorScreen",()->{
            var menu=(FactoryEditorMenu)mc.player.containerMenu;
            return menu.status.contains("cannot contain unconditional loops")&&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals("done");
        });
        for(var condition:java.util.List.of("(true)","1 > 0","not false","-1"))
            add("ui-debug-recipe-loop-"+java.util.List.of("(true)","1 > 0","not false","-1").indexOf(condition),()->edit("while "+condition+" do\n    wait 1 tick"),"FactoryEditorScreen",()->{
                var menu=(FactoryEditorMenu)mc.player.containerMenu;
                return menu.status.contains("cannot contain unconditional loops")&&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals("done");
            });
        add("ui-debug-missing-face",()->edit("get minecraft:iron_ingot from source on"),"FactoryEditorScreen",()->{
            var menu=(FactoryEditorMenu)mc.player.containerMenu;
            return menu.status.contains("Unknown face")&&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals("done");
        });
        add("ui-invalid-upload-draft",()->edit("missingFunction"),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).status.startsWith("Line "));
        add("ui-invalid-upload",()->{
            var menu=(FactoryEditorMenu)mc.player.containerMenu;
            if(menu.providers.isEmpty())throw new IllegalStateException("Missing provider for invalid upload test");
            menu.upload(menu.providers.split("[|]",2)[0]);
        },"FactoryEditorScreen",()->{
            var menu=(FactoryEditorMenu)mc.player.containerMenu;
            return menu.status.startsWith("Line ")&&FactoryPatternData.get(menu.getSlot(0).getItem()).code().equals("done");
        });
        add("ui-factory-panel-code-zh-cn",()->language("zh_cn"),"FactoryEditorScreen",
            ()->net.minecraft.client.resources.language.I18n.get("gui.ae2lightoptimizer.factory.save").equals("保存代码"));
        add("ui-factory-panel-error-zh-cn",()->edit("get"),"FactoryEditorScreen",
            ()->com.example.ae2lightoptimizer.client.FactoryMessages.status(((FactoryEditorMenu)mc.player.containerMenu).status).getString().contains("第 1 行"));
        add("ui-sfm-error-zh-cn",()->edit("EVERY TICK DO INPUT RETAIN 1 iron_ingot FROM A END"),"FactoryEditorScreen",
            ()->com.example.ae2lightoptimizer.client.FactoryMessages.status(((FactoryEditorMenu)mc.player.containerMenu).status).getString().contains("不支持此 SFM 资源子句"));
        add("ui-factory-panel-saved-zh-cn",()->edit("done"),"FactoryEditorScreen",
            ()->com.example.ae2lightoptimizer.client.FactoryMessages.status(((FactoryEditorMenu)mc.player.containerMenu).status).getString().equals("已保存"));
        add("ui-factory-panel-upload-chooser",()->{
            uploadParent=mc.screen;
            mc.setScreen(new com.example.ae2lightoptimizer.client.FactoryUploadScreen(uploadParent,(FactoryEditorMenu)mc.player.containerMenu));
        },"FactoryUploadScreen",()->true);
        add("ui-factory-panel-upload",()->{
            mc.setScreen(uploadParent);
            var menu=(FactoryEditorMenu)mc.player.containerMenu;
            if(menu.providers.isEmpty())throw new IllegalStateException("Connected provider missing");
            menu.upload(menu.providers.split("[|]",2)[0]);
        },"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).status.contains("Uploaded"));
        add("ui-factory-panel-return-recipe",()->((FactoryEditorMenu)mc.player.containerMenu).recipePage(),"FactoryPanelRecipeScreen",()->true);
        add("part-factory-panel-blue",()->{
            cameraX=3.7;cameraY=100;cameraZ=3.2;cameraYaw=145;cameraPitch=30;
            server(p->{p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);});
        },"world",()->mc.screen==null);
        add("part-factory-panel-red",()->server(p->{
            var bus=(appeng.blockentity.networking.CableBusBlockEntity)p.serverLevel().getBlockEntity(new BlockPos(2,100,1));
            if(!bus.recolourBlock(net.minecraft.core.Direction.SOUTH,appeng.api.util.AEColor.RED,p))throw new IllegalStateException("Cable recolor rejected");
            var panel=(FactoryEncodingPanel)appeng.api.parts.PartHelper.getPart(p.serverLevel(),new BlockPos(2,100,1),net.minecraft.core.Direction.SOUTH);
            if(panel.getHost().getColor()!=appeng.api.util.AEColor.RED)throw new IllegalStateException("Panel did not inherit cable dye");
        }),"world",()->mc.screen==null);
        add("language-en-us",()->language("en_us"),"world",()->net.minecraft.client.resources.language.I18n.get("gui.ae2lightoptimizer.factory.save").equals("Save code"));
        add("prepare-handheld-encoder",()->server(p->{
            p.closeContainer(); var phone=FactoryContent.ENCODER.get().getDefaultInstance();
            var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            phone.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(pattern)));
            p.getInventory().setItem(0,phone);p.inventoryMenu.broadcastChanges();
        }),"",()->mc.player.getInventory().getItem(0).getItem()==FactoryContent.ENCODER.get());
        add("ui-handheld-encoder",()->server(p->appeng.menu.MenuOpener.open(FactoryEditorMenu.TYPE.get(),p,appeng.menu.locator.MenuLocators.forInventorySlot(0))),"FactoryEditorScreen",()->mc.player.containerMenu instanceof FactoryEditorMenu);
        add("ui-handheld-encoder-saved",()->edit(VALID),"FactoryEditorScreen",()->((FactoryEditorMenu)mc.player.containerMenu).status.contains("Saved"));
        add("check-handheld-persistence",()->server(p->{
            p.closeContainer(); var phone=p.getInventory().getItem(0);
            var restored=ItemStack.CODEC.parse(p.serverLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                ItemStack.CODEC.encodeStart(p.serverLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),phone).getOrThrow()).getOrThrow();
            if(!FactoryPatternData.get(restored).code().equals(VALID))throw new IllegalStateException("Encoder draft lost");
            var content=restored.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if(content==null || !FactoryPatternData.get(content.stream().findFirst().orElse(ItemStack.EMPTY)).code().equals(VALID))throw new IllegalStateException("Encoder pattern lost");
            p.getInventory().setItem(0,restored);p.inventoryMenu.broadcastChanges();
        }),"",()->mc.screen==null);
        add("prepare-encoder-network",()->server(p->{
            p.closeContainer();var owner=factory(p,new BlockPos(0,100,0));owner.tags.reconcile(java.util.List.of());owner.tags.reconcile(java.util.List.of("A","B","F"));
            var stack=p.getInventory().getItem(0);var data=FactoryPatternData.get(stack);
            stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(data.code(),owner.factoryId(),ItemStack.EMPTY));
            p.serverLevel().setBlockAndUpdate(new BlockPos(0,101,-2),FactoryContent.CABLE.get().defaultBlockState());
            p.serverLevel().setBlockAndUpdate(new BlockPos(1,101,-2),FactoryContent.CABLE.get().defaultBlockState());
            p.serverLevel().setBlockAndUpdate(new BlockPos(0,101,-1),net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(new BlockPos(1,101,-1),net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            owner.tags.tag("A",new BlockPos(0,101,-1).asLong(),v->true);
            owner.tags.tag("B",new BlockPos(0,101,-1).asLong(),v->true);
            owner.tags.tag("B",new BlockPos(1,101,-1).asLong(),v->true);
            p.connection.teleport(1,102,2,180,20);FactoryEncoderItem.refresh(p,stack);
            var view=stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
            stack.set(FactoryEncoderView.TYPE.get(),new FactoryEncoderView(view.tags(),"A",view.selectedPositions(),
                view.otherPositions(),view.machineTags(),view.dimension()));
            p.inventoryMenu.broadcastChanges();
        }),"",()->mc.screen==null);
        add("check-encoder-select-packet",()->{
            var view=mc.player.getMainHandItem().getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
            if(view.tags().size()<2)throw new IllegalStateException("Expected multiple synchronized tags");
            expectedSelection=view.tags().get((view.tags().indexOf(view.selected())+1)%view.tags().size());
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new FactoryEncoderAction(1,BlockPos.ZERO,false,false,false));
        },"",
            ()->mc.player.getMainHandItem().getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY).selected().equals(expectedSelection));
        add("ui-encoder-tag-highlights",()->{
            cameraX=1;cameraY=102;cameraZ=2;cameraYaw=180;cameraPitch=20;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new FactoryEncoderAction(0,new BlockPos(0,101,-1),true,true,false));
        },"world",()->mc.player.getMainHandItem().getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY).selectedPositions().size()==2);
        add("ui-encoder-machine-tags",()->{}, "world",()->{
            var view=mc.player.getMainHandItem().getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
            return view.machineTags().stream().anyMatch(machine->machine.position()==new BlockPos(0,101,-1).asLong()
                && machine.tags().contains("A") && machine.tags().contains("B"));
        });
        add("ui-encoder-remove-one",()->net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new FactoryEncoderAction(0,new BlockPos(0,101,-1),true,false,false,true)), "world",()->{
            var view=mc.player.getMainHandItem().getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
            boolean ok=view.selectedPositions().size()==1 && view.machineTags().stream().anyMatch(machine->
                machine.position()==new BlockPos(0,101,-1).asLong() && !machine.tags().contains("B"));
            if(!ok)throw new IllegalStateException("remove-one selected="+view.selected()+" positions="+view.selectedPositions()+" machines="+view.machineTags());
            return true;
        });
        add("ui-encoder-remove-bulk",()->net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new FactoryEncoderAction(0,new BlockPos(1,101,-1),true,true,false,true)), "world",()->{
            var view=mc.player.getMainHandItem().getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
            boolean ok=view.selectedPositions().isEmpty() && view.machineTags().stream().noneMatch(machine->machine.tags().contains("B"));
            if(!ok)throw new IllegalStateException("remove-bulk selected="+view.selected()+" positions="+view.selectedPositions()+" machines="+view.machineTags());
            return true;
        });
        add("ui-encoder-labels-cleared",()->server(p->{p.getInventory().setItem(0,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();}),"world",()->{
            try {
                var field=com.example.ae2lightoptimizer.client.FactoryEncoderClient.class.getDeclaredField("SCREEN_LABELS");
                field.setAccessible(true);
                return ((java.util.List<?>)field.get(null)).isEmpty();
            }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
        });
        var items=BuiltInRegistries.ITEM.stream().filter(i->BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("ae2lightoptimizer"))
                .sorted(Comparator.comparing(i->BuiltInRegistries.ITEM.getKey(i).toString())).toList();
        add("ui-close",()->server(p->p.closeContainer()),"world",()->mc.screen==null);
        add("model-ae2-pattern-encoding-terminal-reference",
            ()->mc.setScreen(new ModelScreen(appeng.core.definitions.AEParts.PATTERN_ENCODING_TERMINAL.stack())),
            "ModelScreen",()->true);
        for(var item:items) {
            String id=BuiltInRegistries.ITEM.getKey(item).getPath();
            // Install the native model review screen after the server close packet has settled.
            add("model-"+id,()->mc.setScreen(new ModelScreen(item.getDefaultInstance())),"ModelScreen",()->true);
        }
        for(var item:items) if(item instanceof BlockItem bi) {
            String id=BuiltInRegistries.ITEM.getKey(item).getPath();
            add("block-"+id,()->{mc.setScreen(null);server(p->{
                p.closeContainer();var level=p.serverLevel();
                for(int x=28;x<=32;x++)for(int z=3;z<=10;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    for(int y=101;y<=105;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
                var state=bi.getBlock().defaultBlockState();
                if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING))
                    state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.SOUTH);
                level.setBlockAndUpdate(new BlockPos(30,101,4),state);
                p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(30.5,102,8,180,15);
            });},"world",()->mc.screen==null);
        }
        for(var facing:net.minecraft.core.Direction.values()) for(boolean reverse:new boolean[]{false,true}) {
            add("arrows-"+facing.name().toLowerCase(java.util.Locale.ROOT)+(reverse?"-bottom-north-west":"-top-south-east"),()->{
                cameraX=reverse?27:33.5;cameraY=reverse?101:105;cameraZ=reverse?0:8;
                cameraYaw=reverse?-40:140;cameraPitch=reverse?-10:32;
                mc.setScreen(null);server(p->{var level=p.serverLevel();
                    for(int x=28;x<=33;x++)for(int z=1;z<=7;z++)for(int y=101;y<=106;y++)
                        level.setBlockAndUpdate(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(30,103,4),FactoryContent.PROVIDER.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,facing));
                    p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);
                });
            },"world",()->mc.screen==null);
        }
        // Genuine loaded third-party keys/cells, invoked on the server thread.
        add("multi-tag-machine-groups",()->{
            cameraX=215;cameraY=105;cameraZ=54;cameraYaw=145;cameraPitch=30;
            server(p->{p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);MultiTagAudit.start(p.serverLevel());});
        },"world",()->MultiTagAudit.finished&&MultiTagAudit.passed);
        add("complex-factory-flows",()->{
            cameraX=483;cameraY=105;cameraZ=86;cameraYaw=150;cameraPitch=40;
            server(p->{p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);ComplexFlowAudit.start(p.serverLevel());});
        },"world",()->ComplexFlowAudit.finished&&ComplexFlowAudit.passed);
        add("stress-world-transfers",()->server(p->StressAudit.start(p.serverLevel())),"world",()->StressAudit.finished && StressAudit.passed);
        add("topology-service-uniqueness",()->server(p->TopologyAudit.start(p.serverLevel())),"world",()->TopologyAudit.finished && TopologyAudit.passed);
        add("terminal-runtime-sfm",()->server(p->TerminalAudit.start(p.serverLevel())),"world",()->TerminalAudit.finished && TerminalAudit.passed);
        add("induction-card-cache",()->server(p->InductionAudit.start(p.serverLevel())),"world",()->InductionAudit.finished && InductionAudit.passed);
        add("compatibility-registry",()->server(p->CompatibilityAudit.run(p.serverLevel())),"world",()->CompatibilityAudit.finished && CompatibilityAudit.passed);
        if(Boolean.getBoolean("ae2lf.probe.addonCompat"))
            add("addon-soul-capability",()->server(p->SoulCompatAudit.start(p.serverLevel())),"world",()->SoulCompatAudit.finished&&SoulCompatAudit.passed);
        if(Boolean.getBoolean("ae2lf.probe.recipeSet"))
            add("recipe-set-p-o",()->server(p->RecipeSetAudit.start(p.serverLevel())),"world",()->RecipeSetAudit.finished&&RecipeSetAudit.passed);
        steps.addFirst(steps.removeLast()); // Fail fast on optional storage identity mismatches.
        server(p->{var pos=new BlockPos(0,100,-2);p.serverLevel().setBlockAndUpdate(pos,appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
            ((appeng.blockentity.storage.DriveBlockEntity)p.serverLevel().getBlockEntity(pos)).getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());});
        server(p->{var level=p.serverLevel();
            level.setBlockAndUpdate(new BlockPos(1,101,0),com.example.ae2lightoptimizer.block.ModBlocks.CRAFTING_RIPPER.get().defaultBlockState());
            appeng.api.parts.PartHelper.setPart(level,new BlockPos(3,100,0),null,p,appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
            var part=appeng.api.parts.PartHelper.setPart(level,new BlockPos(3,100,0),net.minecraft.core.Direction.SOUTH,p,appeng.core.definitions.AEParts.PATTERN_ENCODING_TERMINAL.get());
            part.getLogic().getEncodedInputInv().setStack(0,new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.OAK_LOG),1));
            var blank=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            blank.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("done","background-qa",ItemStack.EMPTY));
            part.getLogic().getBlankPatternInv().setItemDirect(0,blank);
            appeng.api.parts.PartHelper.setPart(level,new BlockPos(2,100,1),null,p,appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.BLUE));
            var panel=appeng.api.parts.PartHelper.setPart(level,new BlockPos(2,100,1),net.minecraft.core.Direction.SOUTH,p,FactoryContent.PANEL.get());
            panel.getLogic().getEncodedInputInv().setStack(0,new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.OAK_LOG),1));
            panel.getLogic().getEncodedOutputInv().setStack(0,new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS),1));
            var panelBlank=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            panelBlank.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("done","",ItemStack.EMPTY));
            panel.getLogic().getBlankPatternInv().setItemDirect(0,panelBlank);
            level.setBlockAndUpdate(new BlockPos(3,101,4),appeng.core.definitions.AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
            ((appeng.blockentity.networking.EnergyCellBlockEntity)level.getBlockEntity(new BlockPos(3,101,4))).injectAEPower(1_000_000,appeng.api.config.Actionable.MODULATE);
            var terminal=(FactoryBlockEntity)level.getBlockEntity(new BlockPos(4,101,4));
            var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("import input,output\ndone",terminal.factoryId(),ItemStack.EMPTY));
            terminal.factoryPatterns().setItemDirect(0,pattern);terminal.setFactoryDraft("import input,output\ndone");
        });
        if(Boolean.getBoolean("ae2lf.probe.channel"))
            add("channel-indentation-nesting",()->{
                cameraX=340.5;cameraY=102;cameraZ=244.5;cameraYaw=180;cameraPitch=25;
                server(p->{p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);ChannelAudit.start(p.serverLevel());});
            },"world",()->ChannelAudit.passed());
        if(Boolean.getBoolean("ae2lf.probe.guideOnly")) {
            steps.clear();
            for(String locale:List.of("en_us","zh_cn")) {
                add("ui-guide-language-"+locale,()->language(locale),"",()->languageReload!=null&&languageReload.isDone());
                add("ui-guide-top-"+locale,()->{
                    var guide=guideme.Guides.getById(appeng.items.tools.GuideItem.GUIDE_ID);
                    var page=net.minecraft.resources.ResourceLocation.parse("ae2lightoptimizer:items-blocks-machines/loop_factory.md");
                    if(!guide.pageExists(page))throw new IllegalStateException("Factory guide page missing");
                    var screen=guideme.internal.screen.GuideScreen.openNew(guide,guideme.PageAnchor.page(page));
                    mc.setScreen(screen);
                    String document;
                    try {
                        var field=guideme.internal.screen.GuideScreen.class.getDeclaredField("currentPage");
                        field.setAccessible(true);
                        document=((guideme.GuidePage)field.get(screen)).document().getTextContent();
                    } catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
                    if(!document.contains("StageOne")||!document.contains("moveOne")||!document.contains("O2")||!document.contains("EVERY")||document.contains("PARSING ERROR"))
                        throw new IllegalStateException("Guide did not compile all runnable examples: "+document.substring(0,Math.min(240,document.length())));
                },"GuideScreen",()->((guideme.internal.screen.GuideScreen)mc.screen).getCurrentPageId().toString().equals("ae2lightoptimizer:items-blocks-machines/loop_factory.md"));
                for(int page=1;page<=60;page++) {
                    add("ui-guide-page-"+page+"-"+locale,()->{
                        var screen=(guideme.internal.screen.GuideScreen)mc.screen;
                        var viewport=screen.getDocumentViewport();
                        screen.scaledMouseScrolled(viewport.x()+viewport.width()/2.0,viewport.y()+viewport.height()/2.0,0,-8);
                    },"GuideScreen",()->true);
                }
                add("ui-guide-recipes-"+locale,()->{
                    var screen=(guideme.internal.screen.GuideScreen)mc.screen;
                    var viewport=screen.getDocumentViewport();
                    screen.scaledMouseScrolled(viewport.x()+viewport.width()/2.0,viewport.y()+viewport.height()/2.0,0,-10000);
                },"GuideScreen",()->true);
            }
        }
        if(Boolean.getBoolean("ae2lf.probe.mekOnly")) {
            steps.clear();
            add("native-chemical-FE-world",()->{
                cameraX=730;cameraY=106;cameraZ=86;cameraYaw=155;cameraPitch=35;
                server(p->{p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);NativeCapabilityAudit.start(p.serverLevel());});
            },"world",()->NativeCapabilityAudit.finished&&NativeCapabilityAudit.passed);
            for(int machine:new int[]{3,6})add("ui-native-storage-"+machine,()->server(p->{
                p.closeContainer();var pos=NativeCapabilityAudit.positions.get(machine);p.connection.teleport(pos.getX()+.5,pos.getY()+1,pos.getZ()+2,180,15);
                p.gameMode.useItemOn(p,p.serverLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.SOUTH,pos,false));
            }),machine==3?"GuiChemicalTank":"GuiEnergyCube",()->mc.screen!=null);
            add("mekanism-bulk-world",()->{
                cameraX=645;cameraY=105;cameraZ=88;cameraYaw=150;cameraPitch=35;
                server(p->{if(!NativeCapabilityAudit.passed)throw new IllegalStateException("Native capability prerequisite failed");p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);MekanismBulkAudit.start(p.serverLevel());});
            },"world",()->MekanismBulkAudit.finished&&MekanismBulkAudit.passed);
            for(int machine:new int[]{0,4})add("ui-mekanism-factory-"+machine,()->server(p->{
                var pos=MekanismBulkAudit.machines.get(machine);p.connection.teleport(pos.getX()+.5,pos.getY()+1,pos.getZ()+2,180,15);
                p.gameMode.useItemOn(p,p.serverLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.SOUTH,pos,false));
            }),"GuiFactory",()->mc.screen!=null);
        }
        // Finish base-grid assertions before moving to the remote huge-quantity fixture.
        if(!Boolean.getBoolean("ae2lf.probe.guideOnly"))for(String previewMode:java.util.List.of("normal","shift","cleared"))
            add("factory-pattern-preview-"+previewMode,()->PatternPreviewAudit.open(mc,previewMode),"PreviewScreen",()->PatternPreviewAudit.check(mc));
        if(Boolean.getBoolean("ae2lf.probe.hugeQuantities"))
            add("huge-quantity-logistics",()->{mc.setScreen(null);cameraX=1204;cameraY=104;cameraZ=486;cameraYaw=145;cameraPitch=30;server(p->{p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);HugeQuantityAudit.start(p.serverLevel());});},"world",()->HugeQuantityAudit.finished && HugeQuantityAudit.passed);
        if(Boolean.getBoolean("ae2lf.probe.recovery"))
            add("huge-recovery-logistics",()->{mc.setScreen(null);cameraX=1284;cameraY=104;cameraZ=486;cameraYaw=145;cameraPitch=30;server(p->{p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);RecoveryAudit.start(p.serverLevel());});},"world",()->RecoveryAudit.finished && RecoveryAudit.passed);
        if(Boolean.getBoolean("ae2lf.probe.chunkLifecycle"))
            add("chunk-lifecycle-logistics",()->{mc.setScreen(null);cameraX=0;cameraY=104;cameraZ=0;cameraYaw=145;cameraPitch=30;server(p->{p.closeContainer();p.connection.teleport(cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);ChunkLifecycleAudit.begin(p.serverLevel(),p);});},"world",()->ChunkLifecycleAudit.finished && ChunkLifecycleAudit.passed);
        if(Boolean.getBoolean("ae2lf.probe.idInsertionOnly"))
            add("code-id-insertion",()->ItemIdInsertionAudit.begin(mc),"",()->ItemIdInsertionAudit.finished && ItemIdInsertionAudit.passed);
        if(Boolean.getBoolean("ae2lf.probe.visualOnly")) steps.removeIf(step->!step.id.startsWith("arrows-") && !step.id.equals("model-loop_factory_pattern_provider") && !step.id.equals("block-loop_factory_pattern_provider"));
        if(Boolean.getBoolean("ae2lf.probe.groupOnly"))steps.removeIf(step->!step.id.equals("multi-tag-machine-groups"));
        if(Boolean.getBoolean("ae2lf.probe.uiOnly"))steps.removeIf(step->step.id.startsWith("model-")||step.id.startsWith("block-")||step.id.startsWith("arrows-")||step.id.equals("multi-tag-machine-groups")||step.id.equals("stress-world-transfers")||step.id.equals("topology-service-uniqueness")||step.id.equals("terminal-runtime-sfm")||step.id.equals("induction-card-cache")||step.id.equals("compatibility-registry")||step.id.equals("complex-factory-flows")||step.id.equals("parallel-native-orders")||step.id.equals("native-transaction-capabilities"));
        if(Boolean.getBoolean("ae2lf.probe.panelVisualOnly")) steps.removeIf(step->!step.id.startsWith("part-factory-panel-")&&!step.id.equals("model-loop_factory_pattern_encoding_panel")&&!step.id.equals("model-ae2-pattern-encoding-terminal-reference")&&!step.id.equals("block-loop_factory_pattern_encoding_panel"));
        if(Boolean.getBoolean("ae2lf.probe.parserOnly"))steps.removeIf(step->!step.id.startsWith("ui-debug-")&&!java.util.Set.of("ui-factory-panel-recipe","ui-factory-panel-encoded","ui-factory-panel-code","ui-factory-panel-code-saved").contains(step.id));
        if(Boolean.getBoolean("ae2lf.probe.addonCompat"))steps.removeIf(step->!java.util.Set.of("compatibility-registry","addon-soul-capability").contains(step.id));
        if(Boolean.getBoolean("ae2lf.probe.dispatchOnly"))steps.removeIf(step->!step.id.equals("complex-factory-flows"));
        if(Boolean.getBoolean("ae2lf.probe.hugeOnly"))steps.removeIf(step->!step.id.equals("huge-quantity-logistics"));
        if(Boolean.getBoolean("ae2lf.probe.recoveryOnly"))steps.removeIf(step->!step.id.equals("huge-recovery-logistics"));
        if(Boolean.getBoolean("ae2lf.probe.chunkOnly"))steps.removeIf(step->!step.id.equals("chunk-lifecycle-logistics"));
        if(Boolean.getBoolean("ae2lf.probe.extremeOnly"))steps.removeIf(step->!java.util.Set.of("huge-quantity-logistics","huge-recovery-logistics","factory-pattern-preview-normal","factory-pattern-preview-shift","factory-pattern-preview-cleared").contains(step.id));
        if(Boolean.getBoolean("ae2lf.probe.recipeSetOnly"))steps.removeIf(step->!step.id.equals("recipe-set-p-o"));
        if(Boolean.getBoolean("ae2lf.probe.idInsertionOnly"))steps.removeIf(step->!step.id.equals("code-id-insertion"));
        if(Boolean.getBoolean("ae2lf.probe.channelOnly"))steps.removeIf(step->!step.id.equals("channel-indentation-nesting"));
        started=true;
    }
    public static void tick(Minecraft client) {
        mc=client;
        try {
            if(!started)setup();
            if(mc.getSingleplayerServer()==null || mc.player==null){
                results.add(Map.of("id","connection","passed",false,"failure","Client disconnected; aborting remaining world/UI tests"));
                index=steps.size();write();RestartState.visualReady=false;mc.stop();return;
            }
            if(index>=steps.size()) {
                write();RestartState.visualReady=false;RestartState.exitRequested=true;return;
            }
            Step step=steps.get(index);
            if(elapsed++==0){serverError="";captured=false;step.start.run();}
            if(step.id.equals("channel-indentation-nesting"))
                ChannelAudit.observeClientHost(mc.level!=null && mc.getOverlay()==null
                    && mc.level.getBlockEntity(new BlockPos(340,100,241)) instanceof FactoryBlockEntity
                    && mc.level.getBlockState(new BlockPos(340,100,241)).is(FactoryContent.TERMINAL.get()));
            SyntaxHighlightAudit.tick(mc);
            if(step.id.equals("channel-indentation-nesting") && !ChannelAudit.finished() && elapsed<1400)return;

            if(step.id.equals("code-id-insertion"))ItemIdInsertionAudit.tick(mc);
            if(step.screen.equals("world") && mc.player!=null) {
                if(Boolean.getBoolean("ae2lf.probe.visualOnly")){mc.options.hideGui=true;mc.options.fov().set(40);}
                mc.player.getAbilities().flying=true;mc.player.setPos(cameraX,cameraY,cameraZ);mc.player.setYRot(cameraYaw);mc.player.setXRot(cameraPitch);
            }
            if(elapsed<20)return;
            if(step.id.equals("code-id-insertion") && !ItemIdInsertionAudit.finished && elapsed<ItemIdInsertionAudit.timeoutTicks()+400)return;
            if(Boolean.getBoolean("ae2lf.probe.mekOnly")&&!serverError.isEmpty())throw new IllegalStateException(serverError);
            if(step.id.equals("native-chemical-FE-world")&&!NativeCapabilityAudit.finished&&elapsed<2500)return;
            if(step.id.equals("mekanism-bulk-world")&&!MekanismBulkAudit.finished&&elapsed<MekanismBulkAudit.timeoutTicks()+400)return;
            if(step.id.equals("complex-factory-flows")&&!ComplexFlowAudit.finished&&elapsed<ComplexFlowAudit.timeoutTicks()+400)return;
            if(step.id.equals("multi-tag-machine-groups")&&!MultiTagAudit.finished&&elapsed<1400)return;
            if(step.id.equals("induction-card-cache") && !InductionAudit.finished && elapsed<800)return;
            if(step.id.equals("terminal-runtime-sfm") && !TerminalAudit.finished && elapsed<800)return;
            if(step.id.equals("topology-service-uniqueness") && !TopologyAudit.finished && elapsed<800)return;
            if(step.id.equals("stress-world-transfers") && !StressAudit.finished && elapsed<StressAudit.timeoutTicks()+400)return;
            if(step.id.equals("huge-recovery-logistics") && !RecoveryAudit.finished && elapsed<RecoveryAudit.timeoutTicks()+400)return;
            if(step.id.equals("huge-quantity-logistics") && !HugeQuantityAudit.finished && elapsed<HugeQuantityAudit.timeoutTicks()+400)return;
            if(step.id.equals("chunk-lifecycle-logistics") && !ChunkLifecycleAudit.finished && elapsed<ChunkLifecycleAudit.timeoutTicks()+400)return;
            if(step.id.equals("ui-encoder-machine-tags") && elapsed<100)return;
            if(step.id.equals("ui-encoder-labels-cleared") && elapsed<100)return;
            if(step.id.equals("minimal-cable-terminal-redstone") && elapsed<80)return;
            if(step.id.equals("addon-soul-capability") && !SoulCompatAudit.finished && elapsed<1200)return;
            if(step.id.equals("recipe-set-p-o") && !RecipeSetAudit.finished && elapsed<1200)return;
            if(languageReload!=null && (!languageReload.isDone() || mc.getOverlay()!=null)) {
                if(elapsed<1200)return;
                throw new IllegalStateException("Language reload overlay did not finish");
            }
            boolean match=step.screen.isEmpty() || (mc.screen==null ? step.screen.equals("world") : mc.screen.getClass().getSimpleName().equals(step.screen));
            if(!match && elapsed<120)return;
            if(!captured) {
                boolean ok=match && serverError.isEmpty() && step.check.getAsBoolean();
                var row=new LinkedHashMap<String,Object>();row.put("id",step.id);row.put("passed",ok);
                row.put("actualScreen",mc.screen==null?"world":mc.screen.getClass().getName());row.put("serverError",serverError);
                row.put("windowVisible",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE));
                if(mc.screen!=null) {
                    row.put("width",mc.screen.width);row.put("height",mc.screen.height);
                    row.put("widgets",mc.screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).map(x->{
                        var w=(net.minecraft.client.gui.components.AbstractWidget)x;
                        return Map.of("class",x.getClass().getSimpleName(),"x",w.getX(),"y",w.getY(),"width",w.getWidth(),"height",w.getHeight(),"visible",w.visible);
                    }).toList());
                }
                results.add(row);capture(step.id);captured=true;write();
            }
            if(Files.isRegularFile(root.resolve(step.id+".png"))){index++;elapsed=0;}
        }catch(Throwable failure) {
            results.add(Map.of("id",index<steps.size()?steps.get(index).id:"setup","passed",false,"failure",failure.toString()));
            index++;elapsed=0;write();
        }
    }
    private static void write() {
        var planned = steps.stream().map(Step::id).toList();
        var counts = results.stream().collect(java.util.stream.Collectors.groupingBy(
                row -> String.valueOf(row.get("id")), LinkedHashMap::new, java.util.stream.Collectors.counting()));
        var missing = planned.stream().filter(id -> !counts.containsKey(id)).toList();
        var duplicate = counts.entrySet().stream().filter(entry -> entry.getValue() != 1L)
                .map(Map.Entry::getKey).toList();
        var unexpected = counts.keySet().stream().filter(id -> !planned.contains(id)).toList();
        boolean uniquePlan = planned.stream().distinct().count() == planned.size();
        boolean complete = !planned.isEmpty() && uniquePlan && results.size() == planned.size()
                && missing.isEmpty() && duplicate.isEmpty() && unexpected.isEmpty();
        var coverage = Map.of("allPlannedExactlyOnce", complete, "uniquePlannedIds", uniquePlan,
                "plannedCount", planned.size(), "recordedCount", results.size(),
                "missingSteps", missing, "duplicateResults", duplicate, "unexpectedResults", unexpected);
        try {Files.writeString(root.getParent().resolve("ui-item-block-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
            Map.of("status",index>=steps.size()?(complete?"completed":"incomplete"):"running","results",results,
            "plannedSteps",planned,"stepCoverage",coverage,
            "probeProperties",System.getProperties().stringPropertyNames().stream().filter(key->key.startsWith("ae2lf.probe.")).collect(java.util.stream.Collectors.toMap(key->key,System::getProperty)),
            "missing",List.of("handheld key events routed through validated server actions","advanced SFM clauses remain unsupported","uninstalled addon capabilities and long-duration chunk lifecycle not covered"),
            "captureMethod","Native Minecraft framebuffer; ModelScreen uses native item renderer and is not a product UI; UI edits use client menu packet action")));
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private static void capture(String id) {
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(root.resolve(id+".png"));}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private static final class ModelScreen extends Screen {
        private final ItemStack stack;
        ModelScreen(ItemStack stack){super(Component.literal("Native item model evidence"));this.stack=stack;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xff242830);
            g.drawCenteredString(font,BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),width/2,18,0xffffff);
            g.drawCenteredString(font,stack.getHoverName(),width/2,34,0xbcc5d5);
            g.pose().pushPose();g.pose().translate(width/2f-48,height/2f-48,0);g.pose().scale(6,6,1);
            g.renderItem(stack,0,0);g.pose().popPose();
            g.drawCenteredString(font,"Native Minecraft item renderer / QA view",width/2,height-24,0xaab4c6);
        }
    }
}
