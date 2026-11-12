package net.superkat.wavify.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.config.WavifyConfig;

public final class FrameWatch {

    private static float scale = 1f;
    private static long spent;
    private static long windowStart = System.nanoTime();
    private static long started;

    private FrameWatch() {
    }

    public static void begin() {
        started = System.nanoTime();
    }

    public static void end() {
        spent += System.nanoTime() - started;
    }

    public static void tick(Minecraft mc) {
        long now = System.nanoTime();
        long window = now - windowStart;
        if (window < 1_000_000_000L) return;

        double share = (double) spent / window;
        spent = 0;
        windowStart = now;

        if (!WavifyConfig.autoPerformance) {
            scale = 1f;
            return;
        }

        int limit = mc.options.framerateLimit().get();
        int target = Math.min(60, limit >= 260 ? 60 : limit);
        int fps = mc.getFps();

        if (fps < target * 0.9f && share > 0.03) {
            scale = Math.max(0.25f, scale - 0.15f);
        } else if (fps >= target * 0.97f || share < 0.01) {
            scale = Math.min(1f, scale + 0.05f);
        }
    }

    public static float scale() {
        return scale;
    }

    public static float effects() {
        return (float) WavifyConfig.effectsAmount * scale;
    }

    public static int roll(float amount, RandomSource random) {
        int n = Mth.floor(amount);
        return random.nextFloat() < amount - n ? n + 1 : n;
    }
}
