package com.example.ae2lightoptimizer.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import com.example.ae2lightoptimizer.omni.OmniConnectorBlock;
import com.example.ae2lightoptimizer.omni.OmniContent;

/** Slow outward white specks. Both sprite and trajectory stay inside their owning block. */
public final class OmniPulseParticle extends SimpleAnimatedParticle {
    private final BlockPos owner;
    public static volatile long created, stoppedOffline, stoppedAtBoundary;
    public OmniPulseParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0);
        owner = BlockPos.containing(x, y, z);
        xd = vx; yd = vy; zd = vz; gravity = 0; friction = 1; hasPhysics = false;
        lifetime = 48; quadSize = 0.035f; setColor(0xFFFFFF); setFadeColor(0xFFFFFF);
        setSpriteFromAge(sprites); created++;
    }
    @Override public void tick() {
        var state = level.getBlockState(owner);
        if (!state.is(OmniContent.BLOCK.get()) || !state.getValue(OmniConnectorBlock.ONLINE)) {
            stoppedOffline++; remove(); return;
        }
        super.tick();
        if (x < owner.getX() + 0.06 || x > owner.getX() + 0.94 || y < owner.getY() + 0.06 || y > owner.getY() + 0.94
                || z < owner.getZ() + 0.06 || z > owner.getZ() + 0.94) {
            stoppedAtBoundary++; remove();
        }
        setAlpha(0.7f * Math.min(1, Math.max(0, (lifetime - age) / (lifetime * 0.3f))));
    }
    public static void register(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(OmniContent.PULSE.get(), sprites -> (type, level, x, y, z, vx, vy, vz, random) ->
                new OmniPulseParticle(level, x, y, z, vx, vy, vz, sprites));
    }
}
