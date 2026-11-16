package com.example.ae2lightoptimizer.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlock;
import com.example.ae2lightoptimizer.omni.OmniContent;

/** White ring particle orbiting the connector centre inside its own block while a factory structure is formed. */
public final class OmniOrbitParticle extends SimpleAnimatedParticle {
    private final BlockPos owner;
    public static volatile long created, stoppedUnformed, stoppedAtBoundary;
    public OmniOrbitParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0);
        owner = BlockPos.containing(x, y, z);
        xd = vx; yd = vy; zd = vz; gravity = 0; friction = 1; hasPhysics = false;
        lifetime = 30; quadSize = 0.03f; setColor(0xFFFFFF); setFadeColor(0xFFFFFF);
        setSpriteFromAge(sprites); created++;
    }
    @Override public void tick() {
        var state = level.getBlockState(owner);
        if (!state.is(OmniContent.BLOCK.get()) || !state.getValue(OmniConnectorBlock.WIRELESS)) {
            stoppedUnformed++; remove(); return;
        }
        // Hold the particle on its circle instead of letting it drift away from the block.
        double cx = owner.getX() + 0.5, cz = owner.getZ() + 0.5;
        double dx = x - cx, dz = z - cz;
        double radius = Math.sqrt(dx * dx + dz * dz);
        if (radius < 0.001) { remove(); return; }
        double speed = 0.02;
        xd = -dz / radius * speed; yd = 0; zd = dx / radius * speed;
        super.tick();
        if (x < owner.getX() + 0.04 || x > owner.getX() + 0.96 || y < owner.getY() + 0.04 || y > owner.getY() + 0.96
                || z < owner.getZ() + 0.04 || z > owner.getZ() + 0.96) {
            stoppedAtBoundary++; remove(); return;
        }
        setAlpha(0.85f * Math.min(1, Math.max(0, (lifetime - age) / (lifetime * 0.25f))));
    }
    public static void register(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(OmniContent.ORBIT.get(), sprites -> (type, level, x, y, z, vx, vy, vz) ->
                new OmniOrbitParticle(level, x, y, z, vx, vy, vz, sprites));
    }
}
