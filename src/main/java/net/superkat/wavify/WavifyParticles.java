package net.superkat.wavify;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.superkat.wavify.particles.SprayParticleEffect;

public class WavifyParticles {
    public static final String MOD_ID = Wavify.MOD_ID;

    public static final ParticleType<SprayParticleEffect> SPRAY_PARTICLE = FabricParticleTypes.complex(SprayParticleEffect.CODEC, SprayParticleEffect.PACKET_CODEC);
    public static final ParticleType<SprayParticleEffect> WHITE_SPRAY_PARTICLE = FabricParticleTypes.complex(SprayParticleEffect.CODEC, SprayParticleEffect.PACKET_CODEC);

    public static final SimpleParticleType SPLASH_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType BIG_SPLASH_PARTICLE = FabricParticleTypes.simple();

    public static void registerParticles() {
        register("spray_particle", SPRAY_PARTICLE);
        register("white_spray_particle", WHITE_SPRAY_PARTICLE);

        register("splash", SPLASH_PARTICLE);
        register("bigsplash", BIG_SPLASH_PARTICLE);

    }

    private static void register(String id, ParticleType<?> particleType) {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MOD_ID, id), particleType);
    }

}
