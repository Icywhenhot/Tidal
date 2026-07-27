package net.superkat.wavify.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Optional integration with <b>Dynamic Waters: Realistic Flowing Rivers</b> ({@code dynamicwaters}), which
 * carves flowing rivers through the world. When it is installed, Wavify's river waves follow the direction
 * it flows.
 * <br><br>
 * There is <b>no compile or runtime dependency</b> on Dynamic Waters and <b>no reflection</b>: its river
 * fluid overrides the vanilla {@link FluidState#getFlow} to return the carved flow direction, so we simply
 * call that vanilla method and virtual dispatch lands in its implementation when the mod is present. The
 * flow is baked into the fluid state (not recomputed from server-only worldgen), so this is authoritative
 * on the client. When the mod is absent, this class is inert and Wavify behaves exactly as before.
 */
public final class DynamicWatersCompat {

    public static final String MOD_ID = "dynamicwaters";

    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private DynamicWatersCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * The carved flow vector at a position, or {@link Vec3#ZERO} when Dynamic Waters is absent or the block
     * is not flowing river water.
     */
    public static Vec3 getRiverFlow(double x, int y, double z, Level level) {
        if (!LOADED) return Vec3.ZERO;
        BlockPos pos = new BlockPos(Mth.floor(x), y, Mth.floor(z));
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) return Vec3.ZERO;
        return fluid.getFlow(level, pos);
    }
}
