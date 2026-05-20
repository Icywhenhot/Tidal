package net.superkat.wavify;

import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.duck.WavifyWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wavify implements ModInitializer {
	public static final String MOD_ID = "wavify";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MidnightConfig.init(MOD_ID, WavifyConfig.class);

		ClientTickEvents.END_WORLD_TICK.register(clientWorld -> {
			WavifyWorld wavifyWorld = (WavifyWorld) clientWorld;
			wavifyWorld.wavify$wavifyWaveHandler().tick();
		});

		WavifyParticles.registerParticles();
	}
}