package com.example.ae2lightoptimizer.factory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import java.util.function.Predicate;

/** Adapts the common A!(B,C) selector to the generation's registered AE key types. */
public final class FactoryResourceSelector {
    private FactoryResourceSelector() {}
    public static Predicate<AEKey> parse(String source) {
        return parse(source, index -> { throw new IllegalArgumentException("Recipe parameter requires assigned materials"); });
    }
    public static Predicate<AEKey> parse(String source, java.util.function.IntFunction<java.util.Set<AEKey>> parameters) {
        return parse(source, parameters, FactorySelector.RecipeSet.NONE);
    }
    /** Adds the complete recipe sets {@code P} and {@code O} on top of the single material/output operands. */
    public static Predicate<AEKey> parse(String source, java.util.function.IntFunction<java.util.Set<AEKey>> parameters,
            FactorySelector.RecipeSet sets) {
        var selector = FactorySelector.parse(source);
        return key -> {
            return selector.matches(resourceType(key), key.getId().toString(), index -> parameters.apply(index).contains(key), RESOURCE_TAGS, sets);
        };
    }
    /** Canonical factory resource type of a concrete key, shared by matching and recipe-set resolution. */
    public static String resourceType(AEKey key) {
        return key.getType() == AEKeyType.items() ? "minecraft::item"
                : key.getType() == AEKeyType.fluids() ? "minecraft::fluid"
                : com.example.ae2lightoptimizer.storage.PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(),key.getId().toString()) ? "neoforge::fe"
                : key.getType().getId().toString().replace(":", "::");
    }
    public static boolean matchesKey(AEKey key, String keyType, String id) {
        return resourceType(key).equals(keyType) && key.getId().toString().equals(id);
    }
    /**
     * {@code #namespace:path} operands resolve against the tag registry of the key's own type, so
     * vanilla, mod and datapack tags for items and fluids all work without listing them here.
     */
    public static final FactorySelector.TagLookup RESOURCE_TAGS = (tag, keyType, id) -> switch (keyType) {
        case "minecraft::item" -> inTag(net.minecraft.core.registries.BuiltInRegistries.ITEM, tag, id);
        case "minecraft::fluid" -> inTag(net.minecraft.core.registries.BuiltInRegistries.FLUID, tag, id);
        default -> false;
    };

    private static <T> boolean inTag(net.minecraft.core.Registry<T> registry, String tag, String id) {
        var tagId = net.minecraft.resources.ResourceLocation.tryParse(tag);
        var resourceId = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (tagId == null || resourceId == null) return false;
        return registry.getHolder(resourceId)
                .map(holder -> holder.is(net.minecraft.tags.TagKey.create(registry.key(), tagId)))
                .orElse(false);
    }
}
