package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Verifies that the loop condenser still condenses while it is not attached to any network: items
 * put into its input inventory must raise its internal power balance exactly like the native block.
 */
public final class LoopCondenserOfflineFixture {
    public static final boolean ENABLED = "loop_condenser_offline".equals(System.getProperty("ae2lo.probe.chain"));
    private static final int WAIT_TICKS = 60;
    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static int waited;
    private static BlockPos pos;
    private static Level level;
    private static double powerBefore;
    private static final Map<String, Object> summary = new LinkedHashMap<>();

    private LoopCondenserOfflineFixture() {
    }

    public static void start(Level value, BlockPos at) {
        if (started) return;
        started = true;
        level = value;
        pos = at;
        level.setBlockAndUpdate(pos, ModBlocks.LOOP_CONDENSER.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof LoopCondenserBlockEntity condenser)) {
            finished = true;
            summary.put("failure", "block entity missing");
            return;
        }
        powerBefore = condenser.getStoredPower();
        var external = condenser.getExternalInv();
        external.insertItem(0, new ItemStack(Items.OAK_PLANKS, 32), false);
        summary.put("inserted", external.getStackInSlot(0).getCount());
        summary.put("powerBefore", powerBefore);
    }

    public static void tick() {
        if (!started || finished) return;
        if (++waited < WAIT_TICKS) return;
        finished = true;
        if (level.getBlockEntity(pos) instanceof LoopCondenserBlockEntity condenser) {
            double power = condenser.getStoredPower();
            summary.put("powerAfter", power);
            passed = power > powerBefore;
        } else {
            summary.put("failure", "block entity disappeared");
        }
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
