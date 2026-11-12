package net.superkat.wavify.config;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.superkat.wavify.wave.RiverStyle;

public final class WavifyConfig {
    public static final String WAVES = "waves";
    public static final String APPEARANCE = "appearance";
    public static final String RIVERS = "rivers";
    public static final String SOUNDS = "sounds";
    public static final String EFFECTS = "effects";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum ColorSource {
        BIOME,
        CUSTOM
    }

    public enum Quality implements TranslatableEnum {
        PERFORMANCE(3, 32, 2.0, 0.4),
        BALANCED(5, 56, 4.0, 1.0),
        FANCY(6, 72, 5.0, 1.4),
        ULTRA(8, 96, 7.0, 2.0),
        CUSTOM(0, 0, 0, 0);

        final int chunks;
        final int riverRadius;
        final double riverDensity;
        final double effects;

        Quality(int chunks, int riverRadius, double riverDensity, double effects) {
            this.chunks = chunks;
            this.riverRadius = riverRadius;
            this.riverDensity = riverDensity;
            this.effects = effects;
        }

        boolean matches() {
            return CHUNK_RADIUS_VALUE.get() == chunks
                    && RIVER_WAVE_SPAWN_RADIUS_VALUE.get() == riverRadius
                    && Math.abs(RIVER_WAVE_DENSITY_VALUE.get() - riverDensity) < 1.0e-4
                    && Math.abs(EFFECTS_AMOUNT_VALUE.get() - effects) < 1.0e-4;
        }

        void write() {
            CHUNK_RADIUS_VALUE.set(chunks);
            RIVER_WAVE_SPAWN_RADIUS_VALUE.set(riverRadius);
            RIVER_WAVE_DENSITY_VALUE.set(riverDensity);
            EFFECTS_AMOUNT_VALUE.set(effects);
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("wavify.configuration.quality." + name().toLowerCase());
        }
    }

    private static final ModConfigSpec.EnumValue<Quality> QUALITY_VALUE;
    private static final ModConfigSpec.BooleanValue AUTO_PERFORMANCE_VALUE;
    private static final ModConfigSpec.DoubleValue EFFECTS_AMOUNT_VALUE;
    private static final ModConfigSpec.BooleanValue BOAT_WAKES_VALUE;
    private static final ModConfigSpec.BooleanValue ENTITY_RIPPLES_VALUE;
    private static final ModConfigSpec.BooleanValue WATERFALL_MIST_VALUE;
    private static final ModConfigSpec.BooleanValue RAIN_RIPPLES_VALUE;
    private static final ModConfigSpec.BooleanValue ICE_AND_SLUSH_VALUE;
    private static final ModConfigSpec.BooleanValue AUTO_SHADER_OFFSETS_VALUE;

    private static final ModConfigSpec.BooleanValue ENABLE_OCEAN_WAVES_VALUE;
    private static final ModConfigSpec.IntValue CHUNK_RADIUS_VALUE;
    private static final ModConfigSpec.IntValue CHUNK_UPDATES_RESCAN_AMOUNT_VALUE;
    private static final ModConfigSpec.IntValue SPAWN_DISTANCE_VALUE;
    private static final ModConfigSpec.DoubleValue WEATHER_INTENSITY_VALUE;
    private static final ModConfigSpec.DoubleValue WAVE_OPACITY_VALUE;
    private static final ModConfigSpec.DoubleValue FOAM_OPACITY_VALUE;
    private static final ModConfigSpec.BooleanValue FULL_MOON_GLOW_VALUE;
    private static final ModConfigSpec.EnumValue<ColorSource> COLOR_SOURCE_VALUE;
    private static final ModConfigSpec.IntValue CUSTOM_COLOR_VALUE;
    private static final ModConfigSpec.ConfigValue<String> BIOME_COLOR_OVERRIDES_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_WET_OVERLAY_VALUE;
    private static final ModConfigSpec.IntValue WET_SAND_DRY_TIME_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_RIVER_WAVES_VALUE;
    private static final ModConfigSpec.EnumValue<RiverStyle> RIVER_WAVE_STYLE_VALUE;
    private static final ModConfigSpec.IntValue RIVER_WAVE_SPAWN_RADIUS_VALUE;
    private static final ModConfigSpec.DoubleValue RIVER_WAVE_DENSITY_VALUE;
    private static final ModConfigSpec.IntValue RIVER_WAVE_TRAVEL_BLOCKS_VALUE;
    private static final ModConfigSpec.DoubleValue WAVE_Y_OFFSET_VALUE;
    private static final ModConfigSpec.DoubleValue RIVER_WAVE_Y_OFFSET_VALUE;
    private static final ModConfigSpec.DoubleValue SHADER_WAVE_Y_SINK_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_WAVE_SOUNDS_VALUE;
    private static final ModConfigSpec.DoubleValue WAVE_SOUND_VOLUME_VALUE;

    public static final ModConfigSpec SPEC;

    static {
        QUALITY_VALUE = BUILDER.comment("One-click quality. Sets chunk radius, river wave radius and density, and effects amount. Changing any of those by hand switches this to CUSTOM.")
                .translation("wavify.configuration.quality")
                .defineEnum("quality", Quality.BALANCED);
        AUTO_PERFORMANCE_VALUE = BUILDER.comment("Quietly spawns fewer waves and effects while your FPS is low and Wavify is part of the reason.")
                .translation("wavify.configuration.auto_performance")
                .define("autoPerformance", true);

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
        WEATHER_INTENSITY_VALUE = BUILDER.comment("How much rain and thunderstorms whip up the waves. 0 = weather has no effect.")
                .translation("wavify.configuration.weather_intensity")
                .defineInRange("weatherIntensity", 1.0D, 0.0D, 2.0D);
        BUILDER.pop();

        BUILDER.translation("wavify.configuration." + APPEARANCE).push(APPEARANCE);
        WAVE_OPACITY_VALUE = BUILDER.comment("Opacity of the colored wave body. Lower it to see through the waves.")
                .translation("wavify.configuration.wave_opacity")
                .defineInRange("waveOpacity", 1.0D, 0.0D, 1.0D);
        FOAM_OPACITY_VALUE = BUILDER.comment("Opacity of the white foam on top of the waves.")
                .translation("wavify.configuration.foam_opacity")
                .defineInRange("foamOpacity", 1.0D, 0.0D, 1.0D);
        COLOR_SOURCE_VALUE = BUILDER.comment("Whether wave tinting uses biome water colors or the custom color below.")
                .translation("wavify.configuration.color_source")
                .defineEnum("colorSource", ColorSource.BIOME);
        CUSTOM_COLOR_VALUE = BUILDER.comment("Custom wave color when Color Source is set to CUSTOM.")
                .translation("wavify.configuration.custom_color")
                .defineInRange("customColor", 0x3F76E4, 0x000000, 0xFFFFFF);
        BIOME_COLOR_OVERRIDES_VALUE = BUILDER.comment("Comma-separated biome=color overrides. Use a biome id or a #tag, for example minecraft:lukewarm_ocean=3F76E4,#c:is_cold=3D57D6. Ids win over tags.")
                .translation("wavify.configuration.biome_color_overrides")
                .define("biomeColorOverrides", "");
        WAVE_Y_OFFSET_VALUE = BUILDER.comment("Moves ocean and shoreline waves up or down. Negative = lower, closer to the water.")
                .translation("wavify.configuration.wave_y_offset")
                .defineInRange("waveYOffset", 0.0D, -1.0D, 1.0D);
        SHADER_WAVE_Y_SINK_VALUE = BUILDER.comment("Extra downward offset applied to the wave body while shaders are active.")
                .translation("wavify.configuration.shader_wave_y_sink")
                .defineInRange("shaderWaveYSink", -0.30D, -0.5D, 0.5D);
        AUTO_SHADER_OFFSETS_VALUE = BUILDER.comment("Uses a tested wave offset for known shader packs (Complementary, BSL, Sildur's, MakeUp, Bliss, Photon, Solas, Rethinking Voxels). Unknown packs use Shader Wave Y Sink.")
                .translation("wavify.configuration.auto_shader_offsets")
                .define("autoShaderOffsets", true);
        ENABLE_WET_OVERLAY_VALUE = BUILDER.comment("Darkens the ground where a wave washes up, as if the sand got wet. Turn off to disable the effect entirely.")
                .translation("wavify.configuration.enable_wet_overlay")
                .define("enableWetOverlay", true);
        WET_SAND_DRY_TIME_VALUE = BUILDER.comment("How many ticks wet sand takes to dry after a wave leaves it. 0 = dries instantly.")
                .translation("wavify.configuration.wet_sand_dry_time")
                .defineInRange("wetSandDryTime", 100, 0, 400);
        FULL_MOON_GLOW_VALUE = BUILDER.comment("Makes ocean waves glow at full brightness on full moon nights.")
                .translation("wavify.configuration.full_moon_glow")
                .define("fullMoonGlow", false);
        BUILDER.pop();

        BUILDER.translation("wavify.configuration." + RIVERS).push(RIVERS);
        ENABLE_RIVER_WAVES_VALUE = BUILDER.comment("Master toggle for river waves.")
                .translation("wavify.configuration.enable_river_waves")
                .define("enableRiverWaves", true);
        RIVER_WAVE_STYLE_VALUE = BUILDER.comment("How river waves look. CREST = curved crests, STREAKS = thin foam lines along the current, SWELL = soft wide bands, RAPIDS = choppy whitewater, AUTO = picks per spot from river width, depth and current.")
                .translation("wavify.configuration.river_wave_style")
                .defineEnum("riverWaveStyle", RiverStyle.CREST);
        RIVER_WAVE_SPAWN_RADIUS_VALUE = BUILDER.comment("Radius (in blocks) around the player within which river waves are spawned. Larger = waves appear further away, smaller = they only appear close by.")
                .translation("wavify.configuration.river_wave_spawn_radius")
                .defineInRange("riverWaveSpawnRadius", 56, 16, 128);
        RIVER_WAVE_DENSITY_VALUE = BUILDER.comment("River wave density - roughly how many river waves appear per 100 river water blocks near you.")
                .translation("wavify.configuration.river_wave_density")
                .defineInRange("riverWaveDensity", 4.0D, 0.0D, 20.0D);
        RIVER_WAVE_TRAVEL_BLOCKS_VALUE = BUILDER.comment("How far (in blocks) a river wave travels down the channel before fading out.")
                .translation("wavify.configuration.river_wave_travel_blocks")
                .defineInRange("riverWaveTravelBlocks", 18, 4, 48);
        RIVER_WAVE_Y_OFFSET_VALUE = BUILDER.comment("Moves river waves up or down. Negative = lower, closer to the water.")
                .translation("wavify.configuration.river_wave_y_offset")
                .defineInRange("riverWaveYOffset", 0.0D, -1.0D, 1.0D);
        BUILDER.pop();

        BUILDER.translation("wavify.configuration." + EFFECTS).push(EFFECTS);
        EFFECTS_AMOUNT_VALUE = BUILDER.comment("How many ripples, wakes, mist and slush pieces get spawned. 0 = none.")
                .translation("wavify.configuration.effects_amount")
                .defineInRange("effectsAmount", 1.0D, 0.0D, 2.0D);
        BOAT_WAKES_VALUE = BUILDER.comment("V-shaped foam trails behind moving boats, plus spray at the bow.")
                .translation("wavify.configuration.boat_wakes")
                .define("boatWakes", true);
        ENTITY_RIPPLES_VALUE = BUILDER.comment("Ripple rings around players and mobs wading, swimming or landing in water.")
                .translation("wavify.configuration.entity_ripples")
                .define("entityRipples", true);
        WATERFALL_MIST_VALUE = BUILDER.comment("Mist, spray and foam rings where falling water lands.")
                .translation("wavify.configuration.waterfall_mist")
                .define("waterfallMist", true);
        RAIN_RIPPLES_VALUE = BUILDER.comment("Little rings on the water surface while it rains.")
                .translation("wavify.configuration.rain_ripples")
                .define("rainRipples", true);
        ICE_AND_SLUSH_VALUE = BUILDER.comment("Waves fade out at ice, and snowy oceans get floating slush instead of surf.")
                .translation("wavify.configuration.ice_and_slush")
                .define("iceAndSlush", true);
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

    public static boolean autoPerformance = true;
    public static double effectsAmount = 1.0;
    public static boolean boatWakes = true;
    public static boolean entityRipples = true;
    public static boolean waterfallMist = true;
    public static boolean rainRipples = true;
    public static boolean iceAndSlush = true;
    public static boolean autoShaderOffsets = true;
    public static boolean enableOceanWaves = true;
    public static int chunkRadius = 5;
    public static int chunkUpdatesRescanAmount = 50;
    public static int spawnDistance = 8;
    public static double weatherIntensity = 1.0;
    public static double waveOpacity = 1.0;
    public static double foamOpacity = 1.0;
    public static boolean fullMoonGlow = false;
    public static ColorSource colorSource = ColorSource.BIOME;
    public static int customColor = 0x3F76E4;
    public static String biomeColorOverrides = "";
    public static boolean enableWetOverlay = true;
    public static int wetSandDryTime = 100;
    public static boolean enableRiverWaves = true;
    public static RiverStyle riverWaveStyle = RiverStyle.CREST;
    public static int riverWaveSpawnRadius = 56;
    public static double riverWaveDensity = 4.0;
    public static int riverWaveTravelBlocks = 18;
    public static double waveYOffset = 0.0;
    public static double riverWaveYOffset = 0.0;
    public static double shaderWaveYSink = -0.30;
    public static boolean enableWaveSounds = true;
    public static double waveSoundVolume = 1.0;

    private static Quality lastQuality;

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

        Quality q = QUALITY_VALUE.get();
        if (q != Quality.CUSTOM && !q.matches()) {
            if (lastQuality != null && q != lastQuality) {
                lastQuality = q;
                q.write();
            } else {
                lastQuality = Quality.CUSTOM;
                QUALITY_VALUE.set(Quality.CUSTOM);
            }
            SPEC.save();
            return;
        }
        lastQuality = q;

        autoPerformance = AUTO_PERFORMANCE_VALUE.get();
        effectsAmount = EFFECTS_AMOUNT_VALUE.get();
        boatWakes = BOAT_WAKES_VALUE.get();
        entityRipples = ENTITY_RIPPLES_VALUE.get();
        waterfallMist = WATERFALL_MIST_VALUE.get();
        rainRipples = RAIN_RIPPLES_VALUE.get();
        iceAndSlush = ICE_AND_SLUSH_VALUE.get();
        autoShaderOffsets = AUTO_SHADER_OFFSETS_VALUE.get();

        enableOceanWaves = ENABLE_OCEAN_WAVES_VALUE.get();
        chunkRadius = CHUNK_RADIUS_VALUE.get();
        chunkUpdatesRescanAmount = CHUNK_UPDATES_RESCAN_AMOUNT_VALUE.get();
        spawnDistance = SPAWN_DISTANCE_VALUE.get();
        weatherIntensity = WEATHER_INTENSITY_VALUE.get();
        waveOpacity = WAVE_OPACITY_VALUE.get();
        foamOpacity = FOAM_OPACITY_VALUE.get();
        fullMoonGlow = FULL_MOON_GLOW_VALUE.get();
        colorSource = COLOR_SOURCE_VALUE.get();
        customColor = CUSTOM_COLOR_VALUE.get();
        biomeColorOverrides = BIOME_COLOR_OVERRIDES_VALUE.get();
        enableWetOverlay = ENABLE_WET_OVERLAY_VALUE.get();
        wetSandDryTime = WET_SAND_DRY_TIME_VALUE.get();
        enableRiverWaves = ENABLE_RIVER_WAVES_VALUE.get();
        riverWaveStyle = RIVER_WAVE_STYLE_VALUE.get();
        riverWaveSpawnRadius = RIVER_WAVE_SPAWN_RADIUS_VALUE.get();
        riverWaveDensity = RIVER_WAVE_DENSITY_VALUE.get();
        riverWaveTravelBlocks = RIVER_WAVE_TRAVEL_BLOCKS_VALUE.get();
        waveYOffset = WAVE_Y_OFFSET_VALUE.get();
        riverWaveYOffset = RIVER_WAVE_Y_OFFSET_VALUE.get();
        shaderWaveYSink = SHADER_WAVE_Y_SINK_VALUE.get();
        enableWaveSounds = ENABLE_WAVE_SOUNDS_VALUE.get();
        waveSoundVolume = WAVE_SOUND_VOLUME_VALUE.get();
    }
}
