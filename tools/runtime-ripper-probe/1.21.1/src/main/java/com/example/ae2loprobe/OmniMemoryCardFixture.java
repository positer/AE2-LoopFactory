package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.omni.OmniConnectorBlockEntity;
import com.example.ae2lightoptimizer.omni.OmniMemoryCard;
import com.example.ae2lightoptimizer.omni.OmniContent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Verifies that an AE2 memory card carries an omni connector's link to another connector. */
public final class OmniMemoryCardFixture {
    public static final boolean ENABLED = "omni_memory_card".equals(System.getProperty("ae2lo.probe.chain"));
    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static int waited;
    private static final Map<String, Object> summary = new LinkedHashMap<>();

    private OmniMemoryCardFixture() {
    }

    public static void start(Level level) {
        if (started) return;
        started = true;
        BlockPos first = new BlockPos(12, 100, 12);
        BlockPos second = new BlockPos(16, 100, 12);
        level.setBlockAndUpdate(first, OmniContent.ITEM.get().getBlock().defaultBlockState());
        level.setBlockAndUpdate(second, OmniContent.ITEM.get().getBlock().defaultBlockState());
        if (!(level.getBlockEntity(first) instanceof OmniConnectorBlockEntity source)
                || !(level.getBlockEntity(second) instanceof OmniConnectorBlockEntity target)) {
            finished = true;
            summary.put("failure", "connector missing");
            return;
        }
        String link = "ae2lf-memory-card-test";
        source.selectLink(link);
        ItemStack card = new ItemStack(appeng.core.definitions.AEItems.MEMORY_CARD.asItem());
        OmniMemoryCard.write(card, source.linkId());
        String stored = OmniMemoryCard.read(card);
        target.selectLink(stored);
        summary.put("sourceLink", source.linkId());
        summary.put("cardLink", stored);
        summary.put("targetLink", target.linkId());
        passed = link.equals(target.linkId()) && link.equals(stored);
    }

    public static void tick() {
        if (!started || finished) return;
        if (++waited < 5) return;
        finished = true;
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
