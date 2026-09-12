package net.superkat.wavify;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.superkat.wavify.particles.SprayParticleEffect;

public class WavifyParticles {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<SprayParticleEffect>> SPRAY_PARTICLE = PARTICLES.register(
            "spray_particle",
            () -> new CodecParticleType<>(false, SprayParticleEffect.CODEC, SprayParticleEffect.PACKET_CODEC)
    );
    public static final DeferredHolder<ParticleType<?>, ParticleType<SprayParticleEffect>> WHITE_SPRAY_PARTICLE = PARTICLES.register(
            "white_spray_particle",
            () -> new CodecParticleType<>(false, SprayParticleEffect.CODEC, SprayParticleEffect.PACKET_CODEC)
    );

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPLASH_PARTICLE = PARTICLES.register(
            "splash",
            () -> new SimpleParticleType(false) {}
    );
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BIG_SPLASH_PARTICLE = PARTICLES.register(
            "bigsplash",
            () -> new SimpleParticleType(false) {}
    );

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }

    private static final class CodecParticleType<T extends ParticleOptions> extends ParticleType<T> {
        private final MapCodec<T> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, T> packetCodec;

        private CodecParticleType(boolean overrideLimiter, MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> packetCodec) {
            super(overrideLimiter);
            this.codec = codec;
            this.packetCodec = packetCodec;
        }

        @Override
        public MapCodec<T> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
            return this.packetCodec;
        }
    }
}
