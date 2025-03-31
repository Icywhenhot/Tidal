package net.superkat.tidal;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.superkat.tidal.particles.SprayParticleEffect;
import net.superkat.tidal.particles.WhiteSprayParticleEffect;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.particles.old.WaveParticleEffect;
import net.superkat.tidal.particles.old.WhiteWaveParticleEffect;

public class TidalParticles {
    public static final String MOD_ID = Tidal.MOD_ID;

    public static final ParticleType<WaveParticleEffect> WAVE_PARTICLE = FabricParticleTypes.complex(WaveParticleEffect.CODEC, WaveParticleEffect.PACKET_CODEC);
    public static final ParticleType<WhiteWaveParticleEffect> WHITE_WAVE_PARTICLE = FabricParticleTypes.complex(WhiteWaveParticleEffect.CODEC, WhiteWaveParticleEffect.PACKET_CODEC);

    public static final ParticleType<SprayParticleEffect> SPRAY_PARTICLE = FabricParticleTypes.complex(SprayParticleEffect.CODEC, SprayParticleEffect.PACKET_CODEC);
    public static final ParticleType<WhiteSprayParticleEffect> WHITE_SPRAY_PARTICLE = FabricParticleTypes.complex(WhiteSprayParticleEffect.CODEC, WhiteSprayParticleEffect.PACKET_CODEC);

    public static final SimpleParticleType SPLASH_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType BIG_SPLASH_PARTICLE = FabricParticleTypes.simple();

    public static final ParticleType<DebugWaterParticle.DebugWaterParticleEffect> DEBUG_WATERBODY_PARTICLE = FabricParticleTypes.complex(
            DebugWaterParticle.DebugWaterParticleEffect.CODEC,
            DebugWaterParticle.DebugWaterParticleEffect.PACKET_CODEC
    );

    public static final ParticleType<DebugShoreParticle.DebugShoreParticleEffect> DEBUG_SHORELINE_PARTICLE = FabricParticleTypes.complex(
            DebugShoreParticle.DebugShoreParticleEffect.CODEC,
            DebugShoreParticle.DebugShoreParticleEffect.PACKET_CODEC
    );

    public static final ParticleType<DebugWaveMovementParticle.DebugWaveMovementParticleEffect> DEBUG_WAVEMOVEMENT_PARTICLE = FabricParticleTypes.complex(
            DebugWaveMovementParticle.DebugWaveMovementParticleEffect.CODEC,
            DebugWaveMovementParticle.DebugWaveMovementParticleEffect.PACKET_CODEC
    );

    public static void registerParticles() {
        register("wave_particle", WAVE_PARTICLE);
        register("white_wave_particle", WHITE_WAVE_PARTICLE);

        register("spray_particle", SPRAY_PARTICLE);
        register("white_spray_particle", WHITE_SPRAY_PARTICLE);

        register("splash", SPLASH_PARTICLE);
        register("bigsplash", BIG_SPLASH_PARTICLE);

        register("debug_waterbody_particle", DEBUG_WATERBODY_PARTICLE);
        register("debug_shoreline_particle", DEBUG_SHORELINE_PARTICLE);
        register("debug_wavemovement_particle", DEBUG_WAVEMOVEMENT_PARTICLE);
    }

    private static void register(String id, ParticleType<?> particleType) {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MOD_ID, id), particleType);
    }

}
