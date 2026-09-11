package net.superkat.wavify.particles;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.superkat.wavify.WavifyParticles;

import java.util.Locale;

public class SprayParticleEffect implements ParticleOptions {

    public static final Codec<SprayParticleEffect> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("yaw").forGetter(SprayParticleEffect::getYaw),
                    Codec.FLOAT.fieldOf("intensity").forGetter(SprayParticleEffect::getIntensity),
                    Codec.FLOAT.fieldOf("scale").forGetter(SprayParticleEffect::getScale),
                    Codec.BOOL.fieldOf("white").forGetter(SprayParticleEffect::isWhite)
            ).apply(instance, SprayParticleEffect::new)
    );

    public static final ParticleOptions.Deserializer<SprayParticleEffect> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public SprayParticleEffect fromCommand(ParticleType<SprayParticleEffect> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float yaw = reader.readFloat();
            reader.expect(' ');
            float intensity = reader.readFloat();
            reader.expect(' ');
            float scale = reader.readFloat();
            reader.expect(' ');
            boolean white = reader.readBoolean();
            return new SprayParticleEffect(yaw, intensity, scale, white);
        }

        @Override
        public SprayParticleEffect fromNetwork(ParticleType<SprayParticleEffect> type, FriendlyByteBuf buf) {
            float yaw = buf.readFloat();
            float intensity = buf.readFloat();
            float scale = buf.readFloat();
            boolean white = buf.readBoolean();
            return new SprayParticleEffect(yaw, intensity, scale, white);
        }
    };

    protected final float yaw;
    protected final float intensity;
    protected final float scale;
    protected final boolean white;

    public SprayParticleEffect(float yaw, float intensity, float scale, boolean white) {
        this.yaw = yaw;
        this.intensity = intensity;
        this.scale = scale;
        this.white = white;
    }

    public float getYaw() {
        return yaw;
    }

    public float getIntensity() {
        return intensity;
    }

    public float getScale() {
        return scale;
    }

    public boolean isWhite() {
        return white;
    }

    @Override
    public ParticleType<?> getType() {
        return this.white ? WavifyParticles.WHITE_SPRAY_PARTICLE.get() : WavifyParticles.SPRAY_PARTICLE.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.intensity);
        buf.writeFloat(this.scale);
        buf.writeBoolean(this.white);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %b",
                BuiltInRegistries.PARTICLE_TYPE.getKey(this.getType()), this.yaw, this.intensity, this.scale, this.white);
    }
}
