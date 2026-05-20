package net.superkat.wavify.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleType;
import net.superkat.wavify.WavifyParticles;

public class WhiteSprayParticleEffect extends SprayParticleEffect {
    public WhiteSprayParticleEffect(float yaw, float intensity, float scale) {
        super(yaw, intensity, scale);
    }

    public static final MapCodec<WhiteSprayParticleEffect> CODEC = createCodec(WhiteSprayParticleEffect::new);
    public static final PacketCodec<RegistryByteBuf, WhiteSprayParticleEffect> PACKET_CODEC = createPacketCodec(WhiteSprayParticleEffect::new);

    @Override
    public ParticleType<?> getType() {
        return WavifyParticles.WHITE_SPRAY_PARTICLE;
    }
}
