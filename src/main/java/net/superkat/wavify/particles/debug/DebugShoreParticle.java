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

public class DebugShoreParticle extends DebugAbstractColoredParticle<DebugShoreParticle.DebugShoreParticleEffect> {

    public DebugShoreParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, DebugShoreParticleEffect parameters, SpriteSet spriteProvider) {
        super(level, x, y, z, xd, yd, zd, parameters, spriteProvider);
        this.rCol = this.randomizeColor(parameters.color.x(), 0.8f);
        this.gCol = this.randomizeColor(parameters.color.y(), 0.8f);
        this.bCol = this.randomizeColor(parameters.color.z(), 0.8f);
    }

    public static class Factory implements ParticleProvider<DebugShoreParticleEffect> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(DebugShoreParticleEffect dustParticleEffect, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugShoreParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugShoreParticleEffect extends AbstractDebugParticleEffect {
        public static final Codec<DebugShoreParticleEffect> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.FLOAT.fieldOf("r").forGetter(effect -> effect.color.x()),
                        Codec.FLOAT.fieldOf("g").forGetter(effect -> effect.color.y()),
                        Codec.FLOAT.fieldOf("b").forGetter(effect -> effect.color.z()),
                        Codec.FLOAT.fieldOf("scale").forGetter(AbstractDebugParticleEffect::getScale)
                ).apply(instance, (r, g, b, scale) -> new DebugShoreParticleEffect(new Vector3f(r, g, b), scale))
        );

        public static final ParticleOptions.Deserializer<DebugShoreParticleEffect> DESERIALIZER = new ParticleOptions.Deserializer<>() {
            @Override
            public DebugShoreParticleEffect fromCommand(ParticleType<DebugShoreParticleEffect> type, StringReader reader) throws CommandSyntaxException {
                Vector3f color = readColor(reader);
                reader.expect(' ');
                float scale = reader.readFloat();
                return new DebugShoreParticleEffect(color, scale);
            }

            @Override
            public DebugShoreParticleEffect fromNetwork(ParticleType<DebugShoreParticleEffect> type, FriendlyByteBuf buf) {
                return new DebugShoreParticleEffect(readColor(buf), buf.readFloat());
            }
        };

        public DebugShoreParticleEffect(Vector3f color, float scale) {
            super(color, scale);
        }

        @Override
        public ParticleType<DebugShoreParticleEffect> getType() {
            return WavifyParticles.DEBUG_SHORELINE_PARTICLE;
        }
    }

}
