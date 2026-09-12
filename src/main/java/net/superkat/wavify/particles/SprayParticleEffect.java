package net.superkat.wavify.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.superkat.wavify.WavifyParticles;

public class SprayParticleEffect implements ParticleEffect {

    public static final MapCodec<SprayParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("yaw").forGetter(SprayParticleEffect::getYaw),
                    Codec.FLOAT.fieldOf("intensity").forGetter(SprayParticleEffect::getIntensity),
                    Codec.FLOAT.fieldOf("scale").forGetter(SprayParticleEffect::getScale),
                    Codec.BOOL.fieldOf("white").forGetter(SprayParticleEffect::isWhite)
            ).apply(instance, SprayParticleEffect::new)
    );

    public static final PacketCodec<RegistryByteBuf, SprayParticleEffect> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, SprayParticleEffect::getYaw,
            PacketCodecs.FLOAT, SprayParticleEffect::getIntensity,
            PacketCodecs.FLOAT, SprayParticleEffect::getScale,
            PacketCodecs.BOOLEAN, SprayParticleEffect::isWhite,
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
