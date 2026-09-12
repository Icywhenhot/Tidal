package net.superkat.wavify.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.superkat.wavify.WavifyParticles;

public class SprayParticleEffect implements ParticleOptions {

    public static final MapCodec<SprayParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("yaw").forGetter(SprayParticleEffect::getYaw),
                    Codec.FLOAT.fieldOf("intensity").forGetter(SprayParticleEffect::getIntensity),
                    Codec.FLOAT.fieldOf("scale").forGetter(SprayParticleEffect::getScale),
                    Codec.BOOL.fieldOf("white").forGetter(SprayParticleEffect::isWhite)
            ).apply(instance, SprayParticleEffect::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SprayParticleEffect> PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SprayParticleEffect::getYaw,
            ByteBufCodecs.FLOAT, SprayParticleEffect::getIntensity,
            ByteBufCodecs.FLOAT, SprayParticleEffect::getScale,
            ByteBufCodecs.BOOL, SprayParticleEffect::isWhite,
            SprayParticleEffect::new
    );

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
        return this.white ? WavifyParticles.WHITE_SPRAY_PARTICLE : WavifyParticles.SPRAY_PARTICLE;
    }
}
