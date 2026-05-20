package net.superkat.wavify.particles.debug;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.AbstractDustParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.random.Random;
import net.superkat.wavify.WavifyParticles;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class DebugShoreParticle extends DebugAbstractColoredParticle<DebugShoreParticle.DebugShoreParticleEffect> {

    public DebugShoreParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, DebugShoreParticleEffect parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);
        this.red = this.darken(parameters.color.x(), 0.8f);
        this.green = this.darken(parameters.color.y(), 0.8f);
        this.blue = this.darken(parameters.color.z(), 0.8f);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DebugShoreParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DebugShoreParticleEffect dustParticleEffect, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i, Random random) {
            return new DebugShoreParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugShoreParticleEffect extends AbstractDustParticleEffect {
        public static final MapCodec<DebugShoreParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codecs.VECTOR_3F.fieldOf("color").forGetter(effect -> effect.color), SCALE_CODEC.fieldOf("scale").forGetter(AbstractDustParticleEffect::getScale)
                        )
                        .apply(instance, DebugShoreParticleEffect::new)
        );
        public static final PacketCodec<RegistryByteBuf, DebugShoreParticleEffect> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.VECTOR_3F, effect -> effect.color, PacketCodecs.FLOAT, AbstractDustParticleEffect::getScale, DebugShoreParticleEffect::new
        );
        private final Vector3fc color;

        public DebugShoreParticleEffect(Vector3fc color, float scale) {
            super(scale);
            this.color = color;
        }

        @Override
        public ParticleType<DebugShoreParticleEffect> getType() {
            return WavifyParticles.DEBUG_SHORELINE_PARTICLE;
        }
    }

}
