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

public class WavifyParticles {
    public static final String MOD_ID = Wavify.MOD_ID;
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    public static final RegistryObject<ParticleType<SprayParticleEffect>> SPRAY_PARTICLE = PARTICLES.register(
            "spray_particle",
            () -> new CodecParticleType<>(false, SprayParticleEffect.DESERIALIZER, SprayParticleEffect.CODEC)
    );
    public static final RegistryObject<ParticleType<SprayParticleEffect>> WHITE_SPRAY_PARTICLE = PARTICLES.register(
            "white_spray_particle",
            () -> new CodecParticleType<>(false, SprayParticleEffect.DESERIALIZER, SprayParticleEffect.CODEC)
    );

    public static final RegistryObject<SimpleParticleType> SPLASH_PARTICLE = PARTICLES.register(
            "splash",
            () -> new SimpleParticleType(false) {}
    );
    public static final RegistryObject<SimpleParticleType> BIG_SPLASH_PARTICLE = PARTICLES.register(
            "bigsplash",
            () -> new SimpleParticleType(false) {}
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
