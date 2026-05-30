package net.superkat.wavify.particles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;

public class WhiteSprayParticle extends SprayParticle {
    public WhiteSprayParticle(ClientLevel level, double x, double y, double z, double velX, double velY, double velZ, WhiteSprayParticleEffect params, SpriteSet spriteProvider) {
        super(level, x, y, z, velX, velY, velZ, params, spriteProvider);
    }

    @Override
    protected boolean spawnWhite() {
        return false;
    }

    public static class Factory implements ParticleProvider<WhiteSprayParticleEffect> {
        public final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(WhiteSprayParticleEffect params, ClientLevel level, double x, double y, double z, double velX, double velY, double velZ) {
            return new WhiteSprayParticle(level, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}
