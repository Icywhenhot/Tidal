package net.superkat.wavify;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sound.WavifySounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Wavify.MOD_ID)
public class Wavify {
    public static final String MOD_ID = "wavify";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Wavify(IEventBus modEventBus, ModContainer modContainer) {
        WavifyParticles.register(modEventBus);
        WavifySounds.register(modEventBus);
        modEventBus.addListener(WavifyConfig::onLoad);
        modEventBus.addListener(WavifyConfig::onReload);
        modContainer.registerConfig(ModConfig.Type.CLIENT, WavifyConfig.SPEC);
    }
}
