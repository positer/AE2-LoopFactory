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
        // Mount the part on a cable bus that already belongs to the probe's powered network.
        IPartHost host = null;
        BlockPos cablePos = null;
        Direction mountSide = null;
        outer:
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    BlockPos probePos = base.offset(dx, dy, dz);
                    if (level.getBlockEntity(probePos) instanceof IPartHost candidate) {
                        for (Direction side : Direction.values()) {
                            if (candidate.getPart(side) == null
                                    && level.getBlockState(probePos.relative(side)).isAir()) {
                                host = candidate;
                                cablePos = probePos;
                                mountSide = side;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        if (host == null) {
            fail("no free cable bus in the probe network");
            return;
        }
        summary.put("mountPos", cablePos.toShortString());
        summary.put("mountSide", String.valueOf(mountSide));
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