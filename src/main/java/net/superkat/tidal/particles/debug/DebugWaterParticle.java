package net.superkat.tidal.particles.debug;

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
import net.superkat.tidal.TidalParticles;
import org.joml.Vector3fc;

public class DebugWaterParticle extends DebugAbstractColoredParticle<DebugWaterParticle.DebugWaterParticleEffect> {

    public DebugWaterParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, DebugWaterParticleEffect parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);
        this.red = this.darken(parameters.color.x(), 1);
        this.green = this.darken(parameters.color.y(), 1);
        this.blue = this.darken(parameters.color.z(), 1);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DebugWaterParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DebugWaterParticleEffect dustParticleEffect, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i, Random random) {
            return new DebugWaterParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaterParticleEffect extends AbstractDustParticleEffect {
        public static final MapCodec<DebugWaterParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codecs.VECTOR_3F.fieldOf("color").forGetter(effect -> effect.color), SCALE_CODEC.fieldOf("scale").forGetter(AbstractDustParticleEffect::getScale)
                        )
                        .apply(instance, DebugWaterParticleEffect::new)
        );
        public static final PacketCodec<RegistryByteBuf, DebugWaterParticleEffect> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.VECTOR_3F, effect -> effect.color, PacketCodecs.FLOAT, AbstractDustParticleEffect::getScale, DebugWaterParticleEffect::new
        );
        private final Vector3fc color;

        public DebugWaterParticleEffect(Vector3fc color, float scale) {
            super(scale);
            this.color = color;
        }

        @Override
        public ParticleType<DebugWaterParticleEffect> getType() {
            return TidalParticles.DEBUG_WATERBODY_PARTICLE;
        }
    }

}
