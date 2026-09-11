package net.superkat.wavify;

import net.minecraft.client.world.ClientWorld;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.sound.WaveAmbientSoundManager;
import net.superkat.wavify.sprite.WavifySpriteHandler;
import net.superkat.wavify.util.WavifyColors;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.jetbrains.annotations.Nullable;

public final class ClientState {

    public static final WavifySpriteHandler SPRITES = new WavifySpriteHandler();
    public static final WaveAmbientSoundManager SOUND = new WaveAmbientSoundManager();

    private ClientState() {
    }

    public static WavifyWaveHandler wavesIn(ClientWorld world) {
        return ((WavifyWorld) world).wavify$wavifyWaveHandler();
    }

    public static void worldChanged(@Nullable ClientWorld world) {
        SOUND.hardReset();
        WavifyColors.forget();
        if (world == null) return;
        wavesIn(world).reloadNearbyChunks();
    }

    public static void cachesInvalidated(@Nullable ClientWorld world) {
        WavifyColors.forget();
        if (world == null) return;
        WavifyWaveHandler waves = wavesIn(world);
        waves.reloadNearbyChunks();
        waves.waterHandler.rebuild();
    }

    public static void disconnected() {
        SOUND.hardReset();
        WavifyColors.forget();
    }
}
