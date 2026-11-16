package com.example.ae2lightoptimizer.omni;

import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Wireless loop-factory structure. The connector's base face may point anywhere; when it rests on a
 * Loop Factory network terminal, or on a factory pattern provider whose subnet face looks back at the
 * connector, the factory network is bridged.
 */
public final class OmniFactoryWireless {
    private OmniFactoryWireless() {}
    /** The factory machine under the connector's base face, or {@code null} when the structure is not formed. */
    public static FactoryBlockEntity target(Level level, BlockPos connectorPos, Direction base) {
        if (level == null || base == null) return null;
        if (!(level.getBlockEntity(connectorPos.relative(base)) instanceof FactoryBlockEntity factory)) return null;
        if (factory.kind() == FactoryBlock.Kind.TERMINAL) return factory;
        if (factory.kind() == FactoryBlock.Kind.PROVIDER && factory.getFront() == base.getOpposite()) return factory;
        return null;
    }
}
