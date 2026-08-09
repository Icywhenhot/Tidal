package net.superkat.wavify.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.wave.RiverWave;
import net.superkat.wavify.wave.Wave;
import net.superkat.wavify.wave.WavifyWaveHandler;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// runs the ambient ocean and river loops, ticked client side
// every tick we look through the active waves, find the closest ocean and river ones in range,
// and set a target volume on the matching loop, the sound instance does the actual lerp
// the ocean variant gets picked once each time the loop starts from cold, either wave 1 or wave 2
public final class WaveAmbientSoundManager {
    private static final double DETECTION_RADIUS = 48.0;
    private static final double DETECTION_RADIUS_SQ = DETECTION_RADIUS * DETECTION_RADIUS;

    // loudest the loop gets, ramp speed lives in the sound instance
    // full volume right on top of a wave, silent at the edge of the radius
    private static final float OCEAN_MAX_VOLUME = 0.6f;
    private static final float RIVER_MAX_VOLUME = 0.5f;

    private WaveAmbientSoundInstance oceanLoop;
    private WaveAmbientSoundInstance riverLoop;

    public void tick() {
        if (!WavifyConfig.enableWaveSounds) {
            stopAll();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            stopAll();
            return;
        }

        WavifyWaveHandler handler = ((WavifyWorld) mc.world).wavify$wavifyWaveHandler();
        List<Wave> waves = handler.getWaves();
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        double closestOceanSq = Double.MAX_VALUE;
        double closestRiverSq = Double.MAX_VALUE;

        if (waves != null) {
            for (Wave wave : waves) {
                double dx = wave.x - playerPos.x;
                double dy = wave.y - playerPos.y;
                double dz = wave.z - playerPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > DETECTION_RADIUS_SQ) continue;

                if (wave instanceof RiverWave) {
                    if (distSq < closestRiverSq) closestRiverSq = distSq;
                } else {
                    if (distSq < closestOceanSq) closestOceanSq = distSq;
                }
            }
        }

        float configVol = (float) WavifyConfig.waveSoundVolume;
        updateLoop(true, closestOceanSq, OCEAN_MAX_VOLUME * configVol);
        updateLoop(false, closestRiverSq, RIVER_MAX_VOLUME * configVol);
    }

    private void updateLoop(boolean ocean, double closestSq, float maxVolume) {
        WaveAmbientSoundInstance loop = ocean ? oceanLoop : riverLoop;
        boolean present = closestSq < Double.MAX_VALUE;
        float target = 0f;
        if (present) {
            // straight linear falloff from 1 down to 0 across the radius
            double dist = Math.sqrt(closestSq);
            float falloff = (float) Math.max(0.0, 1.0 - dist / DETECTION_RADIUS);
            target = maxVolume * falloff;
        }

        if (present && (loop == null || loop.isDone())) {
            SoundEvent event = ocean ? pickOceanVariant() : WavifySounds.RIVER_WAVE;
            loop = new WaveAmbientSoundInstance(event, maxVolume);
            MinecraftClient.getInstance().getSoundManager().play(loop);
            if (ocean) oceanLoop = loop;
            else riverLoop = loop;
        }

        if (loop != null) {
            loop.targetVolume = target;
            if (loop.isDone()) {
                if (ocean) oceanLoop = null;
                else riverLoop = null;
            }
        }
    }

    private SoundEvent pickOceanVariant() {
        return ThreadLocalRandom.current().nextBoolean() ? WavifySounds.OCEAN_WAVE_1 : WavifySounds.OCEAN_WAVE_2;
    }

    public void stopAll() {
        if (oceanLoop != null) {
            oceanLoop.requestStop();
        }
        if (riverLoop != null) {
            riverLoop.requestStop();
        }
    }

    // leaving a world or swapping dimensions, kill the loops right away
    public void hardReset() {
        SoundManager sm = MinecraftClient.getInstance().getSoundManager();
        if (oceanLoop != null) {
            sm.stop(oceanLoop);
            oceanLoop = null;
        }
        if (riverLoop != null) {
            sm.stop(riverLoop);
            riverLoop = null;
        }
    }
}
