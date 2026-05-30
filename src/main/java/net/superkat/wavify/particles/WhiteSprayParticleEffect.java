package net.superkat.wavify.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.particles.ParticleType;
import net.superkat.wavify.WavifyParticles;

public class WhiteSprayParticleEffect extends SprayParticleEffect {
    public WhiteSprayParticleEffect(float yaw, float intensity, float scale) {
        super(yaw, intensity, scale);
    }

    public static final MapCodec<WhiteSprayParticleEffect> CODEC = createCodec(WhiteSprayParticleEffect::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, WhiteSprayParticleEffect> PACKET_CODEC = createPacketCodec(WhiteSprayParticleEffect::new);

    @Override
    public ParticleType<?> getType() {
        return WavifyParticles.WHITE_SPRAY_PARTICLE.get();
    }
}
