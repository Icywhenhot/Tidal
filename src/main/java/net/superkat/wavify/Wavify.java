package net.superkat.wavify;

import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ModInitializer;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sound.WavifySounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wavify implements ModInitializer {
    public static final String MOD_ID = "wavify";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MidnightConfig.init(MOD_ID, WavifyConfig.class);

        WavifyParticles.register();
        WavifySounds.register();
    }
}
