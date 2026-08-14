package net.superkat.wavify;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sound.WavifySounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Wavify.MOD_ID)
public class Wavify {
    public static final String MOD_ID = "wavify";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Wavify() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        WavifyParticles.register(modEventBus);
        WavifySounds.register(modEventBus);

        modEventBus.addListener(WavifyConfig::onLoad);
        modEventBus.addListener(WavifyConfig::onReload);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WavifyConfig.SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WavifyClient.init(modEventBus));
    }
}
