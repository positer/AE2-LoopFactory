package com.example.ae2lfprobe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

/** Observes the actual JEI runtime; deliberately registers no production ghost handler. */
@JeiPlugin
public final class IdInsertionJeiObserver implements IModPlugin {
    private static volatile IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("ae2lf_runtime_probe","identifier_input_observer");}
    @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
    public static void search(String text){if(runtime!=null)runtime.getIngredientFilter().setFilterText(text);}
    public static double[] find(Minecraft mc,Rect2i area,String id) {
        if(runtime==null)return null;
        var overlay=runtime.getIngredientListOverlay();if(!overlay.isListDisplayed())return null;
        // This only confirms production handler registration. Actual acceptance must come from mouse release.
        if(runtime.getScreenHelper().getGhostIngredientHandlers(mc.screen).isEmpty())
            throw new IllegalStateException("Actual JEI runtime has no ghost handler for FactoryEditorScreen");
        for(int y=12;y<mc.screen.height-24;y+=6)for(int x=6;x<mc.screen.width-6;x+=6) {
            if(x>=area.getX()&&x<area.getX()+area.getWidth()&&y>=area.getY()&&y<area.getY()+area.getHeight())continue;
            NativeIdInput.move(mc,x,y);
            if(id.equals("minecraft:stone")) {
                var item=overlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
                if(item!=null&&item.is(Items.STONE))return new double[]{x,y};
            } else if(water(overlay.getIngredientUnderMouse().orElse(null),runtime.getJeiHelpers().getPlatformFluidHelper()))return new double[]{x,y};
        }
        return null;
    }
    private static <F> boolean water(mezz.jei.api.ingredients.ITypedIngredient<?> ingredient,mezz.jei.api.helpers.IPlatformFluidHelper<F> helper) {
        if(ingredient==null)return false;
        var type=helper.getFluidIngredientType();
        return ingredient.getIngredient(type).map(type::getBase).filter(fluid->fluid==net.minecraft.world.level.material.Fluids.WATER).isPresent();
    }
}
