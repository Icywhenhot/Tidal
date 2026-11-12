package net.superkat.wavify.wave;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.river.RiverFlowField;
import net.superkat.wavify.scan.WaterHandler;
import net.superkat.wavify.util.FrameWatch;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RiverSpawner {

    private static final int BANK_MARGIN = 1;

    private final ClientWorld world;
    private final WaterHandler water;
    private final RiverFlowField field = new RiverFlowField();

    private record Spot(double x, double z, double spacing) {
    }

    public RiverSpawner(ClientWorld world, WaterHandler water) {
        this.world = world;
        this.water = water;
    }

    public void tick(List<Wave> waves, @Nullable ClientPlayerEntity player, long time, float storm) {
        if (player == null || !WavifyConfig.enableRiverWaves) {
            return;
        }

        this.field.ensureBuilt(this.world, this.water.riverWaters, player.getX(), player.getZ(), time);
        if (time % 10 == 0) spawn(waves, player, storm);
    }

    private void spawn(List<Wave> waves, ClientPlayerEntity player, float storm) {
        List<BlockPos> candidates = findCandidates(player);
        if (candidates.isEmpty()) {
            return;
        }

        List<Spot> taken = new ArrayList<>();
        double used = 0;
        for (Wave wave : waves) {
            if (wave instanceof RiverWave rw) {
                taken.add(new Spot(rw.x, rw.z, rw.style.spacing()));
                used += 1.0 / rw.style.density;
            }
        }

        double budget = Math.max(1.0, candidates.size() / 100.0 * WavifyConfig.riverWaveDensity * (1.0 + storm) * FrameWatch.scale());
        int cap = WavifyConfig.riverWaveStyle == RiverStyle.STREAKS || WavifyConfig.riverWaveStyle == RiverStyle.AUTO ? 96 : 48;
        if (used >= budget || taken.size() >= cap) {
            return;
        }

        Random random = this.world.getRandom();
        int attempts = 0;
        double goal = Math.min(budget, used + Math.max(0.5, (budget - used) * (0.15 + random.nextDouble() * 0.2)));
        int maxAttempts = (int) ((goal - used) * 30) + 16;

        while (used < goal && taken.size() < cap && attempts++ < maxAttempts) {
            BlockPos w = candidates.get(random.nextInt(candidates.size()));
            double cx = w.getX() + 0.5;
            double cz = w.getZ() + 0.5;

            RiverFlow.Flow flow = RiverFlow.dynamicFlowAt(this.world, cx, w.getY(), cz);
            boolean dynamic = flow != null;
            if (flow == null) {
                flow = this.field.flowAt(cx, cz);
                if (flow == null) continue;
                if (RiverFlow.banksAt(this.world, cx, cz, w.getY(), flow.dirX(), flow.dirZ(), 4).clearance() < BANK_MARGIN) continue;
            }

            RiverStyle style = WavifyConfig.riverWaveStyle;
            if (style == RiverStyle.AUTO) {
                int width = RiverFlow.banksAt(this.world, cx, cz, w.getY(), flow.dirX(), flow.dirZ(), 5).width();
                int depth = RiverFlow.depthAt(this.world, w, 4);
                style = RiverStyle.pick(width, depth, dynamic, storm, random);
            }
            if (tooClose(taken, cx, cz, style.spacing())) continue;

            waves.add(new RiverWave(this.world, w, this.field, style, flow.dirX(), flow.dirZ(), (float) WavifyConfig.riverWaveTravelBlocks, storm));
            taken.add(new Spot(cx, cz, style.spacing()));
            used += 1.0 / style.density;
        }

    }

    private List<BlockPos> findCandidates(ClientPlayerEntity player) {
        double px = player.getX();
        double pz = player.getZ();
        ChunkPos playerChunk = player.getChunkPos();

        double radius = WavifyConfig.riverWaveSpawnRadius;
        double radiusSq = radius * radius;
        int chunkRadius = MathHelper.ceil(radius / 16.0);

        List<BlockPos> candidates = new ArrayList<>();

        for (int cdx = -chunkRadius; cdx <= chunkRadius; cdx++) {
            for (int cdz = -chunkRadius; cdz <= chunkRadius; cdz++) {
                long key = new ChunkPos(playerChunk.x + cdx, playerChunk.z + cdz).toLong();
                Set<BlockPos> waters = this.water.waters.get(key);
                if (waters == null || waters.isEmpty()) continue;

                for (BlockPos w : waters) {
                    double dx = w.getX() + 0.5 - px;
                    double dz = w.getZ() + 0.5 - pz;
                    if (dx * dx + dz * dz > radiusSq) continue;

                    if (!isDynamicRiver(w)) {
                        if (!RiverFlow.isRiverWater(this.world, w)) continue;
                        if (this.field.isOceanSurroundedRiver(this.world, w)) continue;
                    }
                    if (!RiverFlow.isSurfaceWater(this.world, w)) continue;
                    candidates.add(w);
                }
            }
        }

        return candidates;
    }

    private boolean isDynamicRiver(BlockPos pos) {
        return RiverFlow.dynamicFlowAt(this.world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) != null;
    }

    private boolean tooClose(List<Spot> taken, double x, double z, double spacing) {
        for (Spot spot : taken) {
            double dx = spot.x() - x;
            double dz = spot.z() - z;
            double r = Math.min(spacing, spot.spacing());
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }
}
