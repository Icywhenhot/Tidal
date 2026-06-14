package net.superkat.wavify.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class WavifyConfig extends MidnightConfig {
    public static final String WAVES = "waves";
    public static final String APPEARANCE = "appearance";
    public static final String RIVERS = "rivers";
    public static final String SOUNDS = "sounds";

    public enum ColorSource {
        BIOME,
        CUSTOM
    }

    @Comment(category = WAVES, centered = true) public static Comment reloadReminder;
    @Entry(category = WAVES) public static boolean enableOceanWaves = true;
    @Entry(category = WAVES, isSlider = true, min = 3, max = 16) public static int chunkRadius = 5;
    @Entry(category = WAVES, min = 1, max = 1024) public static int chunkUpdatesRescanAmount = 50;
    @Entry(category = WAVES, isSlider = true, min = 4, max = 32) public static int spawnDistance = 8;

    @Entry(category = WAVES) public static boolean debug = false;
    @Comment(category = WAVES, centered = true) public static Comment debugDocs;
    @Comment(category = WAVES) public static Comment debugDocsSite;
    @Comment(category = WAVES) public static Comment debugDocsSpyglass;
    @Comment(category = WAVES) public static Comment debugDocsSpyglassHotbar;
    @Comment(category = WAVES) public static Comment debugDocsClock;
    @Comment(category = WAVES) public static Comment debugDocsCompass;

    @Entry(category = APPEARANCE, isSlider = true, min = 0.0, max = 1.0, precision = 100) public static double transparency = 1.0;
    @Entry(category = APPEARANCE) public static boolean applyTransparencyToFoam = false;
    @Entry(category = APPEARANCE) public static ColorSource colorSource = ColorSource.BIOME;
    @Entry(category = APPEARANCE, isColor = true) public static int customColor = 0x3F76E4;
    @Entry(category = APPEARANCE) public static String biomeColorOverrides = "";

    @Entry(category = WAVES, isSlider = true, min = 0.0, max = 1.0, precision = 100) public static double lakeWaveMultiplier = 0.2;
    @Entry(category = RIVERS) public static boolean enableRiverWaves = true;
    @Entry(category = RIVERS, isSlider = true, min = 16, max = 128) public static int riverWaveSpawnRadius = 56;
    @Entry(category = RIVERS, isSlider = true, min = 0.0, max = 20.0, precision = 10) public static double riverWaveDensity = 4.0;
    @Entry(category = RIVERS, isSlider = true, min = 4, max = 32) public static int riverWaveTravelBlocks = 11;

    @Entry(category = APPEARANCE, isSlider = true, min = -0.5, max = 0.5, precision = 100) public static double waveYOffset = 0.0;
    @Entry(category = APPEARANCE, isSlider = true, min = -0.5, max = 0.5, precision = 100) public static double shaderWaveYSink = -0.30;

    @Entry(category = SOUNDS) public static boolean enableWaveSounds = true;
    @Entry(category = SOUNDS, isSlider = true, min = 0.0, max = 1.0, precision = 100) public static double waveSoundVolume = 1.0;

    public static int waveTicks = 80; // dummy value

    /** Legacy field kept for any external callers; the live value is {@link #spawnDistance}. */
    public static int waveDistFromShore = 8;

    public static boolean modEnabled = true;
}
