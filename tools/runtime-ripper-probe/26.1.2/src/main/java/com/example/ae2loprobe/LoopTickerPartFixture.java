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
    private static LoopTickerPart lastPart;
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
        // Attach a cable bus to the probe's own powered network (next to the drive), then mount the part.
        BlockPos cablePos = new BlockPos(-2, 100, 0);
        level.setBlockAndUpdate(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        if (!(level.getBlockEntity(cablePos) instanceof IPartHost host)) {
            fail("cable bus did not create a part host");
            return;
        }
        host.addPart(appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT),
                Direction.EAST, null);
        Direction mountSide = Direction.UP;
        BlockPos targetPos = cablePos.relative(mountSide);
        level.setBlockAndUpdate(targetPos, Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(targetPos) instanceof AbstractFurnaceBlockEntity fed) {
            fed.setItem(0, new ItemStack(Items.RAW_IRON, 16));
            fed.setItem(1, new ItemStack(Items.COAL, 8));
        }
        fastFurnace = targetPos;
        var part = host.addPart(com.example.ae2lightoptimizer.item.ModItems.LOOP_TICKER_PART.get(),
                mountSide, null);
        if (!(part instanceof LoopTickerPart ticker)) {
            fail("part was not installed");
            return;
        }
        ticker.setRate(4);
        summary.put("partInstalled", true);
        summary.put("partClass", part.getClass().getSimpleName());
        summary.put("partSide", String.valueOf(part.getSide()));
        lastPart = ticker;

    }

    private static void placeFurnace(BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
        }
    }

    private static int phase;
    private static int isolatedOutput = -1;

    public static void tick() {
        if (!started || finished) return;
        if (++waited < WAIT_TICKS) return;
        if (phase == 0) {
            int plain = count(controlFurnace);
            int fast = count(fastFurnace);
            if (lastPart != null) {
                summary.put("tickCount", lastPart.tickCount);
                summary.put("nodeActiveAtEnd", lastPart.isActive());
            }
            summary.put("controlOutput", plain);
            summary.put("acceleratedOutput", fast);
            // Phase 1: the same part on an isolated, unpowered cable must not accelerate.
            BlockPos isolated = new BlockPos(20, 100, 20);
            level.setBlockAndUpdate(isolated, AEBlocks.CABLE_BUS.block().defaultBlockState());
            if (level.getBlockEntity(isolated) instanceof IPartHost isolatedHost) {
                BlockPos furnacePos = isolated.above();
                level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
                if (level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity fed) {
                    fed.setItem(0, new ItemStack(Items.RAW_IRON, 16));
                    fed.setItem(1, new ItemStack(Items.COAL, 8));
                }
                isolatedHost.addPart(com.example.ae2lightoptimizer.item.ModItems.LOOP_TICKER_PART.get(),
                        Direction.UP, null);
                if (isolatedHost.getPart(Direction.UP) instanceof LoopTickerPart unpowered) {
                    unpowered.setRate(4);
                }
                isolatedFurnace = furnacePos;
                phase = 1;
                waited = 0;
            } else {
                passed = false;
                finished = true;
                summary.put("failure", "isolated cable bus missing");
            }
            return;
        }
        finished = true;
        isolatedOutput = count(isolatedFurnace);
        summary.put("isolatedOutput", isolatedOutput);
        int plain = count(controlFurnace);
        summary.put("controlOutputAfterSecondPhase", plain);
        passed = ((int) summary.get("acceleratedOutput")) > ((int) summary.get("controlOutput"))
                && isolatedOutput <= plain;
    }

    private static BlockPos isolatedFurnace;

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