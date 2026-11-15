package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import java.nio.file.Files;
import java.util.*;

/** Uses physical ME storage and native provider block/part inventories on real grids. */
final class RipperImportFixture {
    static volatile boolean prepared, ready, firstVerified, cancelSeeded, closed, cancelled, finalVerified;
    static volatile long cancelledCount;
    static final long FIRST_IMPORTED = 309, CANCEL_SUPPLY = 4096;
    private static CraftingRipperBlockEntity host, other;
    private static PatternProviderLogicHost block, part, isolated;
    private static appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler;
    private static ItemStack craft, smith, stone, processing, invalid, stale, cancelPattern;
    private static List<ItemStack> existing;
    private static final Map<AEItemKey, Long> expected = new HashMap<>();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static long closedCount, closedTick, lastTotal, maxPerTick;
    static volatile String failure;

    static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    static ItemStack named(ItemStack stack, String name) { stack = stack.copy(); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack; }
    static long total() { return host.getLogic().getPatternInv().nonEmptyStacks().stream().mapToLong(ItemStack::getCount).sum(); }
    static Map<AEItemKey, Long> contents() {
        var result = new HashMap<AEItemKey, Long>();
        for (var stack : host.getLogic().getPatternInv().nonEmptyStacks()) result.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum);
        return result;
    }
    static void expect(ItemStack pattern, long count) { expected.merge(AEItemKey.of(pattern), count, Long::sum); }
    static void insert(ItemStack stack, long count) {
        check(host.getMainNode().getGrid().getStorageService().getInventory().insert(AEItemKey.of(stack), count,
                Actionable.MODULATE, new appeng.me.helpers.MachineSource(host)) == count, "Fixture storage insertion failed");
    }
    static long stored(ItemStack stack) { return host.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(stack)); }

    static void prepare(ServerPlayer player) throws Exception {
        host = DynamicPatternFixture.host;
        var level = player.serverLevel();
        existing = host.getLogic().getPatternInv().nonEmptyStacks().stream().map(ItemStack::copy).toList();
        check(existing.size() == 442, "Dynamic UI baseline changed");
        expected.putAll(contents());
        craft = DynamicPatternFixture.encoded.copy();
        var smithInput = new SmithingRecipeInput(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE.getDefaultInstance(),
                Items.DIAMOND_PICKAXE.getDefaultInstance(), Items.NETHERITE_INGOT.getDefaultInstance());
        var smithRecipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithInput, level).orElseThrow();
        smith = PatternDetailsHelper.encodeSmithingTablePattern(smithRecipe, AEItemKey.of(smithInput.template()),
                AEItemKey.of(smithInput.base()), AEItemKey.of(smithInput.addition()), AEItemKey.of(smithRecipe.value().assemble(smithInput, level.registryAccess())), false);
        var stoneInput = new SingleRecipeInput(Items.STONE.getDefaultInstance());
        var stoneRecipe = level.getRecipeManager().getRecipeFor(RecipeType.STONECUTTING, stoneInput, level).orElseThrow();
        stone = PatternDetailsHelper.encodeStonecuttingPattern(stoneRecipe, AEItemKey.of(Items.STONE),
                AEItemKey.of(stoneRecipe.value().assemble(stoneInput, level.registryAccess())), false);
        processing = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        invalid = named(appeng.core.definitions.AEItems.CRAFTING_PATTERN.stack(), "invalid-import-pattern");
        stale = named(craft, "stale-import-pattern");
        var data = stale.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        stale.set(AEComponents.ENCODED_CRAFTING_PATTERN, new EncodedCraftingPattern(data.inputs(), data.result(),
                ResourceLocation.parse("ae2lo_runtime_probe:missing_import_recipe"), false, false));
        insert(craft, 300); insert(smith, 3); insert(stone, 2);
        insert(processing, 7); insert(invalid, 5); insert(stale, 4);
        expect(craft, 300); expect(smith, 3); expect(stone, 2);

        level.removeBlock(host.getBlockPos().above(), false); // The PAT fixture has finished; free its channel.
        var p = host.getBlockPos().north();
        level.setBlockAndUpdate(p, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        block = (PatternProviderLogicHost) level.getBlockEntity(p);
        var blockPattern = named(craft, "installed-hidden-block");
        block.getLogic().getPatternInv().setItemDirect(0, blockPattern);
        block.getLogic().getPatternInv().setItemDirect(1, processing.copy());
        block.getConfigManager().putSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.NO);
        expect(blockPattern, 1);
        p = host.getBlockPos().north(2);
        PartHelper.setPart(level, p, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        part = (PatternProviderLogicHost) PartHelper.setPart(level, p, Direction.UP, player, AEParts.PATTERN_PROVIDER.get());
        var partPattern = named(stone, "installed-part");
        part.getLogic().getPatternInv().setItemDirect(0, partPattern);
        part.getLogic().getPatternInv().setItemDirect(1, stale.copy());
        expect(partPattern, 1);
        p = host.getBlockPos().north(3);
        level.setBlockAndUpdate(p, ModBlocks.CRAFTING_RIPPER.get().defaultBlockState());
        other = (CraftingRipperBlockEntity) level.getBlockEntity(p);
        var sparsePattern = named(smith, "installed-slot-40003");
        other.getLogic().getPatternInv().setItemDirect(0, invalid.copy());
        other.getLogic().getPatternInv().setItemDirect(40003, sparsePattern);
        expect(sparsePattern, 1);
        p = host.getBlockPos().north(4);
        level.setBlockAndUpdate(p, AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
        assembler = (appeng.blockentity.crafting.MolecularAssemblerBlockEntity) level.getBlockEntity(p);
        var assemblerPattern = named(craft, "installed-manual-assembler");
        assembler.getInternalInventory().setItemDirect(10, assemblerPattern);
        assembler.getInternalInventory().setItemDirect(0, Items.DIAMOND.getDefaultInstance());
        expect(assemblerPattern, 1);
        p = new BlockPos(-8, 100, -3);
        level.setBlockAndUpdate(p, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        isolated = (PatternProviderLogicHost) level.getBlockEntity(p);
        isolated.getLogic().getPatternInv().setItemDirect(0, named(craft, "off-network"));
        report.put("initialPatterns", 442);
        report.put("expectedMoved", FIRST_IMPORTED);
        report.put("sources", List.of("ME storage", "hidden provider block", "provider part", "ripper slot 40003", "manual molecular assembler"));
        lastTotal = total();
        prepared = true;
    }
    static void awaitReady() {
        var grid = host.getMainNode().getGrid();
        ready = grid != null && host.getMainNode().isActive() && block.getGrid() == grid && part.getGrid() == grid
                && other.getGrid() == grid && assembler.getMainNode().getGrid() == grid && isolated.getGrid() != grid;
    }
    static void verifyFirst(ServerPlayer player) {
        var menu = (CraftingRipperMenu) player.containerMenu;
        check(!menu.importingPatterns && menu.importedPatterns == FIRST_IMPORTED, "Import count or completion differs");
        check(contents().equals(expected), "Exact imported components/counts differ");
        for (int i = 0; i < existing.size(); i++) check(ItemStack.matches(existing.get(i), host.getLogic().getPatternInv().getStackInSlot(i)), "Existing slot changed: " + i);
        check(stored(craft) == 0 && stored(smith) == 0 && stored(stone) == 0, "Supported ME patterns remain");
        check(stored(processing) == 7 && stored(invalid) == 5 && stored(stale) == 4, "Unsupported ME patterns moved");
        check(block.getLogic().getPatternInv().getStackInSlot(0).isEmpty() && block.getLogic().getPatternInv().getStackInSlot(1).getCount() == 1, "Block source selection differs");
        check(part.getLogic().getPatternInv().getStackInSlot(0).isEmpty() && !part.getLogic().getPatternInv().getStackInSlot(1).isEmpty(), "Part source selection differs");
        check(other.getLogic().getPatternInv().getStackInSlot(40003).isEmpty() && !other.getLogic().getPatternInv().getStackInSlot(0).isEmpty(), "Sparse source selection differs");
        check(assembler.getInternalInventory().getStackInSlot(10).isEmpty()
                && assembler.getInternalInventory().getStackInSlot(0).is(Items.DIAMOND)
                && assembler.getInternalInventory().getStackInSlot(0).getCount() == 1, "Assembler pattern/material separation failed");
        report.put("assemblerPhysicalPatternOnly", true);
        check(!isolated.getLogic().getPatternInv().getStackInSlot(0).isEmpty(), "Independent network was touched");
        check(host.getLogic().getPatternInv().size() == 765, "Imported inventory lost its empty row");
        report.put("exactComponentsConserved", true); report.put("allThreeRecipeTypes", true);
        report.put("invalidProcessingStaleUnchanged", true); report.put("independentNetworkUntouched", true);
        report.put("existingSlotsUnchanged", true); report.put("sparseSourceTransferred", true);
        firstVerified = true;
    }
    static void seedCancellation(ServerPlayer player) {
        check(total() == 751 && !((CraftingRipperMenu) player.containerMenu).importingPatterns, "Repeat click changed count");
        report.put("repeatClickNoDuplication", true);
        cancelPattern = named(craft, "cancel-resume-pattern"); insert(cancelPattern, CANCEL_SUPPLY); cancelSeeded = true;
    }
    static void closeDuringImport(ServerPlayer player) {
        var menu = (CraftingRipperMenu) player.containerMenu;
        check(menu.importingPatterns && menu.importedPatterns > 0 && menu.importedPatterns < CANCEL_SUPPLY, "Cancellation did not interrupt a partial import");
        player.closeContainer();
        closedCount = total(); cancelledCount = closedCount - 751; closedTick = player.serverLevel().getGameTime(); closed = true;
    }
    static void verifyCancellation(ServerPlayer player) {
        if (cancelled) return;
        if (player.serverLevel().getGameTime() - closedTick < 30) return;
        check(total() == closedCount && cancelledCount + stored(cancelPattern) == CANCEL_SUPPLY, "Closed menu kept moving or lost resources");
        report.put("closeCancelsUncommittedWork", true); report.put("cancelledAfter", cancelledCount);
        host.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(host)); cancelled = true;
    }
    static void verifyFinal(ServerPlayer player) {
        check(total() == 751 + CANCEL_SUPPLY && stored(cancelPattern) == 0, "Resume lost/duplicated patterns");
        report.put("resumeConserved", true); report.put("finalPatterns", total()); finalVerified = true;
    }
    // The ripper draws no idle power, so it no longer drains the finite test cell into an offline state.
    // Draining the network itself is the honest way to make the menu report an unavailable network.
    static double savedEnergy;
    static void disconnect() {
        var grid = host.getMainNode().getGrid();
        if (grid == null) return;
        var energy = grid.getEnergyService();
        savedEnergy = energy.extractAEPower(1e9, Actionable.SIMULATE, PowerMultiplier.ONE);
        energy.extractAEPower(savedEnergy, Actionable.MODULATE, PowerMultiplier.ONE);
    }
    static void reconnect() {
        var grid = host.getMainNode().getGrid();
        if (grid != null) grid.getEnergyService().injectPower(savedEnergy, Actionable.MODULATE);
    }
    static void observe() {
        if (!prepared || finalVerified || failure != null) return;
        long current = total(), delta = current - lastTotal;
        if (delta < 0 || delta > com.example.ae2lightoptimizer.block.RipperPatternImporter.WORK_PER_TICK)
            failure = "Import exceeded per-tick transfer budget: " + delta;
        maxPerTick = Math.max(maxPerTick, delta); lastTotal = current;
    }
    static void finish(Map<String, Object> ui) throws Exception {
        check(failure == null, failure);
        report.put("maxTransferredPerTick", maxPerTick);

        report.put("ui", ui); report.put("passed", true);
        Files.writeString(DynamicPatternFixture.output.resolve("pattern-import-report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
