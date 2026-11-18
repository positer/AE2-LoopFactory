package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.compat.CrystalScienceCompat;
import io.github.lounode.ae2cs.integration.jei.CrystalGrowthCategory;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

@JeiPlugin
public final class CrystalScienceJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, "crystal_science");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (ModList.get().isLoaded("ae2cs")) {
            GrowthRecipes.register(registration);
        }
    }

    private static final class GrowthRecipes {
        static void register(IRecipeRegistration registration) {
            // Crystal Science only publishes seeds from its own registration list.
            registration.addRecipes(CrystalGrowthCategory.RECIPE_TYPE, List.of(CrystalScienceCompat.SEED.get()));
        }
    }
}
