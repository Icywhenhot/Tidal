package net.superkat.wavify.particles.debug;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.particles.ScalableParticleOptionsBase;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.ExtraCodecs;
import net.superkat.wavify.WavifyParticles;
import org.joml.Vector3f;

public class DebugWaterParticle extends DebugAbstractColoredParticle<DebugWaterParticle.DebugWaterParticleEffect> {

    public DebugWaterParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, DebugWaterParticleEffect parameters, SpriteSet spriteProvider) {
        super(level, x, y, z, xd, yd, zd, parameters, spriteProvider);
        this.rCol = this.randomizeColor(parameters.color.x(), 1);
        this.gCol = this.randomizeColor(parameters.color.y(), 1);
        this.bCol = this.randomizeColor(parameters.color.z(), 1);
    }

    public static class Factory implements ParticleProvider<DebugWaterParticleEffect> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DebugWaterParticleEffect dustParticleEffect, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugWaterParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaterParticleEffect extends ScalableParticleOptionsBase {
        public static final MapCodec<DebugWaterParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(effect -> effect.color), SCALE.fieldOf("scale").forGetter(ScalableParticleOptionsBase::getScale)
                        )
                        .apply(instance, DebugWaterParticleEffect::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, DebugWaterParticleEffect> PACKET_CODEC = StreamCodec.composite(
                ByteBufCodecs.VECTOR3F, effect -> effect.color, ByteBufCodecs.FLOAT, ScalableParticleOptionsBase::getScale, DebugWaterParticleEffect::new
        );
        private final Vector3f color;

        public DebugWaterParticleEffect(Vector3f color, float scale) {
            super(scale);
            this.color = color;
        }

        @Override
        public ParticleType<DebugWaterParticleEffect> getType() {
            return WavifyParticles.DEBUG_WATERBODY_PARTICLE.get();
        }
    }

}
