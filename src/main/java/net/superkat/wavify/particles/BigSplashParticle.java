package net.superkat.wavify.particles;

import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.superkat.wavify.util.WavifyColors;

public class BigSplashParticle extends TextureSheetParticle {
    private final SpriteSet spriteProvider;
    public BigSplashParticle(ClientLevel clientWorld, double x, double y, double z, double velX, double velY, double velZ, SpriteSet spriteProvider) {
        super(clientWorld, x, y, z, velX, velY, velZ);
        this.spriteProvider = spriteProvider;
        this.pickSprite(spriteProvider);

        this.lifetime = this.random.nextIntBetweenInclusive(7, 30);
        this.quadSize = 0.5f;
        this.gravity = 0.04f;
        this.setSpriteFromAge(this.spriteProvider);

        int color = WavifyColors.getWaterColor(clientWorld, BlockPos.containing(x, y, z));
        this.setColor(WavifyColors.red(color), WavifyColors.green(color), WavifyColors.blue(color));
    }

    @Override
    public void tick() {
        super.tick();
        this.yd = this.yd - (double)this.gravity;
        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i) {
            return new BigSplashParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
        }
    }
}
