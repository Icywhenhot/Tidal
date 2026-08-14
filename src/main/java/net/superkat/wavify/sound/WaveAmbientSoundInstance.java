package net.superkat.wavify.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

public class WaveAmbientSoundInstance extends AbstractTickableSoundInstance {
    private static final float FADE_PER_TICK = 1f / 70f;

    public float targetVolume = 0f;
    private boolean done = false;

    public WaveAmbientSoundInstance(SoundEvent event, float maxVolume) {
        super(event, SoundSource.AMBIENT, Minecraft.getInstance().level == null
                ? net.minecraft.util.RandomSource.create()
                : Minecraft.getInstance().level.getRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.pitch = 1.0f;
        this.attenuation = Attenuation.NONE;
    }

    public void requestStop() {
        this.targetVolume = 0f;
    }

    @Override
    public boolean isStopped() {
        return done;
    }

    @Override
    public void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            this.done = true;
            return;
        }
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();

        if (this.volume < this.targetVolume) {
            this.volume = Math.min(this.targetVolume, this.volume + FADE_PER_TICK);
        } else if (this.volume > this.targetVolume) {
            this.volume = Math.max(this.targetVolume, this.volume - FADE_PER_TICK);
        }
        if (this.targetVolume <= 0f && this.volume <= 0.001f) {
            this.done = true;
        }
        this.volume = Mth.clamp(this.volume, 0f, 1f);
    }
}
