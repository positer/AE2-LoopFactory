package com.example.ae2loprobe;

import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEBlocks;
import com.example.ae2lightoptimizer.part.LoopTickerPart;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Acceptance fixture for the loop ticker part: it mounts the part on a cable bus and checks that the
 * block the part points at gets accelerated compared to an untouched furnace.
 */
public final class LoopTickerPartFixture {
    public static final boolean ENABLED = "loop_ticker_part".equals(System.getProperty("ae2lo.probe.chain"));
    private static final int WAIT_TICKS = 400;
    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static int waited;
    private static Level level;
    private static BlockPos controlFurnace;
    private static BlockPos fastFurnace;
    private static final Map<String, Object> summary = new LinkedHashMap<>();

    private LoopTickerPartFixture() {
    }

    public static void start(Level value, BlockPos base) {
        if (started) return;
        started = true;
        level = value;
        // Control furnace and an identical one that the part will accelerate.
        controlFurnace = base;
        fastFurnace = base.east(4);
        placeFurnace(controlFurnace);
        placeFurnace(fastFurnace);
        BlockPos cablePos = fastFurnace.below();
        level.setBlockAndUpdate(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        // A small powered network next to the cable so the part's node can become active.
        level.setBlockAndUpdate(cablePos.west(1), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cablePos.west(2), AEBlocks.CONTROLLER.block().defaultBlockState());
        if (!(level.getBlockEntity(cablePos) instanceof IPartHost host)) {
            fail("cable bus did not create a part host");
            return;
        }
        var part = host.addPart(com.example.ae2lightoptimizer.item.ModItems.LOOP_TICKER_PART.get(),
                Direction.UP, null);
        if (!(part instanceof LoopTickerPart ticker)) {
            fail("part was not installed");
            return;
        }
        ticker.setRate(4);
        summary.put("partInstalled", true);
        summary.put("partClass", part.getClass().getSimpleName());
        summary.put("partSide", String.valueOf(part.getSide()));
    }

    private static void placeFurnace(BlockPos pos) {
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
        int plain = count(controlFurnace);
        int fast = count(fastFurnace);
        summary.put("controlOutput", plain);
        summary.put("acceleratedOutput", fast);
        passed = fast > plain;
    }

    private static int count(BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.getItem(2).getCount();
        }
        return -1;
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