package net.superkat.wavify.fx;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.util.FrameWatch;

public class Falls {

    private static final int R = 24;
    private static final int SIDE = R * 2 + 1;
    private static final int PER_TICK = 160;

    private record Spot(int x, double y, int z, int drop, boolean pool) {
    }

    private final ClientWorld world;
    private final SurfaceFx fx;
    private final Long2ObjectOpenHashMap<Spot> spots = new Long2ObjectOpenHashMap<>();
    private int cursor;
    private int cx;
    private int cz;

    public Falls(ClientWorld world, SurfaceFx fx) {
        this.world = world;
        this.fx = fx;
    }

    public void tick(ClientPlayerEntity player, long time) {
        if (!WavifyConfig.waterfallMist) {
            this.spots.clear();
            return;
        }

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int i = 0; i < PER_TICK; i++) {
            if (this.cursor == 0) {
                this.cx = MathHelper.floor(player.getX());
                this.cz = MathHelper.floor(player.getZ());
                int far = (R + 8) * (R + 8);
                this.spots.values().removeIf(s -> MathHelper.square(s.x() - this.cx) + MathHelper.square(s.z() - this.cz) > far);
            }
            probe(this.cx - R + this.cursor % SIDE, this.cz - R + this.cursor / SIDE, m);
            this.cursor = (this.cursor + 1) % (SIDE * SIDE);
        }

        if (this.spots.isEmpty()) return;
        float amt = FrameWatch.effects();
        if (amt <= 0f) return;
        float share = Math.min(1f, 24f / this.spots.size());
        Random r = this.world.getRandom();
        for (Spot s : this.spots.values()) {
            if (player.squaredDistanceTo(s.x() + 0.5, s.y(), s.z() + 0.5) > 40 * 40) continue;
            mist(s, amt * share, r);
        }
    }

    private void probe(int x, int z, BlockPos.Mutable m) {
        long key = ChunkPos.toLong(x, z);
        int h = this.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        m.set(x, h - 1, z);
        if (!this.world.getFluidState(m).isIn(FluidTags.WATER)) {
            this.spots.remove(key);
            return;
        }

        int drop = 0;
        FluidState below = null;
        for (int i = 0; i < 96; i++) {
            m.move(0, -1, 0);
            below = this.world.getFluidState(m);
            if (!below.isIn(FluidTags.WATER) || !below.getOrEmpty(FlowableFluid.FALLING).orElse(false)) break;
            drop++;
        }
        if (drop < 2 || below == null) {
            this.spots.remove(key);
            return;
        }

        boolean pool = below.isIn(FluidTags.WATER);
        double y = pool ? m.getY() + below.getHeight(this.world, m) : m.getY() + 1;
        this.spots.put(key, new Spot(x, y, z, drop, pool));
    }

    private void mist(Spot s, float amt, Random r) {
        float power = Math.min(s.drop(), 20) / 20f;
        double x = s.x() + 0.5;
        double z = s.z() + 0.5;

        int n = FrameWatch.roll((0.15f + power * 0.7f) * amt, r);
        for (int i = 0; i < n; i++) {
            this.world.addParticleClient(WavifyParticles.SPLASH_PARTICLE,
                    x + r.nextGaussian() * 0.3, s.y() + 0.1, z + r.nextGaussian() * 0.3,
                    r.nextGaussian() * 0.08, 0.08 + r.nextDouble() * (0.06 + 0.16 * power), r.nextGaussian() * 0.08);
        }
        if (r.nextFloat() < 0.22f * amt * (0.4f + power)) {
            this.world.addParticleClient(ParticleTypes.POOF,
                    x + r.nextGaussian() * 0.45, s.y() + 0.2 + r.nextDouble() * 0.6, z + r.nextGaussian() * 0.45,
                    r.nextGaussian() * 0.015, 0.02 + r.nextDouble() * 0.03, r.nextGaussian() * 0.015);
        }
        if (s.pool() && r.nextFloat() < 0.14f * amt) {
            this.fx.ring(x, s.y(), z, 0.5f, 1.4f + power * 2f, 32, 0.65f, 0);
        }
    }
}
