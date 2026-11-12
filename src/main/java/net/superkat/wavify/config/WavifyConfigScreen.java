package net.superkat.wavify.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import net.superkat.wavify.wave.RiverStyle;

import java.util.Locale;

public final class WavifyConfigScreen {
    private WavifyConfigScreen() {
    }

    public static Screen create(Screen parent) {
        WavifyConfig.Quality before = WavifyConfig.quality;
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("wavify.configuration.title"))
                .setSavingRunnable(() -> {
                    WavifyConfig.settleQuality(before);
                    WavifyConfig.save();
                });
        ConfigEntryBuilder entry = builder.entryBuilder();

        ConfigCategory waves = builder.getOrCreateCategory(Text.translatable("wavify.configuration.waves"));
        waves.addEntry(entry.startEnumSelector(label("quality"), WavifyConfig.Quality.class, WavifyConfig.quality)
                .setDefaultValue(WavifyConfig.Quality.BALANCED).setTooltip(tip("quality"))
                .setEnumNameProvider(e -> Text.translatable("wavify.configuration.quality." + e.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(v -> WavifyConfig.quality = v).build());
        waves.addEntry(entry.startBooleanToggle(label("auto_performance"), WavifyConfig.autoPerformance)
                .setDefaultValue(true).setTooltip(tip("auto_performance"))
                .setSaveConsumer(v -> WavifyConfig.autoPerformance = v).build());
        waves.addEntry(entry.startBooleanToggle(label("enable_ocean_waves"), WavifyConfig.enableOceanWaves)
                .setDefaultValue(true).setTooltip(tip("enable_ocean_waves"))
                .setSaveConsumer(v -> WavifyConfig.enableOceanWaves = v).build());
        waves.addEntry(entry.startIntSlider(label("chunk_radius"), WavifyConfig.chunkRadius, 3, 16)
                .setDefaultValue(5).setTooltip(tip("chunk_radius"))
                .setSaveConsumer(v -> WavifyConfig.chunkRadius = v).build());
        waves.addEntry(entry.startIntField(label("chunk_updates_rescan_amount"), WavifyConfig.chunkUpdatesRescanAmount)
                .setMin(1).setMax(1024).setDefaultValue(50).setTooltip(tip("chunk_updates_rescan_amount"))
                .setSaveConsumer(v -> WavifyConfig.chunkUpdatesRescanAmount = v).build());
        waves.addEntry(entry.startIntSlider(label("spawn_distance"), WavifyConfig.spawnDistance, 4, 32)
                .setDefaultValue(8).setTooltip(tip("spawn_distance"))
                .setSaveConsumer(v -> WavifyConfig.spawnDistance = v).build());
        waves.addEntry(entry.startDoubleField(label("weather_intensity"), WavifyConfig.weatherIntensity)
                .setMin(0.0).setMax(2.0).setDefaultValue(1.0).setTooltip(tip("weather_intensity"))
                .setSaveConsumer(v -> WavifyConfig.weatherIntensity = v).build());

        ConfigCategory looks = builder.getOrCreateCategory(Text.translatable("wavify.configuration.appearance"));
        looks.addEntry(entry.startIntSlider(label("wave_opacity"), percent(WavifyConfig.waveOpacity), 0, 100)
                .setDefaultValue(100).setTextGetter(WavifyConfigScreen::percentText).setTooltip(tip("wave_opacity"))
                .setSaveConsumer(v -> WavifyConfig.waveOpacity = v / 100.0).build());
        looks.addEntry(entry.startIntSlider(label("foam_opacity"), percent(WavifyConfig.foamOpacity), 0, 100)
                .setDefaultValue(100).setTextGetter(WavifyConfigScreen::percentText).setTooltip(tip("foam_opacity"))
                .setSaveConsumer(v -> WavifyConfig.foamOpacity = v / 100.0).build());
        looks.addEntry(entry.startEnumSelector(label("color_source"), WavifyConfig.ColorSource.class, WavifyConfig.colorSource)
                .setDefaultValue(WavifyConfig.ColorSource.BIOME).setTooltip(tip("color_source"))
                .setEnumNameProvider(e -> Text.translatable("wavify.configuration.color_source." + e.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(v -> WavifyConfig.colorSource = v).build());
        looks.addEntry(entry.startColorField(label("custom_color"), WavifyConfig.customColor)
                .setDefaultValue(0x3F76E4).setTooltip(tip("custom_color"))
                .setSaveConsumer(v -> WavifyConfig.customColor = v).build());
        looks.addEntry(entry.startStrField(label("biome_color_overrides"), WavifyConfig.biomeColorOverrides)
                .setDefaultValue("").setTooltip(tip("biome_color_overrides"))
                .setSaveConsumer(v -> WavifyConfig.biomeColorOverrides = v).build());
        looks.addEntry(entry.startDoubleField(label("wave_y_offset"), WavifyConfig.waveYOffset)
                .setMin(-1.0).setMax(1.0).setDefaultValue(0.0).setTooltip(tip("wave_y_offset"))
                .setSaveConsumer(v -> WavifyConfig.waveYOffset = v).build());
        looks.addEntry(entry.startDoubleField(label("shader_wave_y_sink"), WavifyConfig.shaderWaveYSink)
                .setMin(-0.5).setMax(0.5).setDefaultValue(-0.3).setTooltip(tip("shader_wave_y_sink"))
                .setSaveConsumer(v -> WavifyConfig.shaderWaveYSink = v).build());
        looks.addEntry(entry.startBooleanToggle(label("auto_shader_offsets"), WavifyConfig.autoShaderOffsets)
                .setDefaultValue(true).setTooltip(tip("auto_shader_offsets"))
                .setSaveConsumer(v -> WavifyConfig.autoShaderOffsets = v).build());
        looks.addEntry(entry.startBooleanToggle(label("enable_wet_overlay"), WavifyConfig.enableWetOverlay)
                .setDefaultValue(true).setTooltip(tip("enable_wet_overlay"))
                .setSaveConsumer(v -> WavifyConfig.enableWetOverlay = v).build());
        looks.addEntry(entry.startIntSlider(label("wet_sand_dry_time"), WavifyConfig.wetSandDryTime, 0, 400)
                .setDefaultValue(100).setTooltip(tip("wet_sand_dry_time"))
                .setSaveConsumer(v -> WavifyConfig.wetSandDryTime = v).build());
        looks.addEntry(entry.startBooleanToggle(label("full_moon_glow"), WavifyConfig.fullMoonGlow)
                .setDefaultValue(false).setTooltip(tip("full_moon_glow"))
                .setSaveConsumer(v -> WavifyConfig.fullMoonGlow = v).build());

        ConfigCategory rivers = builder.getOrCreateCategory(Text.translatable("wavify.configuration.rivers"));
        rivers.addEntry(entry.startBooleanToggle(label("enable_river_waves"), WavifyConfig.enableRiverWaves)
                .setDefaultValue(true).setTooltip(tip("enable_river_waves"))
                .setSaveConsumer(v -> WavifyConfig.enableRiverWaves = v).build());
        rivers.addEntry(entry.startEnumSelector(label("river_wave_style"), RiverStyle.class, WavifyConfig.riverWaveStyle)
                .setDefaultValue(RiverStyle.CREST).setTooltip(tip("river_wave_style"))
                .setSaveConsumer(v -> WavifyConfig.riverWaveStyle = v).build());
        rivers.addEntry(entry.startIntSlider(label("river_wave_spawn_radius"), WavifyConfig.riverWaveSpawnRadius, 16, 128)
                .setDefaultValue(56).setTooltip(tip("river_wave_spawn_radius"))
                .setSaveConsumer(v -> WavifyConfig.riverWaveSpawnRadius = v).build());
        rivers.addEntry(entry.startDoubleField(label("river_wave_density"), WavifyConfig.riverWaveDensity)
                .setMin(0.0).setMax(20.0).setDefaultValue(4.0).setTooltip(tip("river_wave_density"))
                .setSaveConsumer(v -> WavifyConfig.riverWaveDensity = v).build());
        rivers.addEntry(entry.startIntSlider(label("river_wave_travel_blocks"), WavifyConfig.riverWaveTravelBlocks, 4, 48)
                .setDefaultValue(18).setTooltip(tip("river_wave_travel_blocks"))
                .setSaveConsumer(v -> WavifyConfig.riverWaveTravelBlocks = v).build());
        rivers.addEntry(entry.startDoubleField(label("river_wave_y_offset"), WavifyConfig.riverWaveYOffset)
                .setMin(-1.0).setMax(1.0).setDefaultValue(0.0).setTooltip(tip("river_wave_y_offset"))
                .setSaveConsumer(v -> WavifyConfig.riverWaveYOffset = v).build());

        ConfigCategory effects = builder.getOrCreateCategory(Text.translatable("wavify.configuration.effects"));
        effects.addEntry(entry.startIntSlider(label("effects_amount"), percent(WavifyConfig.effectsAmount), 0, 200)
                .setDefaultValue(100).setTextGetter(WavifyConfigScreen::percentText).setTooltip(tip("effects_amount"))
                .setSaveConsumer(v -> WavifyConfig.effectsAmount = v / 100.0).build());
        effects.addEntry(entry.startBooleanToggle(label("boat_wakes"), WavifyConfig.boatWakes)
                .setDefaultValue(true).setTooltip(tip("boat_wakes"))
                .setSaveConsumer(v -> WavifyConfig.boatWakes = v).build());
        effects.addEntry(entry.startBooleanToggle(label("entity_ripples"), WavifyConfig.entityRipples)
                .setDefaultValue(true).setTooltip(tip("entity_ripples"))
                .setSaveConsumer(v -> WavifyConfig.entityRipples = v).build());
        effects.addEntry(entry.startBooleanToggle(label("waterfall_mist"), WavifyConfig.waterfallMist)
                .setDefaultValue(true).setTooltip(tip("waterfall_mist"))
                .setSaveConsumer(v -> WavifyConfig.waterfallMist = v).build());
        effects.addEntry(entry.startBooleanToggle(label("rain_ripples"), WavifyConfig.rainRipples)
                .setDefaultValue(true).setTooltip(tip("rain_ripples"))
                .setSaveConsumer(v -> WavifyConfig.rainRipples = v).build());
        effects.addEntry(entry.startBooleanToggle(label("ice_and_slush"), WavifyConfig.iceAndSlush)
                .setDefaultValue(true).setTooltip(tip("ice_and_slush"))
                .setSaveConsumer(v -> WavifyConfig.iceAndSlush = v).build());

        ConfigCategory sounds = builder.getOrCreateCategory(Text.translatable("wavify.configuration.sounds"));
        sounds.addEntry(entry.startBooleanToggle(label("enable_wave_sounds"), WavifyConfig.enableWaveSounds)
                .setDefaultValue(true).setTooltip(tip("enable_wave_sounds"))
                .setSaveConsumer(v -> WavifyConfig.enableWaveSounds = v).build());
        sounds.addEntry(entry.startIntSlider(label("wave_sound_volume"), percent(WavifyConfig.waveSoundVolume), 0, 100)
                .setDefaultValue(100).setTextGetter(WavifyConfigScreen::percentText).setTooltip(tip("wave_sound_volume"))
                .setSaveConsumer(v -> WavifyConfig.waveSoundVolume = v / 100.0).build());

        return builder.build();
    }

    private static Text label(String key) {
        return Text.translatable("wavify.configuration." + key);
    }

    private static Text[] tip(String key) {
        String full = "wavify.configuration." + key + ".tooltip";
        return Language.getInstance().hasTranslation(full) ? new Text[]{Text.translatable(full)} : new Text[0];
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100);
    }

    private static Text percentText(int value) {
        return Text.literal(value + "%");
    }
}
