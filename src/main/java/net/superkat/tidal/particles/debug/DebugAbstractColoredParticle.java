package net.superkat.tidal.particles.debug;

import net.minecraft.client.particle.DustParticleBase;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ScalableParticleOptionsBase;

public abstract class DebugAbstractColoredParticle<T extends ScalableParticleOptionsBase> extends DustParticleBase<T> {
    protected DebugAbstractColoredParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, T parameters, SpriteSet spriteProvider) {
        super(level, x, y, z, xd, yd, zd, parameters, spriteProvider);
        this.xd = 0f;
        this.yd = 0f;
        this.zd = 0f;
        this.quadSize = this.quadSize * 0.95F * parameters.getScale();
        this.lifetime = 11;
    }

    @Override
    protected float randomizeColor(float colorComponent, float multiplier) {
        return colorComponent * multiplier;
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize;
    }
}
