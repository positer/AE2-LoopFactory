package com.example.ae2lfprobe;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedPatternItem;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.item.ModItems;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.Fluids;

/** Test-only comparison frames using native item rendering and the native AE2 Shift hook. */
public final class PatternPreviewAudit {
    private record Case(String id, ItemStack recipe, ItemStack factory) {}
    private static List<Case> cases;
    private static final Map<String,Object> results=new LinkedHashMap<>();
    private PatternPreviewAudit() {}

    public static void open(Minecraft mc,String mode) {
        if(!Set.of("normal","shift","cleared").contains(mode))throw new IllegalArgumentException(mode);
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)throw new IllegalStateException("Preview requires a live integrated client");
        if(cases==null||mode.equals("normal")){cases=createCases(mc);results.clear();}
        if(mode.equals("cleared"))for(var row:cases) {
            var old=FactoryPatternData.get(row.factory);
            // Reuse the same rendered ItemStack identities to detect a stale target after its component changes.
            row.factory.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("",old.factoryId(),ItemStack.EMPTY));
        }
        mc.setScreen(new PreviewScreen(mode));
    }

    /** Only the independent input mixin in this test JAR calls this; other screens retain native input. */
    public static Boolean inputOverride(int key) {
        if(key!=340&&key!=344)return null;
        if(!(Minecraft.getInstance().screen instanceof PreviewScreen screen))return null;
        if(screen.rendering)screen.renderInputQueries++;
        return key==340&&!screen.mode.equals("normal");
    }

    public static boolean check(Minecraft mc) {
        if(!(mc.screen instanceof PreviewScreen screen)||screen.frames<2)return false;
        boolean shift=Screen.hasShiftDown();
        require(shift==!screen.mode.equals("normal"),"Native Shift query did not observe the scoped test input");
        require(screen.renderInputQueries>0,"Native GUI rendering did not query its Shift input");
        var rows=new ArrayList<Map<String,Object>>();
        for(var row:cases) {
            var factory=(EncodedPatternItem<?>)row.factory.getItem();
            var actual=factory.getOutput(row.factory);
            var expected=row.recipe.isEmpty()?ItemStack.EMPTY:((EncodedPatternItem<?>)row.recipe.getItem()).getOutput(row.recipe);
            boolean cleared=screen.mode.equals("cleared")||row.recipe.isEmpty();
            require(cleared?actual.isEmpty():ItemStack.matches(actual,expected),"Preview differs from native recipe: "+row.id);
            if(!row.recipe.isEmpty())require(!expected.isEmpty(),"Native fixture has no target: "+row.id);
            var factoryTooltip=tooltip(row.factory,mc);
            var nativeTooltip=row.recipe.isEmpty()?List.<Component>of():tooltip(row.recipe,mc);
            if(!cleared)require(factoryTooltip.equals(nativeTooltip),"Native recipe tooltip differs: "+row.id);
            var entry=new LinkedHashMap<String,Object>();
            entry.put("case",row.id);
            entry.put("targetEqual",cleared?actual.isEmpty():ItemStack.matches(actual,expected));
            entry.put("expectedEmpty",cleared);
            entry.put("nativeTarget",key(expected));
            entry.put("factoryTarget",key(actual));
            entry.put("tooltipCompared",!cleared);
            entry.put("nativeTooltip",nativeTooltip.stream().map(Component::getString).toList());
            entry.put("factoryTooltip",factoryTooltip.stream().map(Component::getString).toList());
            rows.add(entry);
        }
        results.put(screen.mode,Map.of("status","passed","syntheticModifier",true,
                "modifierScope","InputConstants only while PreviewScreen is active",
                "nativeShiftDown",shift,"renderedFrames",screen.frames,"nativeInputQueriesDuringRender",screen.renderInputQueries,
                "comparisons",rows,"iconRegions",screen.regions,
                "framebuffer",Map.of("width",mc.getMainRenderTarget().width,"height",mc.getMainRenderTarget().height,
                        "guiWidth",screen.width,"guiHeight",screen.height,"guiScale",mc.getWindow().getGuiScale())));
        try {
            var dir=Path.of(System.getProperty("ae2lf.probe.reportDir"));Files.createDirectories(dir);
            Files.writeString(dir.resolve("pattern-preview-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("status",results.keySet().containsAll(Set.of("normal","shift","cleared"))?"passed":"running",
                            "modes",results,"renderPath","Native GuiGraphics item rendering with AE2 GUI hook",
                            "inputScope","Test input substitution, not physical keyboard automation")));
        }catch(java.io.IOException failure){throw new RuntimeException(failure);}
        return true;
    }

    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static String key(ItemStack stack) {
        if(stack.isEmpty())return "empty";
        var wrapped=GenericStack.unwrapItemStack(stack);
        return wrapped==null?net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                :wrapped.what().getType().getId()+"/"+wrapped.what().getId();
    }
    private static List<Component> tooltip(ItemStack stack,Minecraft mc) {
        var lines=new ArrayList<Component>();
        stack.getItem().appendHoverText(stack,Item.TooltipContext.of(mc.level),lines,TooltipFlag.NORMAL);
        return List.copyOf(lines);
    }
    private static ItemStack factory(ItemStack recipe,String code,String binding) {
        var stack=new ItemStack(ModItems.LOOP_FACTORY_PATTERN.get());
        stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,binding,recipe));
        return stack;
    }
    @SuppressWarnings("unchecked")
    private static List<Case> createCases(Minecraft mc) {
        var holder=(RecipeHolder<CraftingRecipe>)(Object)mc.getSingleplayerServer().getRecipeManager().byKey(
                net.minecraft.resources.ResourceLocation.parse("minecraft:oak_planks")).orElseThrow();
        var grid=new ItemStack[9];Arrays.fill(grid,ItemStack.EMPTY);grid[0]=new ItemStack(Items.OAK_LOG);
        var crafting=PatternDetailsHelper.encodeCraftingPattern(holder,grid,new ItemStack(Items.OAK_PLANKS,4),false,false);
        var named=new ItemStack(Items.STONE);named.set(DataComponents.CUSTOM_NAME,Component.literal("Exact preview component"));
        var processing=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE),1)),List.of(new GenericStack(AEItemKey.of(named),1)));
        var fluid=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.ICE),1)),List.of(new GenericStack(AEFluidKey.of(Fluids.WATER),1000)));
        return List.of(new Case("crafting-bound",crafting,factory(crafting,"done","preview-bound")),
                new Case("crafting-empty-unbound",crafting,factory(crafting,"","")),
                new Case("crafting-invalid-unbound",crafting,factory(crafting,"missingFunction","")),
                new Case("processing-component",processing,factory(processing,"missingFunction","")),
                new Case("processing-fluid",fluid,factory(fluid,"","")),
                new Case("no-recipe",ItemStack.EMPTY,factory(ItemStack.EMPTY,"done","preview-bound")));
    }

    public static final class PreviewScreen extends Screen {
        private final String mode;
        private int frames,renderInputQueries;
        private boolean rendering;
        private final List<Map<String,Object>> regions=new ArrayList<>();
        PreviewScreen(String mode){super(Component.literal("Native pattern target comparison"));this.mode=mode;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float delta) {
            g.fill(0,0,width,height,0xff242830);
            g.drawString(font,"Native recipe / Factory wrapper - "+mode,12,10,0xffffffff);
            int nativeX=width*3/5,factoryX=width*4/5;
            g.drawString(font,"Native",nativeX,28,0xffc5cedf);
            g.drawString(font,"Factory",factoryX,28,0xffc5cedf);
            int scale=Math.max(1,Math.min(3,(height-85)/(cases.size()*22)));
            regions.clear();rendering=true;
            try {
                for(int index=0;index<cases.size();index++) {
                    var row=cases.get(index);int y=45+index*22*scale;
                    g.drawString(font,row.id,12,y+4,0xffc5cedf);
                    icon(g,row.recipe.isEmpty()?new ItemStack(ModItems.LOOP_FACTORY_PATTERN.get()):row.recipe,nativeX,y,scale);
                    icon(g,row.factory,factoryX,y,scale);
                    regions.add(Map.of("case",row.id,"native",Map.of("x",nativeX,"y",y,"width",16*scale,"height",16*scale),
                            "factory",Map.of("x",factoryX,"y",y,"width",16*scale,"height",16*scale),
                            "expectPixelEquality",mode.equals("shift")&&!row.recipe.isEmpty()||row.recipe.isEmpty()));
                }
            } finally {rendering=false;}
            g.drawString(font,"Native AE2 render path; scoped test Shift input",12,height-23,0xffaab4c6);
            frames++;
        }
        private void icon(net.minecraft.client.gui.GuiGraphics g,ItemStack stack,int x,int y,int scale) {
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(stack,0,0);g.pose().popPose();
        }
    }
}
