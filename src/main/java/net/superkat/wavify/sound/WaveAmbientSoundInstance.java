package net.superkat.wavify.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/**
 * Looping ambient sound attached to the player. {@link WaveAmbientSoundManager}
 * sets {@link #targetVolume} each tick; this instance lerps {@code volume}
 * toward it for smooth fades, and self-discards once volume reaches 0 with a
 * zero target (true fade-out).
 */
public class WaveAmbientSoundInstance extends AbstractTickableSoundInstance {
    private static final float FADE_PER_TICK = 1f / 70f; // ~3.5 seconds 0 -> 1 at 20 tps

    public float targetVolume = 0f;
    private boolean done = false;

    public WaveAmbientSoundInstance(SoundEvent event, float maxVolume) {
        super(event, SoundSource.AMBIENT, Minecraft.getInstance().level == null
                ? net.minecraft.util.RandomSource.create()
                : Minecraft.getInstance().level.getRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f; // start near silence; lerp up
        this.pitch = 1.0f;
        this.attenuation = Attenuation.NONE; // ambient, not positional
        // Position is set in tick() to follow the player.
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
        // Once volume + target both reach 0, the manager will mark us done.
        if (this.targetVolume <= 0f && this.volume <= 0.001f) {
            this.done = true;
        }
        // Clamp for safety.
        this.volume = Mth.clamp(this.volume, 0f, 1f);
    }
}
