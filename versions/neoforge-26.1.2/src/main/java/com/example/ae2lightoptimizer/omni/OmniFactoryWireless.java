package com.example.ae2lightoptimizer.omni;

import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Wireless loop-factory structure. The connector's base face may point anywhere:
 * a factory network terminal forms the structure only through its top face, while a factory pattern
 * provider forms it when it looks back at the base face (its subnet side).
 */
public final class OmniFactoryWireless {
    private OmniFactoryWireless() {}
    /** The factory machine under the connector's base face, or {@code null} when the structure is not formed. */
    public static FactoryBlockEntity target(Level level, BlockPos connectorPos, Direction base) {
        if (level == null || base == null) return null;
        if (!(level.getBlockEntity(connectorPos.relative(base)) instanceof FactoryBlockEntity factory)) return null;
        if (factory.kind() == FactoryBlock.Kind.TERMINAL) return base == Direction.DOWN ? factory : null;
        if (factory.kind() == FactoryBlock.Kind.PROVIDER) return factory.getFront() == base.getOpposite() ? factory : null;
        return null;
    }
}
