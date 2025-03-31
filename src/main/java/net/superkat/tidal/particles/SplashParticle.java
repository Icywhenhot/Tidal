package net.superkat.tidal.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.RainSplashParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;

public class SplashParticle extends RainSplashParticle {
    public SplashParticle(ClientWorld clientWorld, double x, double y, double z, double velX, double velY, double velZ) {
        super(clientWorld, x, y, z);
        this.gravityStrength = 0.04F;
        this.velocityX = velX;
        this.velocityY = velY;
        this.velocityZ = velZ;
        if(this.random.nextBoolean()) {
            this.updateWaterColor();
        }
    }

    public void updateWaterColor() {
        int color = BiomeColors.getWaterColor(this.world, this.getPos());
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        this.setColor(r, g, b);
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

        public Particle createParticle(SimpleParticleType simpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            SplashParticle waterSplashParticle = new SplashParticle(clientWorld, d, e, f, g, h, i);
            waterSplashParticle.setSprite(this.spriteProvider);
            return waterSplashParticle;
        }
    }
}
