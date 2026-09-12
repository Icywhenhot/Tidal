package net.superkat.wavify.wave;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.river.RiverFlowField;
import net.superkat.wavify.scan.WaterHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RiverSpawner {

    private static final double MIN_SPACING_SQ = 5.0 * 5.0;

    private static final int BANK_MARGIN = 1;

    private final ClientLevel level;
    private final WaterHandler water;
    private final RiverFlowField field = new RiverFlowField();

    private record Spot(double x, double z) {
    }

    public RiverSpawner(ClientLevel level, WaterHandler water) {
        this.level = level;
        this.water = water;
    }

    public void tick(List<Wave> waves, @Nullable LocalPlayer player, long time) {
        if (player == null || !WavifyConfig.enableRiverWaves) {
            return;
        }

        this.field.ensureBuilt(this.water.riverWaters, player.getX(), player.getZ(), time);
        if (time % 40 == 0) spawn(waves, player);
    }

    private void spawn(List<Wave> waves, LocalPlayer player) {
        List<BlockPos> candidates = findCandidates(player);
        if (candidates.isEmpty()) {
            return;
        }

        List<Spot> taken = new ArrayList<>();
        for (Wave wave : waves) {
            if (wave instanceof RiverWave riverWave) taken.add(new Spot(riverWave.x, riverWave.z));
        }

        int target = (int) Math.round(candidates.size() / 100.0 * WavifyConfig.riverWaveDensity);
        target = Mth.clamp(target, 1, 48);
        int toSpawn = target - taken.size();
        if (toSpawn <= 0) {
            return;
        }

        RandomSource random = this.level.getRandom();
        int spawned = 0;
        int attempts = 0;
        int maxAttempts = toSpawn * 8 + 16;

        while (spawned < toSpawn && attempts++ < maxAttempts) {
            BlockPos w = candidates.get(random.nextInt(candidates.size()));
            double cx = w.getX() + 0.5;
            double cz = w.getZ() + 0.5;
            if (tooClose(taken, cx, cz)) continue;

            RiverFlow.Flow flow = RiverFlow.dynamicFlowAt(this.level, cx, w.getY(), cz);
            if (flow == null) {
                flow = this.field.flowAt(cx, cz);
                if (flow == null) continue;
                if (RiverFlow.banksAt(this.level, cx, cz, w.getY(), flow.dirX(), flow.dirZ(), 4).clearance() < BANK_MARGIN) continue;
            }

            waves.add(new RiverWave(this.level, w, this.field, flow.dirX(), flow.dirZ(), (float) WavifyConfig.riverWaveTravelBlocks));
            taken.add(new Spot(cx, cz));
            spawned++;
        }

    }

    private List<BlockPos> findCandidates(LocalPlayer player) {
        double px = player.getX();
        double pz = player.getZ();
        ChunkPos playerChunk = player.chunkPosition();

        double radius = WavifyConfig.riverWaveSpawnRadius;
        double radiusSq = radius * radius;
        int chunkRadius = Mth.ceil(radius / 16.0);

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
                        if (!RiverFlow.isRiverWater(this.level, w)) continue;
                        if (this.field.isOceanSurroundedRiver(this.level, w)) continue;
                    }
                    if (!RiverFlow.isSurfaceWater(this.level, w)) continue;
                    candidates.add(w);
                }
            }
        }

        return candidates;
    }

    private boolean isDynamicRiver(BlockPos pos) {
        return RiverFlow.dynamicFlowAt(this.level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) != null;
    }

    private boolean tooClose(List<Spot> taken, double x, double z) {
        for (Spot spot : taken) {
            double dx = spot.x() - x;
            double dz = spot.z() - z;
            if (dx * dx + dz * dz <= MIN_SPACING_SQ) return true;
        }
        return false;
    }

}
