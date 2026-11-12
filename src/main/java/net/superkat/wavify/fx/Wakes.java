package net.superkat.wavify.fx;

import net.minecraft.client.render.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;
import net.superkat.wavify.ClientState;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.renderer.WaveRenderer;
import net.superkat.wavify.sprite.WavifySprites;
import net.superkat.wavify.util.FrameWatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class Wakes {

    private static final int LIFE = 80;
    private static final double SPACING = 0.25;
    private static final float FULL = 0.38f;
    private static final float HULL = 0.62f;
    private static final float SPREAD = 0.18f;
    private static final double SWAY = 1.4;

    private record Pt(double x, double y, double z, float nx, float nz, long born, float speed, float dist, int light, boolean gap) {
    }

    private static final class Trail {
        final ArrayDeque<Pt> pts = new ArrayDeque<>();
        double lx = Double.NaN;
        double lz;
        boolean gap = true;
        long seen;
        float dist;
        float foam;
        float spray;
        boolean left;
    }

    private final ClientWorld world;
    private final SurfaceFx fx;
    private final Int2ObjectOpenHashMap<Trail> trails = new Int2ObjectOpenHashMap<>();
    private final List<float[]> line = new ArrayList<>();

    public Wakes(ClientWorld world, SurfaceFx fx) {
        this.world = world;
        this.fx = fx;
    }

    public void tick(ClientPlayerEntity player, long time) {
        float amt = FrameWatch.effects();
        if (!WavifyConfig.boatWakes || amt <= 0f) {
            this.trails.clear();
            return;
        }
        Random r = this.world.getRandom();

        for (Entity e : this.world.getEntities()) {
            if (!(e instanceof AbstractBoatEntity boat) || boat.squaredDistanceTo(player) > 96 * 96) continue;
            Trail t = this.trails.computeIfAbsent(boat.getId(), k -> new Trail());
            t.seen = time;

            double dx = boat.getX() - boat.lastX;
            double dz = boat.getZ() - boat.lastZ;
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            double fx;
            double fz;
            if (speed > 0.01f) {
                fx = dx / speed;
                fz = dz / speed;
            } else {
                float yaw = boat.getYaw() * MathHelper.RADIANS_PER_DEGREE;
                fx = -MathHelper.sin(yaw);
                fz = MathHelper.cos(yaw);
            }
            float nx = (float) -fz;
            float nz = (float) fx;

            double sx = boat.getX() - fx * 0.7;
            double sz = boat.getZ() - fz * 0.7;
            double top = SurfaceFx.top(this.world, sx, sz, MathHelper.floor(boat.getY()));
            if (Double.isNaN(top) || Math.abs(boat.getY() - top) > 0.7) {
                t.gap = true;
                continue;
            }

            if (speed >= 0.03f) lay(t, sx, top, sz, nx, nz, time, speed);

            float rate = MathHelper.clamp(speed / FULL, 0f, 1.3f);
            if (speed < 0.04f) continue;

            t.foam += rate * 2.45f * amt;
            while (t.foam >= 1f) {
                t.foam -= 1f;
                double back = 0.2 + r.nextDouble() * 0.9;
                double side = (r.nextDouble() - 0.5) * 1.1;
                double px = boat.getX() - fx * back + nx * side;
                double pz = boat.getZ() - fz * back + nz * side;
                float vx = (float) (dx * 0.6 + nx * r.nextGaussian() * 0.01);
                float vz = (float) (dz * 0.6 + nz * r.nextGaussian() * 0.01);
                float size = 0.28f + 0.12f * rate + r.nextFloat() * 0.08f;
                this.fx.puff(px, top, pz, vx, vz, size, size * 1.35f, 13 + r.nextInt(10), 0.88f);
            }

            t.spray += rate * rate * 1.26f * amt;
            while (t.spray >= 1f) {
                t.spray -= 1f;
                t.left = !t.left;
                int side = t.left ? 1 : -1;
                double along = -0.3 + r.nextDouble() * 1.0;
                double px = boat.getX() + fx * along + nx * side * 0.66;
                double pz = boat.getZ() + fz * along + nz * side * 0.66;
                float out = 0.04f + 0.05f * rate + r.nextFloat() * 0.02f;
                float vx = (float) (dx * 0.5) + nx * side * out;
                float vz = (float) (dz * 0.5) + nz * side * out;
                float size = 0.22f + 0.1f * rate;
                this.fx.puff(px, top, pz, vx, vz, size, size * 1.6f, 8 + r.nextInt(6), 0.85f);
            }

            if (speed > 0.2f) {
                int n = FrameWatch.roll((speed - 0.17f) * 4.2f * amt, r);
                for (int i = 0; i < n; i++) {
                    int side = r.nextBoolean() ? 1 : -1;
                    double bx = boat.getX() + fx * 1.0 + nx * side * 0.5;
                    double bz = boat.getZ() + fz * 1.0 + nz * side * 0.5;
                    double out = 0.06 + r.nextDouble() * 0.08;
                    this.world.addParticleClient(WavifyParticles.SPLASH_PARTICLE, bx, top + 0.1, bz,
                            nx * side * out + fx * speed * 0.5, 0.1 + r.nextDouble() * 0.12, nz * side * out + fz * speed * 0.5);
                }
            }
        }

        this.trails.values().removeIf(t -> {
            while (!t.pts.isEmpty() && time - t.pts.peekFirst().born() > LIFE) t.pts.pollFirst();
            return t.pts.isEmpty() && time - t.seen > 40;
        });
    }

    private void lay(Trail t, double sx, double top, double sz, float nx, float nz, long time, float speed) {
        if (Double.isNaN(t.lx) || t.gap) {
            t.pts.addLast(new Pt(sx, top + 0.02, sz, nx, nz, time, speed, t.dist, light(sx, top, sz), true));
            t.lx = sx;
            t.lz = sz;
            t.gap = false;
            return;
        }
        double ddx = sx - t.lx;
        double ddz = sz - t.lz;
        double len = Math.sqrt(ddx * ddx + ddz * ddz);
        if (len > 4.0) {
            t.gap = true;
            return;
        }
        int steps = (int) (len / SPACING);
        if (steps <= 0) return;
        steps = Math.min(steps, 8);
        int light = light(sx, top, sz);
        for (int i = 1; i <= steps; i++) {
            double f = i * SPACING / len;
            double px = t.lx + ddx * f;
            double pz = t.lz + ddz * f;
            t.dist += (float) SPACING;
            t.pts.addLast(new Pt(px, top + 0.02, pz, nx, nz, time, speed, t.dist, light, false));
        }
        double f = steps * SPACING / len;
        t.lx += ddx * f;
        t.lz += ddz * f;
    }

    private int light(double x, double y, double z) {
        return WorldRenderer.getLightmapCoordinates(this.world, BlockPos.ofFloored(x, y + 0.5, z));
    }

    public void render(VertexConsumer buffer, Camera camera, long time, float delta) {
        if (this.trails.isEmpty()) return;
        Sprite sprite = ClientState.SPRITES.getSprite(WavifySprites.WAKE_LINE_ID);
        Vec3d cam = camera.getCameraPos();
        float foam = (float) WavifyConfig.foamOpacity;
        float now = time + delta;

        for (Trail t : this.trails.values()) {
            for (int side = -1; side <= 1; side += 2) {
                this.line.clear();
                for (Pt pt : t.pts) {
                    if (pt.gap() && !this.line.isEmpty()) {
                        strip(buffer, sprite);
                        this.line.clear();
                    }
                    float age = now - pt.born();
                    float a = fade(age, pt.speed()) * foam;
                    float sway = 0.14f * Math.min(1f, age / 12f) * MathHelper.clamp(pt.speed() / FULL, 0.3f, 1f)
                            * MathHelper.sin((float) (pt.dist() / SWAY * MathHelper.TAU) - now * 0.22f);
                    float off = side * (HULL + pt.speed() * age * SPREAD + sway);
                    float half = 0.2f + age * 0.004f;
                    this.line.add(new float[]{
                            (float) (pt.x() - cam.x) + pt.nx() * off,
                            (float) (pt.y() - cam.y),
                            (float) (pt.z() - cam.z) + pt.nz() * off,
                            a, half, pt.light()
                    });
                }
                strip(buffer, sprite);
            }
        }
    }

    private void strip(VertexConsumer buffer, Sprite sprite) {
        int n = this.line.size();
        if (n < 2) return;

        List<float[]> pts = new ArrayList<>(n * 2);
        for (int i = 0; i < n - 1; i++) {
            float[] p0 = this.line.get(Math.max(0, i - 1));
            float[] p1 = this.line.get(i);
            float[] p2 = this.line.get(i + 1);
            float[] p3 = this.line.get(Math.min(n - 1, i + 2));
            pts.add(p1);
            pts.add(new float[]{
                    catmull(p0[0], p1[0], p2[0], p3[0]),
                    (p1[1] + p2[1]) * 0.5f,
                    catmull(p0[2], p1[2], p2[2], p3[2]),
                    (p1[3] + p2[3]) * 0.5f,
                    (p1[4] + p2[4]) * 0.5f,
                    p2[5]
            });
        }
        pts.add(this.line.get(n - 1));

        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();
        float[] prevL = null;
        float[] prevR = null;
        float prevA = 0f;
        int prevLight = 0;
        int m = pts.size();
        for (int i = 0; i < m; i++) {
            float[] p = pts.get(i);
            float[] a = pts.get(Math.max(0, i - 1));
            float[] b = pts.get(Math.min(m - 1, i + 1));
            float tx = b[0] - a[0];
            float tz = b[2] - a[2];
            float len = MathHelper.sqrt(tx * tx + tz * tz);
            if (len < 1.0e-5f) continue;
            float px = -tz / len * p[4];
            float pz = tx / len * p[4];
            float[] l = {p[0] - px, p[1], p[2] - pz};
            float[] r = {p[0] + px, p[1], p[2] + pz};
            if (prevL != null && (prevA > 0.01f || p[3] > 0.01f)) {
                WaveRenderer.foam(buffer, prevL[0], prevL[1], prevL[2], u0, v0, prevA, prevLight);
                WaveRenderer.foam(buffer, prevR[0], prevR[1], prevR[2], u1, v0, prevA, prevLight);
                WaveRenderer.foam(buffer, r[0], r[1], r[2], u1, v1, p[3], (int) p[5]);
                WaveRenderer.foam(buffer, l[0], l[1], l[2], u0, v1, p[3], (int) p[5]);
            }
            prevL = l;
            prevR = r;
            prevA = p[3];
            prevLight = (int) p[5];
        }
    }

    private static float catmull(float p0, float p1, float p2, float p3) {
        return (-p0 + 9f * p1 + 9f * p2 - p3) / 16f;
    }

    private static float fade(float age, float speed) {
        if (age >= LIFE) return 0f;
        float left = 1f - age / LIFE;
        return Math.min(1f, age / 3f) * left * (float) Math.sqrt(left) * MathHelper.clamp(speed / 0.25f, 0.35f, 1f) * 0.45f;
    }
}
