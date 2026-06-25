package net.superkat.wavify.particles;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.superkat.wavify.WavifyParticles;

public class WhiteSprayParticleEffect extends SprayParticleEffect {
    public WhiteSprayParticleEffect(float yaw, float intensity, float scale) {
        super(yaw, intensity, scale);
    }

    public static final Codec<WhiteSprayParticleEffect> CODEC = createCodec(WhiteSprayParticleEffect::new);
    public static final ParticleOptions.Deserializer<WhiteSprayParticleEffect> DESERIALIZER = createDeserializer(WhiteSprayParticleEffect::new);

    @Override
    public ParticleType<?> getType() {
        return WavifyParticles.WHITE_SPRAY_PARTICLE.get();
    }
}
