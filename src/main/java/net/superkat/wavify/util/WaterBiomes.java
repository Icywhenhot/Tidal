package net.superkat.wavify.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;

public final class WaterBiomes {

    private WaterBiomes() {
    }

    public static boolean isRiver(RegistryEntry<Biome> biome) {
        return biome.isIn(BiomeTags.IS_RIVER) || biome.isIn(ConventionalBiomeTags.IS_RIVER);
    }

    public static boolean isOcean(RegistryEntry<Biome> biome) {
        return biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(ConventionalBiomeTags.IS_OCEAN);
    }

    public static boolean isFrozen(World world, BlockPos pos) {
        return world.getBiome(pos).value().getTemperature() < 0.15f;
    }
}
