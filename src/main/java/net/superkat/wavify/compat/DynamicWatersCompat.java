package net.superkat.wavify.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.loader.api.FabricLoader;

public final class DynamicWatersCompat {

    public static final String MOD_ID = "dynamicwaters";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private DynamicWatersCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    public static Vec3 getRiverFlow(double x, int y, double z, Level level) {
        if (!LOADED) return Vec3.ZERO;
        BlockPos pos = new BlockPos(Mth.floor(x), y, Mth.floor(z));
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) return Vec3.ZERO;
        return fluid.getFlow(level, pos);
    }
}
