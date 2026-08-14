package net.superkat.wavify.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.wave.RiverWave;
import net.superkat.wavify.wave.Wave;
import net.superkat.wavify.wave.WavifyWaveHandler;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class WaveAmbientSoundManager {
    private static final double DETECTION_RADIUS = 48.0;
    private static final double DETECTION_RADIUS_SQ = DETECTION_RADIUS * DETECTION_RADIUS;

    private static final float OCEAN_MAX_VOLUME = 0.6f;
    private static final float RIVER_MAX_VOLUME = 0.5f;

    private WaveAmbientSoundInstance oceanLoop;
    private WaveAmbientSoundInstance riverLoop;

    public void tick() {
        if (!WavifyConfig.enableWaveSounds) {
            stopAll();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            stopAll();
            return;
        }

        WavifyWaveHandler handler = ((WavifyWorld) mc.level).wavify$wavifyWaveHandler();
        List<Wave> waves = handler.getWaves();
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());

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
            double dist = Math.sqrt(closestSq);
            float falloff = (float) Math.max(0.0, 1.0 - dist / DETECTION_RADIUS);
            target = maxVolume * falloff;
        }

        if (present && (loop == null || loop.isStopped())) {
            SoundEvent event = ocean ? pickOceanVariant() : WavifySounds.RIVER_WAVE.get();
            loop = new WaveAmbientSoundInstance(event, maxVolume);
            Minecraft.getInstance().getSoundManager().play(loop);
            if (ocean) oceanLoop = loop;
            else riverLoop = loop;
        }

        if (loop != null) {
            loop.targetVolume = target;
            if (loop.isStopped()) {
                if (ocean) oceanLoop = null;
                else riverLoop = null;
            }
        }
    }

    private SoundEvent pickOceanVariant() {
        return ThreadLocalRandom.current().nextBoolean() ? WavifySounds.OCEAN_WAVE_1.get() : WavifySounds.OCEAN_WAVE_2.get();
    }

    public void stopAll() {
        if (oceanLoop != null) {
            oceanLoop.requestStop();
        }
        if (riverLoop != null) {
            riverLoop.requestStop();
        }
    }

    public void hardReset() {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
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
