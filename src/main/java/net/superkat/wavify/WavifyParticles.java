package net.superkat.wavify;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.superkat.wavify.particles.SprayParticleEffect;
import net.superkat.wavify.particles.WhiteSprayParticleEffect;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;

public class WavifyParticles {
    public static final String MOD_ID = Wavify.MOD_ID;

    public static final ParticleType<SprayParticleEffect> SPRAY_PARTICLE =
            new CodecParticleType<>(false, SprayParticleEffect.DESERIALIZER, SprayParticleEffect.CODEC);
    public static final ParticleType<WhiteSprayParticleEffect> WHITE_SPRAY_PARTICLE =
            new CodecParticleType<>(false, WhiteSprayParticleEffect.DESERIALIZER, WhiteSprayParticleEffect.CODEC);

    public static final SimpleParticleType SPLASH_PARTICLE = new SimpleParticleType(false) {};
    public static final SimpleParticleType BIG_SPLASH_PARTICLE = new SimpleParticleType(false) {};

    public static final ParticleType<DebugWaterParticle.DebugWaterParticleEffect> DEBUG_WATERBODY_PARTICLE =
            new CodecParticleType<>(
                    false,
                    DebugWaterParticle.DebugWaterParticleEffect.DESERIALIZER,
                    DebugWaterParticle.DebugWaterParticleEffect.CODEC
            );

    public static final ParticleType<DebugShoreParticle.DebugShoreParticleEffect> DEBUG_SHORELINE_PARTICLE =
            new CodecParticleType<>(
                    false,
                    DebugShoreParticle.DebugShoreParticleEffect.DESERIALIZER,
                    DebugShoreParticle.DebugShoreParticleEffect.CODEC
            );

    public static final ParticleType<DebugWaveMovementParticle.DebugWaveMovementParticleEffect> DEBUG_WAVEMOVEMENT_PARTICLE =
            new CodecParticleType<>(
                    false,
                    DebugWaveMovementParticle.DebugWaveMovementParticleEffect.DESERIALIZER,
                    DebugWaveMovementParticle.DebugWaveMovementParticleEffect.CODEC
            );

    public static void register() {
        register("spray_particle", SPRAY_PARTICLE);
        register("white_spray_particle", WHITE_SPRAY_PARTICLE);

        register("splash", SPLASH_PARTICLE);
        register("bigsplash", BIG_SPLASH_PARTICLE);

        register("debug_waterbody_particle", DEBUG_WATERBODY_PARTICLE);
        register("debug_shoreline_particle", DEBUG_SHORELINE_PARTICLE);
        register("debug_wavemovement_particle", DEBUG_WAVEMOVEMENT_PARTICLE);
    }

    private static void register(String name, ParticleType<?> particleType) {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, new ResourceLocation(MOD_ID, name), particleType);
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
