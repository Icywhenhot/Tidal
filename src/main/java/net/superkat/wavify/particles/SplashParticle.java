package net.superkat.wavify.particles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.util.WavifyColors;
import org.joml.Vector3f;

public class SplashParticle extends WaterDropParticle {
    public SplashParticle(ClientLevel clientWorld, double x, double y, double z, double velX, double velY, double velZ, SpriteSet spriteProvider) {
        super(clientWorld, x, y, z, spriteProvider.first());
        this.gravity = 0.04F;
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        this.updateWaterColor();
    }

    public void updateWaterColor() {
        Vector3f color = WavifyColors.getWaterColorVec(this.level, this.getBlockPos());
        this.setColor(color.x, color.y, color.z);
    }

    public BlockPos getBlockPos() {
        return BlockPos.containing(this.x, this.y, this.z);
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i, RandomSource random) {
            return new SplashParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
        }
    }
}
