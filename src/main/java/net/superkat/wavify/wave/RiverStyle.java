package net.superkat.wavify.wave;

import net.minecraft.util.RandomSource;

public enum RiverStyle {
    AUTO(1, 11, 1f, 1f, 0.62f, 1f, 0.64f, 1.12f, 0.055f, 1f, 1f, 1f, 1f, 1f, 0f),
    CREST(5, 11, 1f, 1f, 0.62f, 1f, 0.64f, 1.12f, 0.055f, 1f, 1f, 1f, 1f, 1f, 0f),
    STREAKS(1, 1, 0f, 0f, 0.22f, 0.22f, 1.2f, 1.2f, 0f, 0.3f, 1.8f, 3.5f, 0.55f, 0.85f, 0f),
    SWELL(7, 13, 1.35f, 0.15f, 1f, 1f, 0.8f, 0.9f, 0.02f, 0.6f, 0.55f, 0.4f, 0.45f, 1.6f, 0f),
    RAPIDS(3, 3, 0.55f, 1.6f, 0.7f, 0.85f, 0.55f, 0.75f, 0.09f, 2.2f, 2.0f, 1.6f, 1.2f, 0.75f, 0.04f);

    public final int minColumns;
    public final int maxColumns;
    public final float spread;
    public final float curve;
    public final float widthIn;
    public final float widthOut;
    public final float lengthIn;
    public final float lengthOut;
    public final float lift;
    public final float pulse;
    public final float speed;
    public final float density;
    public final float foam;
    public final float travel;
    public final float sprayChance;

    RiverStyle(int minColumns, int maxColumns, float spread, float curve, float widthIn, float widthOut,
               float lengthIn, float lengthOut, float lift, float pulse, float speed, float density,
               float foam, float travel, float sprayChance) {
        this.minColumns = minColumns;
        this.maxColumns = maxColumns;
        this.spread = spread;
        this.curve = curve;
        this.widthIn = widthIn;
        this.widthOut = widthOut;
        this.lengthIn = lengthIn;
        this.lengthOut = lengthOut;
        this.lift = lift;
        this.pulse = pulse;
        this.speed = speed;
        this.density = density;
        this.foam = foam;
        this.travel = travel;
        this.sprayChance = sprayChance;
    }

    public double spacing() {
        return switch (this) {
            case STREAKS -> 2.0;
            case SWELL -> 8.0;
            case RAPIDS -> 3.5;
            default -> 5.0;
        };
    }

    public static RiverStyle pick(int width, int depth, boolean flowing, float storm, RandomSource random) {
        if (width <= 4 && (flowing || depth <= 2 || random.nextFloat() < storm * 0.6f)) return RAPIDS;
        if (width >= 8 && depth >= 3 && random.nextFloat() > storm) return SWELL;
        if (width >= 5 && random.nextFloat() < 0.3f) return STREAKS;
        return CREST;
    }
}
