package net.superkat.wavify.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

// looping ambient sound stuck to the player
// the manager sets a target volume each tick and this lerps toward it so fades stay smooth
// once both volume and target hit 0 it discards itself
public class WaveAmbientSoundInstance extends MovingSoundInstance {
    private static final float FADE_PER_TICK = 1f / 70f; // about 3.5 seconds to go silent to full at 20 tps

    public float targetVolume = 0f;
    private boolean done = false;

    public WaveAmbientSoundInstance(SoundEvent event, float maxVolume) {
        super(event, SoundCategory.AMBIENT, MinecraftClient.getInstance().world == null
                ? net.minecraft.util.math.random.Random.create()
                : MinecraftClient.getInstance().world.random);
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.0001f; // basically silent, we lerp up from here
        this.pitch = 1.0f;
        this.attenuationType = AttenuationType.NONE; // ambient so it doesn't fall off with distance
        // tick() moves it to follow the player
    }

    public void requestStop() {
        this.targetVolume = 0f;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void tick() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
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
        // when volume and target are both 0 the manager marks us done
        if (this.targetVolume <= 0f && this.volume <= 0.001f) {
            this.done = true;
        }
        // just in case
        this.volume = MathHelper.clamp(this.volume, 0f, 1f);
    }
}
