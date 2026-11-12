package net.superkat.wavify.fx;

import net.minecraft.util.math.MathHelper;

public class Decal {
    static final int RING = 0;
    static final int SLUSH = 1;
    static final int PUFF = 2;

    final int kind;
    final int variant;
    double x;
    double y;
    double z;
    double prevX;
    double prevZ;
    float vx;
    float vz;
    float drag = 1f;
    float spin;
    int age;
    int life;
    final float from;
    final float to;
    final float peak;
    float angle;
    int light;

    Decal(int kind, int variant, double x, double y, double z, float from, float to, int life, float peak, float angle, int light) {
        this.kind = kind;
        this.variant = variant;
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = x;
        this.prevZ = z;
        this.from = from;
        this.to = to;
        this.life = life;
        this.peak = peak;
        this.angle = angle;
        this.light = light;
    }

    boolean tick() {
        this.prevX = this.x;
        this.prevZ = this.z;
        this.x += this.vx;
        this.z += this.vz;
        this.vx *= this.drag;
        this.vz *= this.drag;
        this.angle += this.spin;
        return ++this.age < this.life;
    }

    float size(float t) {
        if (this.kind == SLUSH) return this.from;
        float e = 1f - (1f - t) * (1f - t);
        return MathHelper.lerp(e, this.from, this.to);
    }

    float alpha(float age) {
        if (age < 0f) return 0f;
        float t = age / this.life;
        if (this.kind == SLUSH) {
            return this.peak * Math.min(1f, age / 20f) * MathHelper.clamp((this.life - age) / 40f, 0f, 1f);
        }
        if (this.kind == PUFF) {
            return this.peak * Math.min(1f, age / 1.5f) * MathHelper.clamp((1f - t) / 0.35f, 0f, 1f);
        }
        float left = 1f - t;
        return this.peak * Math.min(1f, age / 2f) * left * (float) Math.sqrt(left);
    }
}
