package com.example.ae2loprobe;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.core.definitions.AEItems;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Acceptance fixture for the Loop Matter Condenser: mark a 64k loop cell, place it in the storage
 * slot, seed the network and check that the marked stock is drained and matter balls are returned.
 */
public final class LoopCondenserFixture {
    public static final boolean ENABLED = "loop_condenser".equals(System.getProperty("ae2lo.probe.chain"));
    private static final int WAIT_TICKS = 60;
    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static int waited;
    private static BlockPos pos;
    private static IGrid grid;
    private static long beforePlanks;
    private static long beforeBalls;
    private static Map<String, Object> summary = new LinkedHashMap<>();

    private LoopCondenserFixture() {
    }

    public static void start(Level level, IGrid grid, BlockPos pos) {
        if (started) return;
        started = true;
        LoopCondenserFixture.grid = grid;
        LoopCondenserFixture.pos = pos;
        if (!(level.getBlockEntity(pos) instanceof LoopCondenserBlockEntity condenser)) {
            fail("block entity missing");
            return;
        }
        // A drive right next to the condenser joins the same grid without any cable part.
        var drivePos = pos.east(1);
        level.setBlockAndUpdate(drivePos, appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        // A controller plus a creative energy cell make the little network channel-capable.
        level.setBlockAndUpdate(pos.west(1), appeng.core.definitions.AEBlocks.CONTROLLER.block().defaultBlockState());
        level.setBlockAndUpdate(pos.west(2), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var driveCell = ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance();
        try {
            var cellInv = appeng.api.storage.StorageCells.getCellInventory(driveCell, null);
            if (cellInv != null) {
                cellInv.insert(AEItemKey.of(Items.OAK_PLANKS), 64, appeng.api.config.Actionable.MODULATE,
                        new appeng.me.helpers.BaseActionSource());
            }
        } catch (Throwable ignored) { }
        if (level.getBlockEntity(drivePos) instanceof appeng.blockentity.storage.DriveBlockEntity drive) {
            drive.getInternalInventory().setItemDirect(0, driveCell);
        }
        var testGrid = condenser.mainNode().getGrid() != null ? condenser.mainNode().getGrid() : grid;
        LoopCondenserFixture.grid = testGrid;
        ItemStack cell = new ItemStack(ModItems.LOOP_STORAGE_CELL_64K.get());
        if (cell.getItem() instanceof ICellWorkbenchItem workbench) {
            var config = workbench.getConfigInventory(cell);
            config.setStack(0, GenericStack.fromItemStack(new ItemStack(Items.OAK_PLANKS)));
        } else {
            fail("cell is not workbench-marked capable");
            return;
        }
        condenser.getInternalInventory().setItemDirect(0, cell);
        long inserted = grid.getStorageService().getInventory()
                .insert(AEItemKey.of(Items.OAK_PLANKS), 64, appeng.api.config.Actionable.MODULATE,
                        new appeng.me.helpers.BaseActionSource());
        beforePlanks = count(AEItemKey.of(Items.OAK_PLANKS));
        summary.put("inserted", inserted);
        summary.put("nodeActive", condenser.mainNode().isActive());
        summary.put("gridNodeQueries", com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity.gridNodeQueries);
        summary.put("drivePresent", level.getBlockEntity(pos.east(1)) != null);
        summary.put("driveClass", String.valueOf(level.getBlockEntity(pos.east(1)) == null ? null
                : level.getBlockEntity(pos.east(1)).getClass().getSimpleName()));
        summary.put("sameGrid", condenser.mainNode().getGrid() == grid);
        summary.put("condenserGridPresent", condenser.mainNode().getGrid() != null);
        beforeBalls = count(AEItemKey.of(AEItems.MATTER_BALL.asItem()));
    }

    public static void tick() {
        if (finished) return;
        if (!started) return;
        if (++waited < WAIT_TICKS) return;
        finished = true;
        long planks = count(AEItemKey.of(Items.OAK_PLANKS));
        long balls = count(AEItemKey.of(AEItems.MATTER_BALL.asItem()));
        summary.put("planksBefore", beforePlanks);
        summary.put("planksAfter", planks);
        summary.put("matterBallsBefore", beforeBalls);
        summary.put("matterBallsAfter", balls);
        summary.put("drained", beforePlanks - planks);
        summary.put("produced", balls - beforeBalls);
        passed = planks < beforePlanks && balls > beforeBalls;
    }

    private static long count(AEItemKey key) {
        return grid.getStorageService().getInventory().getAvailableStacks().get(key);
    }

    private static void fail(String reason) {
        finished = true;
        passed = false;
        summary.put("failure", reason);
    }

    public static boolean isFinished() {
        return finished;
    }

    public static boolean passed() {
        return passed;
    }

    public static Map<String, Object> summary() {
        return summary;
    }
}