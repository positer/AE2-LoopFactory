package com.example.ae2lightoptimizer.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlock;
import com.example.ae2lightoptimizer.omni.OmniContent;
import com.example.ae2lightoptimizer.omni.OmniOrbitMath;

/**
 * White ring particle orbiting the connector centre inside its own block. The orbit lies in the plane
 * perpendicular to the base face, and its lifetime is one full revolution so a single particle closes
 * the circle instead of drawing a short arc.
 */
public final class OmniOrbitParticle extends SimpleAnimatedParticle {
    private static final double RADIUS = 0.33;
    private static final double SPEED = 0.02;
    private static final int LIFETIME = (int) Math.round(2 * Math.PI * RADIUS / SPEED);
    private final BlockPos owner;
    public static volatile long created, stoppedUnformed, stoppedAtBoundary;
    public OmniOrbitParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0);
        owner = BlockPos.containing(x, y, z);
        xd = vx; yd = vy; zd = vz; gravity = 0; friction = 1; hasPhysics = false;
        lifetime = LIFETIME; quadSize = 0.03f; setColor(0xFFFFFF); setFadeColor(0xFFFFFF);
        setSpriteFromAge(sprites); created++;
    }
    @Override public void tick() {
        var state = level.getBlockState(owner);
        if (!state.is(OmniContent.BLOCK.get()) || !state.getValue(OmniConnectorBlock.WIRELESS)) {
            stoppedUnformed++; remove(); return;
        }
        var axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).getOpposite();
        double cx = owner.getX() + 0.5, cy = owner.getY() + 0.5, cz = owner.getZ() + 0.5;
        var tangent = OmniOrbitMath.tangent(axis, x - cx, y - cy, z - cz);
        if (tangent.x() == 0 && tangent.y() == 0 && tangent.z() == 0) { remove(); return; }
        xd = tangent.x() * SPEED; yd = tangent.y() * SPEED; zd = tangent.z() * SPEED;
        super.tick();
        if (x < owner.getX() + 0.02 || x > owner.getX() + 0.98 || y < owner.getY() + 0.02 || y > owner.getY() + 0.98
                || z < owner.getZ() + 0.02 || z > owner.getZ() + 0.98) {
            stoppedAtBoundary++; remove(); return;
        }
        setAlpha(0.85f * Math.min(1, Math.max(0, (lifetime - age) / (lifetime * 0.15f))));
    }
    public static void register(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(OmniContent.ORBIT.get(), sprites -> (type, level, x, y, z, vx, vy, vz) ->
                new OmniOrbitParticle(level, x, y, z, vx, vy, vz, sprites));
    }
}
