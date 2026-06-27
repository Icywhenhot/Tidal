package net.superkat.wavify.particles.debug;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.multiplayer.ClientLevel;

public abstract class DebugAbstractColoredParticle<T extends AbstractDebugParticleEffect> extends TextureSheetParticle {
    protected final SpriteSet spriteProvider;

    protected DebugAbstractColoredParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, T parameters, SpriteSet spriteProvider) {
        super(level, x, y, z, xd, yd, zd);
        this.spriteProvider = spriteProvider;
        this.pickSprite(spriteProvider);
        this.xd = 0f;
        this.yd = 0f;
        this.zd = 0f;
        this.quadSize = this.quadSize * 0.95F * parameters.getScale();
        this.lifetime = 11;
        this.setSpriteFromAge(spriteProvider);
    }

    protected float randomizeColor(float colorComponent, float multiplier) {
        return colorComponent * multiplier;
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
