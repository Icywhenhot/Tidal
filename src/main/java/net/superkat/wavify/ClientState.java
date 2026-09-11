package net.superkat.wavify;

import net.minecraft.client.multiplayer.ClientLevel;
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

    public static WavifyWaveHandler wavesIn(ClientLevel level) {
        return ((WavifyWorld) level).wavify$wavifyWaveHandler();
    }

    public static void worldChanged(@Nullable ClientLevel level) {
        SOUND.hardReset();
        WavifyColors.forget();
        if (level == null) return;
        wavesIn(level).reloadNearbyChunks();
    }

    public static void cachesInvalidated(@Nullable ClientLevel level) {
        WavifyColors.forget();
        if (level == null) return;
        WavifyWaveHandler waves = wavesIn(level);
        waves.reloadNearbyChunks();
        waves.waterHandler.rebuild();
    }

    public static void disconnected() {
        SOUND.hardReset();
        WavifyColors.forget();
    }
}
