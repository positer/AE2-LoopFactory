package com.example.ae2lightoptimizer.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/** Exact smithing inputs for recipes with genuinely absent optional slots. */
public final class CatalogSmithingPattern implements IMolecularAssemblerSupportedPattern {
    private static final String MARKER = "ae2lightoptimizer:optional_smithing";
    private static final String[] SLOT_NAMES = {"template", "base", "addition"};
    private static boolean decoderRegistered;

    private final AEItemKey definition;
    private final ResourceKey<Recipe<?>> recipeId;
    private final AEItemKey[] slots;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    private CatalogSmithingPattern(AEItemKey definition, ResourceKey<Recipe<?>> recipeId,
            SmithingRecipeInput input, ItemStack output) {
        this.definition = definition;
        this.recipeId = recipeId;
        this.slots = new AEItemKey[3];
        var nonempty = new ArrayList<IInput>();
        for (int i = 0; i < slots.length; i++) {
            var key = AEItemKey.of(input.getItem(i));
            slots[i] = key;
            if (key != null) nonempty.add(new ExactInput(key));
        }
        inputs = nonempty.toArray(IInput[]::new);
        outputs = List.of(new GenericStack(Objects.requireNonNull(AEItemKey.of(output)), output.getCount()));
    }

    /** The native decoder ignores blank patterns; our marked definition is claimed by this decoder only. */
    public static synchronized void registerDecoder() {
        if (decoderRegistered) return;
        PatternDetailsHelper.registerDecoder(new IPatternDetailsDecoder() {
            @Override
            public boolean isEncodedPattern(ItemStack stack) {
                return marked(AEItemKey.of(stack));
            }

            @Override
            public IPatternDetails decodePattern(AEItemKey definition, Level level) {
                return decode(definition, level);
            }
        });
        decoderRegistered = true;
    }

    /** Builds from the registered recipe and its real assembled output, never an invented empty-slot item. */
    public static CatalogSmithingPattern fromRecipe(RecipeHolder<?> holder, SmithingRecipeInput input, Level level) {
        var live = liveRecipe(holder.id(), level);
        if (live == null || live.value() != holder.value()) throw new IllegalArgumentException("Unregistered smithing recipe");
        var copied = copy(input);
        var recipe = (SmithingRecipe) live.value();
        if (!optional(recipe) || !unitInputs(copied) || !recipe.matches(copied, level)) {
            throw new IllegalArgumentException("Invalid optional smithing inputs");
        }
        var output = recipe.assemble(copied);
        if (output.isEmpty()) throw new IllegalArgumentException("Empty smithing output");
        var data = new CompoundTag();
        data.putInt(MARKER, 1);
        data.putString("recipe", holder.id().identifier().toString());
        for (int i = 0; i < 3; i++) writeStack(data, SLOT_NAMES[i], input.getItem(i), level);
        writeStack(data, "output", output, level);
        var stack = AEItems.BLANK_PATTERN.stack();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return new CatalogSmithingPattern(Objects.requireNonNull(AEItemKey.of(stack)), holder.id(), copied, output);
    }

    /** Invalid, stale or forged definitions are refused without changing any world or inventory state. */
    public static CatalogSmithingPattern decode(AEItemKey definition, Level level) {
        if (!marked(definition)) return null;
        try {
            var data = definition.get(DataComponents.CUSTOM_DATA).copyTag();
            if (data.getIntOr(MARKER, 0) != 1) return null;
            var id = ResourceKey.create(Registries.RECIPE, Identifier.parse(data.getStringOr("recipe", "")));
            var holder = liveRecipe(id, level);
            if (holder == null || !optional((SmithingRecipe) holder.value())) return null;
            var input = new SmithingRecipeInput(readStack(data, "template", level),
                    readStack(data, "base", level), readStack(data, "addition", level));
            var expected = readStack(data, "output", level);
            var recipe = (SmithingRecipe) holder.value();
            if (!unitInputs(input) || expected.isEmpty() || !recipe.matches(copy(input), level)
                    || !ItemStack.matches(expected, recipe.assemble(copy(input)))) return null;
            return new CatalogSmithingPattern(definition, id, input, expected);
        } catch (RuntimeException invalidDefinition) {
            return null;
        }
    }

    private static boolean marked(AEItemKey definition) {
        if (definition == null || !definition.is(AEItems.BLANK_PATTERN)) return false;
        var data = definition.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(MARKER);
    }

    private static RecipeHolder<?> liveRecipe(ResourceKey<Recipe<?>> id, Level level) {
        if (!(level instanceof ServerLevel server)) return null;
        var holder = server.getServer().getRecipeManager().byKey(id).orElse(null);
        return holder != null && holder.value() instanceof SmithingRecipe
                && holder.value().getType() == RecipeType.SMITHING ? holder : null;
    }

    private static boolean optional(SmithingRecipe recipe) {
        return recipe.templateIngredient().isEmpty() || recipe.additionIngredient().isEmpty();
    }

    private static boolean unitInputs(SmithingRecipeInput input) {
        if (input.base().isEmpty()) return false;
        for (int i = 0; i < 3; i++) {
            if (!input.getItem(i).isEmpty() && input.getItem(i).getCount() != 1) return false;
        }
        return true;
    }

    private static SmithingRecipeInput copy(SmithingRecipeInput input) {
        return new SmithingRecipeInput(input.template().copy(), input.base().copy(), input.addition().copy());
    }

    private static void writeStack(CompoundTag data, String name, ItemStack stack, Level level) {
        if (!stack.isEmpty()) data.put(name, ItemStack.CODEC.encodeStart(
                level.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow());
    }

    private static ItemStack readStack(CompoundTag data, String name, Level level) {
        var tag = data.get(name);
        return tag == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(
                level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
    }

    public ResourceKey<Recipe<?>> getRecipeId() { return recipeId; }
    public String recipeId() { return recipeId.identifier().toString(); }
    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return inputs; }
    @Override public List<GenericStack> getOutputs() { return outputs; }

    @Override
    public boolean isSlotEnabled(int slot) {
        return slot >= 3 && slot <= 5 && slots[slot - 3] != null;
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey key, Level level) {
        return isSlotEnabled(slot) && slots[slot - 3].equals(key);
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor grid) {
        if (inputHolder.length != inputs.length) throw new IllegalArgumentException("Wrong smithing input count");
        for (int i = 0; i < inputs.length; i++) {
            var key = inputs[i].getPossibleInputs()[0].what();
            if (inputHolder[i].get(key) < 1) throw new IllegalArgumentException("Missing smithing input");
        }
        for (int slot = 0; slot < 9; slot++) grid.set(slot, ItemStack.EMPTY);
        int index = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (slots[slot] == null) continue;
            inputHolder[index++].remove(slots[slot], 1);
            grid.set(slot + 3, slots[slot].toStack());
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, Level level) {
        // CraftingInput trims outer empty rows/columns. Reconstruct the known smithing positions.
        int first = slots[0] == null ? 1 : 0;
        int last = slots[2] == null ? 1 : 2;
        if (input.height() != 1 || input.width() != last - first + 1) return ItemStack.EMPTY;
        var items = new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        for (int i = first; i <= last; i++) {
            var stack = input.getItem(i - first);
            if (stack.isEmpty() || stack.getCount() != 1 || !Objects.equals(slots[i], AEItemKey.of(stack))) {
                return ItemStack.EMPTY;
            }
            items[i] = stack.copy();
        }
        var actual = new SmithingRecipeInput(items[0], items[1], items[2]);
        var holder = liveRecipe(recipeId, level);
        if (holder == null) return ItemStack.EMPTY;
        var recipe = (SmithingRecipe) holder.value();
        if (!optional(recipe) || !recipe.matches(actual, level)) return ItemStack.EMPTY;
        var output = recipe.assemble(actual);
        var expected = getPrimaryOutput();
        return output.getCount() == expected.amount() && expected.what().equals(AEItemKey.of(output))
                ? output : ItemStack.EMPTY;
    }

    @Override public boolean equals(Object other) {
        return other instanceof CatalogSmithingPattern pattern && definition.equals(pattern.definition);
    }
    @Override public int hashCode() { return definition.hashCode(); }

    private record ExactInput(AEItemKey key) implements IInput {
        @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(key, 1)}; }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey input, Level level) { return key.equals(input); }
        @Override public AEKey getRemainingKey(AEKey input) { return null; }
    }
}
