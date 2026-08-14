package net.superkat.wavify.particles;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.util.WavifyColors;
import org.joml.Vector3f;

public class BigSplashParticle extends SingleQuadParticle {
    private final SpriteSet spriteProvider;
    public BigSplashParticle(ClientLevel clientWorld, double x, double y, double z, double velX, double velY, double velZ, SpriteSet spriteProvider) {
        super(clientWorld, x, y, z, velX, velY, velZ, spriteProvider.first());
        this.spriteProvider = spriteProvider;

        this.lifetime = this.random.nextIntBetweenInclusive(7, 30);
        this.quadSize = 0.5f;
        this.gravity = 0.04f;
        this.setSpriteFromAge(this.spriteProvider);

        Vector3f color = WavifyColors.getWaterColorVec(clientWorld, BlockPos.containing(x, y, z));
        this.setColor(color.x, color.y, color.z);
    }

    @Override
    public void tick() {
        super.tick();
        this.yd = this.yd - (double)this.gravity;
        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i, RandomSource random) {
            return new BigSplashParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
        }
    }
}
