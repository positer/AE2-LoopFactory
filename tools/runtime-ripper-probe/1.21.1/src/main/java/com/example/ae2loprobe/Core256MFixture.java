package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.item.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;

/** Actual automatic-catalog order; external materials exclude every crafting-table intermediate. */
final class Core256MFixture {
    static final boolean ENABLED = "core256m".equals(System.getProperty("ae2lo.probe.chain", "template"));
    static final long ORDER = 3_000L;
    static final long RAW = 3_000_000_000L;
    static final long ESTIMATED_BYTES = 769_888_153L;
    private static final String[] TIERS = { "1k", "4k", "16k", "64k", "256k", "1m", "4m", "16m", "64m", "256m" };
    private static Map<String, Long> applications = Map.of();

    private Core256MFixture() { }

    static AEItemKey target() { return AEItemKey.of(ModItems.LOOP_STORAGE_CORE_256M.get()); }

    static Map<String, AEItemKey> keys() {
        var result = new LinkedHashMap<String, AEItemKey>();
        result.put("iron_ingot", AEItemKey.of(Items.IRON_INGOT));
        result.put("certus_quartz", AEItemKey.of(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()));
        result.put("fluix_crystal", AEItemKey.of(AEItems.FLUIX_CRYSTAL.asItem()));
        result.put("gold_ingot", AEItemKey.of(Items.GOLD_INGOT));
        result.put("netherite_scrap", AEItemKey.of(Items.NETHERITE_SCRAP));
        result.put("singularity", AEItemKey.of(AEItems.SINGULARITY.asItem()));
        result.put("loop_crystal_powder", AEItemKey.of(ModItems.LOOP_CRYSTAL_POWDER.get()));
        result.put("loop_crystal", AEItemKey.of(ModItems.LOOP_CRYSTAL.get()));
        result.put("loop_crystal_fragment", AEItemKey.of(ModItems.LOOP_CRYSTAL_FRAGMENT.get()));
        result.put("netherite_ingot", AEItemKey.of(Items.NETHERITE_INGOT));
        var cores = List.of(ModItems.LOOP_STORAGE_CORE_1K, ModItems.LOOP_STORAGE_CORE_4K,
                ModItems.LOOP_STORAGE_CORE_16K, ModItems.LOOP_STORAGE_CORE_64K, ModItems.LOOP_STORAGE_CORE_256K,
                ModItems.LOOP_STORAGE_CORE_1M, ModItems.LOOP_STORAGE_CORE_4M, ModItems.LOOP_STORAGE_CORE_16M,
                ModItems.LOOP_STORAGE_CORE_64M, ModItems.LOOP_STORAGE_CORE_256M);
        for (int i = 0; i < TIERS.length; i++) result.put("core_" + TIERS[i], AEItemKey.of(cores.get(i).get()));
        return result;
    }

    static Map<String, Long> consumption() {
        return Map.of("iron_ingot", 352_836_000L, "certus_quartz", 167_425_000L,
                "fluix_crystal", 19_804_000L, "gold_ingot", 5_808_000L, "netherite_scrap", 5_808_000L,
                "singularity", 363_000L, "loop_crystal_powder", 29_160_000L);
    }

    static Map<String, Object> prepare(IGrid grid, CraftingRipperBlockEntity ripper) {
        ripper.getUpgrades().setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
        check(ripper.isLoopCardInstalled(), "Core fixture Loop Card did not install");
        var keys = keys();
        var inventory = grid.getStorageService().getInventory();
        var source = new MachineSource(grid::getPivot);
        for (String raw : consumption().keySet()) {
            long inserted = inventory.insert(keys.get(raw), RAW, Actionable.MODULATE, source);
            check(inserted == RAW, "Core fixture long raw insertion truncated: " + raw);
        }
        check(inventory.insert(keys.get("loop_crystal"), 1, Actionable.MODULATE, source) == 1,
                "Core fixture could not insert its one crystal seed");
        var catalog = ripper.getLogic().getAvailablePatterns();
        for (String output : expectedApplications().keySet()) {
            check(catalog.stream().anyMatch(pattern -> pattern.getPrimaryOutput().what().equals(keys.get(output))),
                    "Automatic catalog omits required core-chain recipe: " + output);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", "loop_card_catalog");
        result.put("estimatedPlanBytes", ESTIMATED_BYTES);
        result.put("rawStockPerKey", RAW);
        result.put("externalMaterials", consumption().keySet());
        result.put("externalBoundary", "Iron/gold ingots, netherite scrap, certus/fluix crystals, singularities and loop powder are supplied. "
                + "Powder requires the inscriber or optional machine processing; singularities require condensation. "
                + "All ten cores, fragments, crystal growth and netherite ingots are crafted within the one submitted job.");
        result.put("expectedConsumption", consumption());
        result.put("expectedApplications", expectedApplications());
        result.put("catalogPatterns", catalog.size());
        return result;
    }

    static void verifyInitial(Map<String, Long> stock) {
        for (String name : keys().keySet()) {
            long expected = consumption().containsKey(name) ? RAW : name.equals("loop_crystal") ? 1 : 0;
            check(stock.getOrDefault(name, 0L) == expected, "Core fixture must not start with intermediates: " + name);
        }
    }

    static void verifyPlan(ICraftingPlan plan) {
        var byKey = new LinkedHashMap<AEItemKey, String>();
        keys().forEach((name, key) -> byKey.put(key, name));
        var observed = new LinkedHashMap<String, Long>();
        for (var entry : plan.patternTimes().entrySet()) {
            String name = byKey.get(entry.getKey().getPrimaryOutput().what());
            check(name != null, "Unexpected selected automatic core-chain output: " + entry.getKey().getPrimaryOutput());
            observed.merge(name, entry.getValue(), Math::addExact);
        }
        applications = Map.copyOf(observed);
        check(applications.equals(expectedApplications()), "Automatic core-chain applications differ: " + applications);
        var required = new LinkedHashMap<String, Long>(consumption());
        required.put("loop_crystal", 1L);
        var keys = keys();
        for (var key : keys.entrySet()) check(plan.usedItems().get(key.getValue()) == required.getOrDefault(key.getKey(), 0L),
                "Core plan reserves unexpected initial input: " + key.getKey());
    }

    static Map<String, Long> expectedApplications() {
        var result = new LinkedHashMap<String, Long>();
        long count = ORDER;
        for (int i = TIERS.length - 1; i >= 0; i--) {
            result.put("core_" + TIERS[i], count);
            count = Math.multiplyExact(count, 3L);
        }
        result.put("loop_crystal", 19_804_000L);
        result.put("loop_crystal_fragment", 78_853_000L);
        result.put("netherite_ingot", 1_452_000L);
        return result;
    }

    static Map<String, Long> applications() { return applications; }

    static void addStock(KeyCounter counter, Map<String, Long> stock) {
        keys().forEach((name, key) -> stock.put(name, counter.get(key)));
    }

    static void verifyFinal(Map<String, Long> before, Map<String, Long> after) {
        var consumption = consumption();
        for (String name : keys().keySet()) {
            long expected = consumption.containsKey(name) ? before.get(name) - consumption.get(name)
                    : name.equals("core_256m") ? ORDER : name.equals("loop_crystal") ? 1 : 0;
            check(after.getOrDefault(name, 0L) == expected, "Core final raw/intermediate/output balance differs: " + name);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
