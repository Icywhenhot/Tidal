package net.superkat.tidal.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class TidalConfig extends MidnightConfig {
    // A proper config screen will come soon
    // MidnightLib will be used for BlanketCon versions(bc its fast & easy), YetAnotherConfigLib(YACL) will be used afterward
    // For now though, this exists to make it easy to implement configurable stuff whilst making the code

    public static final String WAVES = "waves";

    @Comment(category = WAVES, centered = true) public static Comment reloadReminder;
    @Entry(category = WAVES, isSlider = true, min = 3, max = 16) public static int chunkRadius = 5;
    @Entry(category = WAVES, min = 1, max = 1024) public static int chunkUpdatesRescanAmount = 50;

    @Entry(category = WAVES) public static boolean debug = false;
    @Comment(category = WAVES, centered = true) public static Comment debugDocs;
    @Comment(category = WAVES) public static Comment debugDocsSite;
    @Comment(category = WAVES) public static Comment debugDocsSpyglass;
    @Comment(category = WAVES) public static Comment debugDocsSpyglassHotbar;
    @Comment(category = WAVES) public static Comment debugDocsClock;
    @Comment(category = WAVES) public static Comment debugDocsCompass;

    public static int waveTicks = 80; // dummy value
    public static int waveDistFromShore = 8;

    public static boolean modEnabled = true; // unused for now because no time
}
