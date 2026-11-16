package com.example.ae2lightoptimizer.omni;

import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Wireless loop-factory link. A formed structure is an omni connector whose bottom face rests on
 * either a Loop Factory network terminal or a factory pattern provider whose subnet face points up.
 * The link always joins the channel network to the machine's MAIN network; a provider's subnet is
 * deliberately left alone.
 */
public final class OmniFactoryWireless {
    private OmniFactoryWireless() {}
    /** The factory machine this connector rests on, or {@code null} when the structure is not formed. */
    public static FactoryBlockEntity target(Level level, BlockPos connectorPos) {
        if (level == null) return null;
        if (!(level.getBlockEntity(connectorPos.below()) instanceof FactoryBlockEntity factory)) return null;
        if (factory.kind() == FactoryBlock.Kind.TERMINAL) return factory;
        if (factory.kind() == FactoryBlock.Kind.PROVIDER && factory.getFront() == Direction.UP) return factory;
        return null;
    }
}
