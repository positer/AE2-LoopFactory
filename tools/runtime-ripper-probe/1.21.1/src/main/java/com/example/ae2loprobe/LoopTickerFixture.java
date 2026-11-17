package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.block.LoopTickerBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Acceptance fixture for the loop ticker: two furnaces with identical inputs, one accelerated, and
 * their smelted output compared after a fixed number of ticks.
 */
public final class LoopTickerFixture {
    public static final boolean ENABLED = "loop_ticker".equals(System.getProperty("ae2lo.probe.chain"));
    private static final int WAIT_TICKS = 400;
    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static int waited;
    private static BlockPos control;
    private static BlockPos accelerated;
    private static final Map<String, Object> summary = new LinkedHashMap<>();

    private LoopTickerFixture() {
    }

    public static void start(Level level, BlockPos origin) {
        if (started) return;
        started = true;
        control = origin;
        accelerated = origin.east(3);
        placeFurnace(level, control);
        placeFurnace(level, accelerated);
        BlockPos tickerPos = accelerated.above(1);
        level.setBlockAndUpdate(tickerPos,
                ModBlocks.LOOP_TICKER.get().defaultBlockState()
                        .setValue(com.example.ae2lightoptimizer.block.LoopTickerBlock.FACING, Direction.DOWN));
        if (level.getBlockEntity(tickerPos) instanceof LoopTickerBlockEntity ticker) {
            ticker.setRate(4);
        }
    }

    private static void placeFurnace(Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
        }
    }

    public static void tick() {
        if (!started || finished) return;
        if (++waited < WAIT_TICKS) return;
        finished = true;
        int plain = count(control);
        int fast = count(accelerated);
        summary.put("controlOutput", plain);
        summary.put("acceleratedOutput", fast);
        passed = fast > plain;
    }

    private static int count(BlockPos pos) {
        if (LoopTickerFixture.level == null) return -1;
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.getItem(2).getCount();
        }
        return -1;
    }

    private static Level level;

    public static void bind(Level value) {
        level = value;
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
