package net.superkat.wavify.particles;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Function3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.superkat.wavify.WavifyParticles;

import java.util.Locale;

public class SprayParticleEffect implements ParticleOptions {

    protected static <T extends SprayParticleEffect> Codec<T> createCodec(Function3<Float, Float, Float, T> particle) {
        return RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.FLOAT.fieldOf("yaw").forGetter(SprayParticleEffect::getYaw),
                        Codec.FLOAT.fieldOf("intensity").forGetter(SprayParticleEffect::getIntensity),
                        Codec.FLOAT.fieldOf("scale").forGetter(SprayParticleEffect::getScale)
                ).apply(instance, particle)
        );
    }

    protected static <T extends SprayParticleEffect> ParticleOptions.Deserializer<T> createDeserializer(Function3<Float, Float, Float, T> particle) {
        return new ParticleOptions.Deserializer<>() {
            @Override
            public T fromCommand(ParticleType<T> type, StringReader reader) throws CommandSyntaxException {
                reader.expect(' ');
                float yaw = reader.readFloat();
                reader.expect(' ');
                float intensity = reader.readFloat();
                reader.expect(' ');
                float scale = reader.readFloat();
                return particle.apply(yaw, intensity, scale);
            }

            @Override
            public T fromNetwork(ParticleType<T> type, FriendlyByteBuf buf) {
                return particle.apply(buf.readFloat(), buf.readFloat(), buf.readFloat());
            }
        };
    }

    public static final Codec<SprayParticleEffect> CODEC = createCodec(SprayParticleEffect::new);
    public static final ParticleOptions.Deserializer<SprayParticleEffect> DESERIALIZER = createDeserializer(SprayParticleEffect::new);

    protected final float yaw;
    protected final float intensity;
    protected final float scale;

    public SprayParticleEffect(float yaw, float intensity, float scale) {
        this.yaw = yaw;
        this.intensity = intensity;
        this.scale = scale;
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

    @Override
    public ParticleType<?> getType() {
        return WavifyParticles.SPRAY_PARTICLE.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.intensity);
        buf.writeFloat(this.scale);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f",
                BuiltInRegistries.PARTICLE_TYPE.getKey(this.getType()), this.yaw, this.intensity, this.scale);
    }
}
