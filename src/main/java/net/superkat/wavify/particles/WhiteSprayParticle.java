package net.superkat.wavify.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.random.Random;

public class WhiteSprayParticle extends SprayParticle {
    public WhiteSprayParticle(ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, WhiteSprayParticleEffect params, SpriteProvider spriteProvider) {
        super(world, x, y, z, velX, velY, velZ, params, spriteProvider);
    }

    @Override
    protected boolean spawnWhite() {
        return false;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<WhiteSprayParticleEffect> {
        public final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(WhiteSprayParticleEffect params, ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, Random random) {
            return new WhiteSprayParticle(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}
