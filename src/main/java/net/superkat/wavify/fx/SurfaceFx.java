package net.superkat.wavify.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.superkat.wavify.ClientState;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.renderer.WaveRenderer;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.sprite.WaveSprite;
import net.superkat.wavify.sprite.WavifySprites;
import net.superkat.wavify.util.FrameWatch;
import net.superkat.wavify.util.WaterBiomes;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public class SurfaceFx {

    private static final int MAX_DECALS = 2400;

    private final ClientLevel level;
    private final List<Decal> decals = new ObjectArrayList<>();
    private final Int2LongOpenHashMap atSurface = new Int2LongOpenHashMap();
    private int slushCount;

    public final Wakes wakes;
    public final Falls falls;

    public SurfaceFx(ClientLevel level) {
        this.level = level;
        this.wakes = new Wakes(level, this);
        this.falls = new Falls(level, this);
        this.atSurface.defaultReturnValue(Long.MIN_VALUE);
    }

    public static double top(ClientLevel level, double x, double z, int preferredY) {
        int y = RiverFlow.surfaceWaterY(level, x, z, preferredY);
        if (y == Integer.MIN_VALUE) return Double.NaN;
        BlockPos pos = BlockPos.containing(x, y, z);
        return y + level.getFluidState(pos).getHeight(level, pos);
    }

    public void tick(LocalPlayer player, long time) {
        this.slushCount = 0;
        for (Iterator<Decal> it = this.decals.iterator(); it.hasNext(); ) {
            Decal d = it.next();
            if (!d.tick()) {
                it.remove();
                continue;
            }
            if (d.kind == Decal.SLUSH) {
                this.slushCount++;
                if ((d.age + d.variant) % 10 == 0) settle(d);
            }
        }

        this.wakes.tick(player, time);
        this.falls.tick(player, time);
        rain(player);
        entities(player, time);
        slush(player, time);
    }

    public void ring(double x, double y, double z, float from, float to, int life, float peak, int delay) {
        if (this.decals.size() >= MAX_DECALS) return;
        float angle = this.level.getRandom().nextFloat() * Mth.TWO_PI;
        Decal d = new Decal(Decal.RING, 0, x, y + 0.015, z, from, to, life, peak, angle, light(x, y, z));
        d.age = -delay;
        this.decals.add(d);
    }

    public void slushPiece(double x, double y, double z, float vx, float vz) {
        if (this.decals.size() >= MAX_DECALS) return;
        RandomSource r = this.level.getRandom();
        float size = 0.18f + r.nextFloat() * 0.22f;
        Decal d = new Decal(Decal.SLUSH, r.nextInt(3), x, y + 0.012, z, size, size, 500 + r.nextInt(500), 0.95f, r.nextFloat() * Mth.TWO_PI, light(x, y, z));
        d.vx = vx;
        d.vz = vz;
        this.decals.add(d);
    }

    public void puff(double x, double y, double z, float vx, float vz, float from, float to, int life, float drag) {
        if (this.decals.size() >= MAX_DECALS) return;
        RandomSource r = this.level.getRandom();
        Decal d = new Decal(Decal.PUFF, 0, x, y + 0.02 + r.nextFloat() * 0.01, z, from, to, life, 0.48f, r.nextFloat() * Mth.TWO_PI, light(x, y, z));
        d.vx = vx;
        d.vz = vz;
        d.drag = drag;
        d.spin = (float) r.nextGaussian() * 0.04f;
        this.decals.add(d);
    }

    private int light(double x, double y, double z) {
        return LevelRenderer.getLightColor(this.level, BlockPos.containing(x, y + 0.5, z));
    }

    private void settle(Decal d) {
        double top = top(this.level, d.x, d.z, Mth.floor(d.y));
        if (Double.isNaN(top) || Math.abs(top - d.y) > 0.3) {
            d.vx = 0;
            d.vz = 0;
            d.life = Math.min(d.life, d.age + 40);
            return;
        }
        d.light = light(d.x, d.y, d.z);
    }

    private void rain(LocalPlayer player) {
        if (!WavifyConfig.rainRipples) return;
        float rain = this.level.getRainLevel(1f);
        if (rain < 0.2f) return;
        RandomSource r = this.level.getRandom();
        int count = FrameWatch.roll(rain * (18f + 12f * this.level.getThunderLevel(1f)) * FrameWatch.effects(), r);
        int px = Mth.floor(player.getX());
        int pz = Mth.floor(player.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            int x = px + r.nextInt(49) - 24;
            int z = pz + r.nextInt(49) - 24;
            FluidState fluid = openWater(x, z, pos);
            if (fluid == null) continue;
            if (!this.level.isRainingAt(pos.above())) continue;
            double y = pos.getY() + fluid.getHeight(this.level, pos);
            ring(x + r.nextDouble(), y, z + r.nextDouble(), 0.08f, 0.3f + r.nextFloat() * 0.3f, 10 + r.nextInt(7), 0.55f, 0);
        }
    }

    @Nullable
    private FluidState openWater(int x, int z, BlockPos.MutableBlockPos out) {
        int h = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        out.set(x, h - 1, z);
        FluidState fluid = this.level.getFluidState(out);
        if (!fluid.is(FluidTags.WATER)) return null;
        if (!this.level.getBlockState(out.above()).isAir()) return null;
        return fluid;
    }

    private void entities(LocalPlayer player, long time) {
        if (!WavifyConfig.entityRipples) {
            this.atSurface.clear();
            return;
        }
        float amt = FrameWatch.effects();
        RandomSource r = this.level.getRandom();

        for (Entity e : this.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e.isPassenger() || e.isSpectator()) continue;
            if (e.distanceToSqr(player) > 48 * 48) continue;

            double top = top(this.level, e.getX(), e.getZ(), Mth.floor(e.getY()));
            if (Double.isNaN(top) || e.getY() > top + 0.05 || e.getBoundingBox().maxY < top + 0.1) continue;

            boolean was = this.atSurface.get(e.getId()) >= time - 2;
            this.atSurface.put(e.getId(), time);
            if (amt <= 0f) continue;

            double dx = e.getX() - e.xo;
            double dz = e.getZ() - e.zo;
            double dy = e.getY() - e.yo;
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            float w = e.getBbWidth();

            if (!was && dy < -0.2) {
                float reach = w + 1.4f + (float) Math.min(2.0, -dy * 2.5);
                for (int i = 0; i < 3; i++) {
                    ring(e.getX(), top, e.getZ(), w * 0.7f, reach - i * 0.5f, 28, 0.8f, i * 5);
                }
            } else if (speed > 0.03f) {
                if ((time + e.getId()) % 3 == 0 && r.nextFloat() < amt) {
                    ring(e.getX(), top, e.getZ(), w * 0.7f, w * 0.7f + 0.7f + speed * 6f, 22, 0.6f, 0);
                }
            } else if (r.nextFloat() < 0.025f * amt) {
                ring(e.getX(), top, e.getZ(), w * 0.6f, w + 0.6f, 30, 0.35f, 0);
            }
        }

        if (time % 100 == 0) this.atSurface.int2LongEntrySet().removeIf(en -> en.getLongValue() < time - 100);
    }

    private void slush(LocalPlayer player, long time) {
        if (!WavifyConfig.iceAndSlush || time % 5 != 0) return;
        float amt = FrameWatch.effects();
        if (this.slushCount > 260 * amt) return;
        RandomSource r = this.level.getRandom();
        int count = FrameWatch.roll(3f * amt, r);
        int px = Mth.floor(player.getX());
        int pz = Mth.floor(player.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            int x = px + r.nextInt(81) - 40;
            int z = pz + r.nextInt(81) - 40;
            FluidState fluid = openWater(x, z, pos);
            if (fluid == null || !WaterBiomes.isFrozen(this.level, pos)) continue;
            double y = pos.getY() + fluid.getHeight(this.level, pos);
            float a = r.nextFloat() * Mth.TWO_PI;
            float s = 0.003f + r.nextFloat() * 0.006f;
            slushPiece(x + r.nextDouble(), y, z + r.nextDouble(), Mth.cos(a) * s, Mth.sin(a) * s);
        }
    }

    public void slushSurf(BlockPos spawn, float yaw) {
        if (!WavifyConfig.iceAndSlush) return;
        float amt = FrameWatch.effects();
        if (this.slushCount > 400 * amt) return;
        RandomSource r = this.level.getRandom();
        int count = FrameWatch.roll(4f * amt, r);
        this.slushCount += count;
        double top = top(this.level, spawn.getX() + 0.5, spawn.getZ() + 0.5, spawn.getY());
        if (Double.isNaN(top)) return;
        float dx = Mth.cos(yaw * Mth.DEG_TO_RAD);
        float dz = Mth.sin(yaw * Mth.DEG_TO_RAD);
        for (int i = 0; i < count; i++) {
            float s = 0.012f + r.nextFloat() * 0.01f;
            slushPiece(spawn.getX() + 0.5 + r.nextGaussian(), top, spawn.getZ() + 0.5 + r.nextGaussian(), dx * s, dz * s);
        }
    }

    public void render(VertexConsumer buffer, Camera camera, float delta) {
        long time = this.level.getGameTime();
        this.wakes.render(buffer, camera, time, delta);
        if (this.decals.isEmpty()) return;

        TextureAtlasSprite ring = ClientState.SPRITES.getSprite(WavifySprites.RIPPLE_ID);
        WaveSprite puff = ClientState.SPRITES.getWaveSprite(WavifySprites.FOAM_PUFF_ID);
        TextureAtlasSprite[] slush = new TextureAtlasSprite[WavifySprites.SLUSH_IDS.length];
        for (int i = 0; i < slush.length; i++) slush[i] = ClientState.SPRITES.getSprite(WavifySprites.SLUSH_IDS[i]);

        Vec3 cam = camera.getPosition();
        float foam = (float) WavifyConfig.foamOpacity;
        for (Decal d : this.decals) {
            float age = d.age + delta;
            float a = d.alpha(age) * foam;
            if (a <= 0.01f) continue;
            float size = d.size(age / d.life);
            float u0;
            float u1;
            float v0;
            float v1;
            if (d.kind == Decal.PUFF) {
                WaveSprite.Uv uv = puff.uvAt(d.age, d.life);
                u0 = uv.u0();
                u1 = uv.u1();
                v0 = uv.v0();
                v1 = uv.v1();
            } else {
                TextureAtlasSprite sprite = d.kind == Decal.SLUSH ? slush[d.variant] : ring;
                u0 = sprite.getU0();
                u1 = sprite.getU1();
                v0 = sprite.getV0();
                v1 = sprite.getV1();
            }

            float x = (float) (Mth.lerp(delta, d.prevX, d.x) - cam.x);
            float y = (float) (d.y - cam.y);
            float z = (float) (Mth.lerp(delta, d.prevZ, d.z) - cam.z);
            if (d.kind == Decal.SLUSH) y += Mth.sin((age + d.variant * 17) * 0.08f) * 0.012f;

            float angle = d.angle + d.spin * delta;
            float c = Mth.cos(angle) * size;
            float s = Mth.sin(angle) * size;
            WaveRenderer.foam(buffer, x - c + s, y, z - s - c, u0, v0, a, d.light);
            WaveRenderer.foam(buffer, x - c - s, y, z - s + c, u0, v1, a, d.light);
            WaveRenderer.foam(buffer, x + c - s, y, z + s + c, u1, v1, a, d.light);
            WaveRenderer.foam(buffer, x + c + s, y, z + s - c, u1, v0, a, d.light);
        }
    }
}
