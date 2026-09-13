package com.example.ae2lightoptimizer.factory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;

/** Pattern identity includes its exact recipe, code snapshot and persistent factory binding. */
public record FactoryPatternData(String code, String factoryId, ItemStack recipe) {
    public static final FactoryPatternData EMPTY = new FactoryPatternData("", "", ItemStack.EMPTY);
    public static final Codec<String> LARGE_TEXT=Codec.either(Codec.STRING,Codec.string(0,8192).listOf())
            .xmap(either->either.map(value->value,parts->String.join("",parts)),value->{
                var chunks=FactoryCodeChunks.split(value,8192);
                return com.mojang.datafixers.util.Either.right(chunks);
            });
    public static final Codec<String> TEXT=LARGE_TEXT.validate(value->value.length()<=FactoryCompiler.MAX_SOURCE_LENGTH?com.mojang.serialization.DataResult.success(value):com.mojang.serialization.DataResult.error(()->"Code exceeds maximum length"));
    public static final Codec<FactoryPatternData> CODEC = RecordCodecBuilder.create(i -> i.group(
            TEXT.fieldOf("code").forGetter(FactoryPatternData::code),
            Codec.string(0, 128).fieldOf("factory_id").forGetter(FactoryPatternData::factoryId),
            ItemStack.OPTIONAL_CODEC.fieldOf("recipe").forGetter(FactoryPatternData::recipe)
    ).apply(i, FactoryPatternData::new));
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Ae2LightOptimizer.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FactoryPatternData>> TYPE =
            COMPONENTS.registerComponentType("factory_program", b -> b.persistent(CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CODEC)));
    public FactoryPatternData { recipe = recipe.copy(); }
    @Override public ItemStack recipe() { return recipe.copy(); }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof FactoryPatternData data && code.equals(data.code)
                && factoryId.equals(data.factoryId) && ItemStack.matches(recipe, data.recipe);
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(code, factoryId, ItemStack.hashItemAndComponents(recipe), recipe.getCount());
    }
    public boolean hasRecipe() { return !recipe.isEmpty(); }
    public FactoryProgram compile() { return FactoryCompiler.compile(code, hasRecipe()); }
    public static FactoryPatternData get(ItemStack stack) { return stack.getOrDefault(TYPE.get(), EMPTY); }
}
