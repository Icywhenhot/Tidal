package net.superkat.wavify.particles.debug;

import net.minecraft.client.particle.AbstractDustParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.AbstractDustParticleEffect;

public abstract class DebugAbstractColoredParticle<T extends AbstractDustParticleEffect> extends AbstractDustParticle<T> {
    protected DebugAbstractColoredParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, T parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);
        this.velocityX = 0f;
        this.velocityY = 0f;
        this.velocityZ = 0f;
        this.scale = this.scale * 0.95F * parameters.getScale();
        this.maxAge = 11;
    }

    @Override
    protected float darken(float colorComponent, float multiplier) {
        return colorComponent * multiplier;
    }

    @Override
    public float getSize(float tickDelta) {
        return this.scale;
    }
}
