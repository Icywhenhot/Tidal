package net.superkat.wavify.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

public final class WaterBiomes {

    private WaterBiomes() {
    }

    public static boolean isRiver(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_RIVER) || biome.is(Tags.Biomes.IS_RIVER);
    }

    public static boolean isOcean(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(Tags.Biomes.IS_OCEAN);
    }

    public static boolean isFrozen(Level level, BlockPos pos) {
        return level.getBiome(pos).value().getBaseTemperature() < 0.15f;
    }
}
