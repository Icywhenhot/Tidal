package net.superkat.wavify.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.superkat.wavify.util.WavifyColors;

public class BigSplashParticle extends BillboardParticle {
    private final SpriteProvider spriteProvider;
    public BigSplashParticle(ClientWorld clientWorld, double x, double y, double z, double velX, double velY, double velZ, SpriteProvider spriteProvider) {
        super(clientWorld, x, y, z, velX, velY, velZ, spriteProvider.getFirst());
        this.spriteProvider = spriteProvider;

        this.maxAge = this.random.nextBetween(7, 30);
        this.scale = 0.5f;
        this.gravityStrength = 0.04f;

        int color = WavifyColors.getWaterColor(clientWorld, BlockPos.ofFloored(x, y, z));
        this.setColor(WavifyColors.red(color), WavifyColors.green(color), WavifyColors.blue(color));

        this.updateSprite(this.spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        this.velocityY = this.velocityY - (double)this.gravityStrength;
        this.updateSprite(this.spriteProvider);
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType simpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i, Random random) {
            return new BigSplashParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
        }
    }
}
