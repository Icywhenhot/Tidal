package net.superkat.wavify.particles.debug;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
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

    public static class DebugWaterParticleEffect extends AbstractDebugParticleEffect {
        public static final Codec<DebugWaterParticleEffect> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.FLOAT.fieldOf("r").forGetter(effect -> effect.color.x()),
                        Codec.FLOAT.fieldOf("g").forGetter(effect -> effect.color.y()),
                        Codec.FLOAT.fieldOf("b").forGetter(effect -> effect.color.z()),
                        Codec.FLOAT.fieldOf("scale").forGetter(AbstractDebugParticleEffect::getScale)
                ).apply(instance, (r, g, b, scale) -> new DebugWaterParticleEffect(new Vector3f(r, g, b), scale))
        );

        public static final ParticleOptions.Deserializer<DebugWaterParticleEffect> DESERIALIZER = new ParticleOptions.Deserializer<>() {
            @Override
            public DebugWaterParticleEffect fromCommand(ParticleType<DebugWaterParticleEffect> type, StringReader reader) throws CommandSyntaxException {
                Vector3f color = readColor(reader);
                reader.expect(' ');
                float scale = reader.readFloat();
                return new DebugWaterParticleEffect(color, scale);
            }

            @Override
            public DebugWaterParticleEffect fromNetwork(ParticleType<DebugWaterParticleEffect> type, FriendlyByteBuf buf) {
                return new DebugWaterParticleEffect(readColor(buf), buf.readFloat());
            }
        };

        public DebugWaterParticleEffect(Vector3f color, float scale) {
            super(color, scale);
        }

        @Override
        public ParticleType<DebugWaterParticleEffect> getType() {
            return WavifyParticles.DEBUG_WATERBODY_PARTICLE.get();
        }
    }

}
