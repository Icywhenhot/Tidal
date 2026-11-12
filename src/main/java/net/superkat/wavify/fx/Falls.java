package net.superkat.wavify.fx;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.util.FrameWatch;

public class Falls {

    private static final int R = 24;
    private static final int SIDE = R * 2 + 1;
    private static final int PER_TICK = 160;

    private record Spot(int x, double y, int z, int drop, boolean pool) {
    }

    private final ClientLevel level;
    private final SurfaceFx fx;
    private final Long2ObjectOpenHashMap<Spot> spots = new Long2ObjectOpenHashMap<>();
    private int cursor;
    private int cx;
    private int cz;

    public Falls(ClientLevel level, SurfaceFx fx) {
        this.level = level;
        this.fx = fx;
    }

    public void tick(LocalPlayer player, long time) {
        if (!WavifyConfig.waterfallMist) {
            this.spots.clear();
            return;
        }

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < PER_TICK; i++) {
            if (this.cursor == 0) {
                this.cx = Mth.floor(player.getX());
                this.cz = Mth.floor(player.getZ());
                int far = (R + 8) * (R + 8);
                this.spots.values().removeIf(s -> Mth.square(s.x() - this.cx) + Mth.square(s.z() - this.cz) > far);
            }
            probe(this.cx - R + this.cursor % SIDE, this.cz - R + this.cursor / SIDE, m);
            this.cursor = (this.cursor + 1) % (SIDE * SIDE);
        }

        if (this.spots.isEmpty()) return;
        float amt = FrameWatch.effects();
        if (amt <= 0f) return;
        float share = Math.min(1f, 24f / this.spots.size());
        RandomSource r = this.level.getRandom();
        for (Spot s : this.spots.values()) {
            if (player.distanceToSqr(s.x() + 0.5, s.y(), s.z() + 0.5) > 40 * 40) continue;
            mist(s, amt * share, r);
        }
    }

    private void probe(int x, int z, BlockPos.MutableBlockPos m) {
        long key = ChunkPos.asLong(x, z);
        int h = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        m.set(x, h - 1, z);
        if (!this.level.getFluidState(m).is(FluidTags.WATER)) {
            this.spots.remove(key);
            return;
        }

        int drop = 0;
        FluidState below = null;
        for (int i = 0; i < 96; i++) {
            m.move(0, -1, 0);
            below = this.level.getFluidState(m);
            if (!below.is(FluidTags.WATER) || !below.getOptionalValue(FlowingFluid.FALLING).orElse(false)) break;
            drop++;
        }
        if (drop < 2 || below == null) {
            this.spots.remove(key);
            return;
        }

        boolean pool = below.is(FluidTags.WATER);
        double y = pool ? m.getY() + below.getHeight(this.level, m) : m.getY() + 1;
        this.spots.put(key, new Spot(x, y, z, drop, pool));
    }

    private void mist(Spot s, float amt, RandomSource r) {
        float power = Math.min(s.drop(), 20) / 20f;
        double x = s.x() + 0.5;
        double z = s.z() + 0.5;

        int n = FrameWatch.roll((0.15f + power * 0.7f) * amt, r);
        for (int i = 0; i < n; i++) {
            this.level.addParticle(WavifyParticles.SPLASH_PARTICLE.get(),
                    x + r.nextGaussian() * 0.3, s.y() + 0.1, z + r.nextGaussian() * 0.3,
                    r.nextGaussian() * 0.08, 0.08 + r.nextDouble() * (0.06 + 0.16 * power), r.nextGaussian() * 0.08);
        }
        if (r.nextFloat() < 0.22f * amt * (0.4f + power)) {
            this.level.addParticle(ParticleTypes.POOF,
                    x + r.nextGaussian() * 0.45, s.y() + 0.2 + r.nextDouble() * 0.6, z + r.nextGaussian() * 0.45,
                    r.nextGaussian() * 0.015, 0.02 + r.nextDouble() * 0.03, r.nextGaussian() * 0.015);
        }
        if (s.pool() && r.nextFloat() < 0.14f * amt) {
            this.fx.ring(x, s.y(), z, 0.5f, 1.4f + power * 2f, 32, 0.65f, 0);
        }
    }
}
