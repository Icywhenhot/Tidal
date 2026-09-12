package net.superkat.wavify.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.RainSplashParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.superkat.wavify.util.WavifyColors;

public class SplashParticle extends RainSplashParticle {
    public SplashParticle(ClientWorld clientWorld, double x, double y, double z, double velX, double velY, double velZ, SpriteProvider spriteProvider) {
        super(clientWorld, x, y, z, spriteProvider.getFirst());
        this.gravityStrength = 0.04F;
        this.velocityX = velX;
        this.velocityY = velY;
        this.velocityZ = velZ;
        this.updateWaterColor();
    }

    public void updateWaterColor() {
        int color = WavifyColors.getWaterColor(this.world, this.getPos());
        this.setColor(WavifyColors.red(color), WavifyColors.green(color), WavifyColors.blue(color));
    }

    public BlockPos getPos() {
        return BlockPos.ofFloored(this.x, this.y, this.z);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType simpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i, Random random) {
            return new SplashParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
        }
    }
}
