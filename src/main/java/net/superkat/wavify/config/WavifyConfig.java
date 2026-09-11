package net.superkat.wavify.config;

import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

public final class WavifyConfig {
    public static final String WAVES = "waves";
    public static final String APPEARANCE = "appearance";
    public static final String RIVERS = "rivers";
    public static final String SOUNDS = "sounds";

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public enum ColorSource {
        BIOME,
        CUSTOM
    }

    private static final ForgeConfigSpec.BooleanValue ENABLE_OCEAN_WAVES_VALUE;
    private static final ForgeConfigSpec.IntValue CHUNK_RADIUS_VALUE;
    private static final ForgeConfigSpec.IntValue CHUNK_UPDATES_RESCAN_AMOUNT_VALUE;
    private static final ForgeConfigSpec.IntValue SPAWN_DISTANCE_VALUE;
    private static final ForgeConfigSpec.DoubleValue TRANSPARENCY_VALUE;
    private static final ForgeConfigSpec.BooleanValue APPLY_TRANSPARENCY_TO_FOAM_VALUE;
    private static final ForgeConfigSpec.EnumValue<ColorSource> COLOR_SOURCE_VALUE;
    private static final ForgeConfigSpec.IntValue CUSTOM_COLOR_VALUE;
    private static final ForgeConfigSpec.ConfigValue<String> BIOME_COLOR_OVERRIDES_VALUE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_WET_OVERLAY_VALUE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_RIVER_WAVES_VALUE;
    private static final ForgeConfigSpec.IntValue RIVER_WAVE_SPAWN_RADIUS_VALUE;
    private static final ForgeConfigSpec.DoubleValue RIVER_WAVE_DENSITY_VALUE;
    private static final ForgeConfigSpec.IntValue RIVER_WAVE_TRAVEL_BLOCKS_VALUE;
    private static final ForgeConfigSpec.DoubleValue WAVE_Y_OFFSET_VALUE;
    private static final ForgeConfigSpec.DoubleValue SHADER_WAVE_Y_SINK_VALUE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_WAVE_SOUNDS_VALUE;
    private static final ForgeConfigSpec.DoubleValue WAVE_SOUND_VOLUME_VALUE;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.translation("wavify.configuration." + WAVES).push(WAVES);
        ENABLE_OCEAN_WAVES_VALUE = BUILDER.comment("Master toggle for ocean and beach (shoreline) waves.")
                .translation("wavify.configuration.enable_ocean_waves")
                .define("enableOceanWaves", true);
        CHUNK_RADIUS_VALUE = BUILDER.comment("Chunk radius around the player that is considered for wave spawning.")
                .translation("wavify.configuration.chunk_radius")
                .defineInRange("chunkRadius", 5, 3, 16);
        CHUNK_UPDATES_RESCAN_AMOUNT_VALUE = BUILDER.comment("How many block updates a chunk can receive before being rescanned.")
                .translation("wavify.configuration.chunk_updates_rescan_amount")
                .defineInRange("chunkUpdatesRescanAmount", 50, 1, 1024);
        SPAWN_DISTANCE_VALUE = BUILDER.comment("How many blocks from shore waves try to spawn.")
                .translation("wavify.configuration.spawn_distance")
                .defineInRange("spawnDistance", 8, 4, 32);
        BUILDER.pop();

        BUILDER.translation("wavify.configuration." + APPEARANCE).push(APPEARANCE);
        TRANSPARENCY_VALUE = BUILDER.comment("Overall wave body transparency.")
                .translation("wavify.configuration.transparency")
                .defineInRange("transparency", 1.0D, 0.0D, 1.0D);
        APPLY_TRANSPARENCY_TO_FOAM_VALUE = BUILDER.comment("Applies the transparency slider to foam as well.")
                .translation("wavify.configuration.apply_transparency_to_foam")
                .define("applyTransparencyToFoam", false);
        COLOR_SOURCE_VALUE = BUILDER.comment("Whether wave tinting uses biome water colors or the custom color below.")
                .translation("wavify.configuration.color_source")
                .defineEnum("colorSource", ColorSource.BIOME);
        CUSTOM_COLOR_VALUE = BUILDER.comment("Custom wave color when Color Source is set to CUSTOM.")
                .translation("wavify.configuration.custom_color")
                .defineInRange("customColor", 0x3F76E4, 0x000000, 0xFFFFFF);
        BIOME_COLOR_OVERRIDES_VALUE = BUILDER.comment("Comma-separated biome=color overrides, for example minecraft:luke_warm_ocean=3F76E4.")
                .translation("wavify.configuration.biome_color_overrides")
                .define("biomeColorOverrides", "");
        WAVE_Y_OFFSET_VALUE = BUILDER.comment("Offsets both the wave body and foam vertically.")
                .translation("wavify.configuration.wave_y_offset")
                .defineInRange("waveYOffset", 0.0D, -0.5D, 0.5D);
        SHADER_WAVE_Y_SINK_VALUE = BUILDER.comment("Extra downward offset applied to the wave body while shaders are active.")
                .translation("wavify.configuration.shader_wave_y_sink")
                .defineInRange("shaderWaveYSink", -0.30D, -0.5D, 0.5D);
        ENABLE_WET_OVERLAY_VALUE = BUILDER.comment("Darkens the ground where a wave washes up, as if the sand got wet. Turn off to disable the effect entirely.")
                .translation("wavify.configuration.enable_wet_overlay")
                .define("enableWetOverlay", true);
        BUILDER.pop();

        BUILDER.translation("wavify.configuration." + RIVERS).push(RIVERS);
        ENABLE_RIVER_WAVES_VALUE = BUILDER.comment("Master toggle for river waves.")
                .translation("wavify.configuration.enable_river_waves")
                .define("enableRiverWaves", true);
        RIVER_WAVE_SPAWN_RADIUS_VALUE = BUILDER.comment("Radius (in blocks) around the player within which river waves are spawned. Larger = waves appear further away, smaller = they only appear close by.")
                .translation("wavify.configuration.river_wave_spawn_radius")
                .defineInRange("riverWaveSpawnRadius", 56, 16, 128);
        RIVER_WAVE_DENSITY_VALUE = BUILDER.comment("River wave density - roughly how many river waves appear per 100 river water blocks near you.")
                .translation("wavify.configuration.river_wave_density")
                .defineInRange("riverWaveDensity", 4.0D, 0.0D, 20.0D);
        RIVER_WAVE_TRAVEL_BLOCKS_VALUE = BUILDER.comment("How far (in blocks) a river wave travels down the channel before fading out.")
                .translation("wavify.configuration.river_wave_travel_blocks")
                .defineInRange("riverWaveTravelBlocks", 11, 4, 32);
        BUILDER.pop();

        BUILDER.translation("wavify.configuration." + SOUNDS).push(SOUNDS);
        ENABLE_WAVE_SOUNDS_VALUE = BUILDER.comment("Enables the ambient ocean and river sound loops.")
                .translation("wavify.configuration.enable_wave_sounds")
                .define("enableWaveSounds", true);
        WAVE_SOUND_VOLUME_VALUE = BUILDER.comment("Master volume multiplier for ambient wave loops.")
                .translation("wavify.configuration.wave_sound_volume")
                .defineInRange("waveSoundVolume", 1.0D, 0.0D, 1.0D);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean enableOceanWaves = true;
    public static int chunkRadius = 5;
    public static int chunkUpdatesRescanAmount = 50;
    public static int spawnDistance = 8;
    public static double transparency = 1.0;
    public static boolean applyTransparencyToFoam = false;
    public static ColorSource colorSource = ColorSource.BIOME;
    public static int customColor = 0x3F76E4;
    public static String biomeColorOverrides = "";
    public static boolean enableWetOverlay = true;
    public static boolean enableRiverWaves = true;
    public static int riverWaveSpawnRadius = 56;
    public static double riverWaveDensity = 4.0;
    public static int riverWaveTravelBlocks = 11;
    public static double waveYOffset = 0.0;
    public static double shaderWaveYSink = -0.30;
    public static boolean enableWaveSounds = true;
    public static double waveSoundVolume = 1.0;

    private WavifyConfig() {
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        sync(event);
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        sync(event);
    }

    private static void sync(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        enableOceanWaves = ENABLE_OCEAN_WAVES_VALUE.get();
        chunkRadius = CHUNK_RADIUS_VALUE.get();
        chunkUpdatesRescanAmount = CHUNK_UPDATES_RESCAN_AMOUNT_VALUE.get();
        spawnDistance = SPAWN_DISTANCE_VALUE.get();
        transparency = TRANSPARENCY_VALUE.get();
        applyTransparencyToFoam = APPLY_TRANSPARENCY_TO_FOAM_VALUE.get();
        colorSource = COLOR_SOURCE_VALUE.get();
        customColor = CUSTOM_COLOR_VALUE.get();
        biomeColorOverrides = BIOME_COLOR_OVERRIDES_VALUE.get();
        enableWetOverlay = ENABLE_WET_OVERLAY_VALUE.get();
        enableRiverWaves = ENABLE_RIVER_WAVES_VALUE.get();
        riverWaveSpawnRadius = RIVER_WAVE_SPAWN_RADIUS_VALUE.get();
        riverWaveDensity = RIVER_WAVE_DENSITY_VALUE.get();
        riverWaveTravelBlocks = RIVER_WAVE_TRAVEL_BLOCKS_VALUE.get();
        waveYOffset = WAVE_Y_OFFSET_VALUE.get();
        shaderWaveYSink = SHADER_WAVE_Y_SINK_VALUE.get();
        enableWaveSounds = ENABLE_WAVE_SOUNDS_VALUE.get();
        waveSoundVolume = WAVE_SOUND_VOLUME_VALUE.get();
    }
}
