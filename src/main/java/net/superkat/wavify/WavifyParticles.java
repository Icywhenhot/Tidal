package net.superkat.wavify;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.superkat.wavify.particles.SprayParticleEffect;
import net.superkat.wavify.particles.WhiteSprayParticleEffect;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;

public class WavifyParticles {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    public static final RegistryObject<ParticleType<SprayParticleEffect>> SPRAY_PARTICLE = PARTICLES.register(
            "spray_particle",
            () -> new CodecParticleType<>(false, SprayParticleEffect.DESERIALIZER, SprayParticleEffect.CODEC)
    );
    public static final RegistryObject<ParticleType<WhiteSprayParticleEffect>> WHITE_SPRAY_PARTICLE = PARTICLES.register(
            "white_spray_particle",
            () -> new CodecParticleType<>(false, WhiteSprayParticleEffect.DESERIALIZER, WhiteSprayParticleEffect.CODEC)
    );

    public static final RegistryObject<SimpleParticleType> SPLASH_PARTICLE = PARTICLES.register(
            "splash",
            () -> new SimpleParticleType(false) {}
    );
    public static final RegistryObject<SimpleParticleType> BIG_SPLASH_PARTICLE = PARTICLES.register(
            "bigsplash",
            () -> new SimpleParticleType(false) {}
    );

    public static final RegistryObject<ParticleType<DebugWaterParticle.DebugWaterParticleEffect>> DEBUG_WATERBODY_PARTICLE = PARTICLES.register(
            "debug_waterbody_particle",
            () -> new CodecParticleType<>(
                    false,
                    DebugWaterParticle.DebugWaterParticleEffect.DESERIALIZER,
                    DebugWaterParticle.DebugWaterParticleEffect.CODEC
            )
    );

    public static final RegistryObject<ParticleType<DebugShoreParticle.DebugShoreParticleEffect>> DEBUG_SHORELINE_PARTICLE = PARTICLES.register(
            "debug_shoreline_particle",
            () -> new CodecParticleType<>(
                    false,
                    DebugShoreParticle.DebugShoreParticleEffect.DESERIALIZER,
                    DebugShoreParticle.DebugShoreParticleEffect.CODEC
            )
    );

    public static final RegistryObject<ParticleType<DebugWaveMovementParticle.DebugWaveMovementParticleEffect>> DEBUG_WAVEMOVEMENT_PARTICLE = PARTICLES.register(
            "debug_wavemovement_particle",
            () -> new CodecParticleType<>(
                    false,
                    DebugWaveMovementParticle.DebugWaveMovementParticleEffect.DESERIALIZER,
                    DebugWaveMovementParticle.DebugWaveMovementParticleEffect.CODEC
            )
    );

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }

    private static final class CodecParticleType<T extends ParticleOptions> extends ParticleType<T> {
        private final Codec<T> codec;

        private CodecParticleType(boolean overrideLimiter, ParticleOptions.Deserializer<T> deserializer, Codec<T> codec) {
            super(overrideLimiter, deserializer);
            this.codec = codec;
        }

        @Override
        public Codec<T> codec() {
            return this.codec;
        }
    }
}
