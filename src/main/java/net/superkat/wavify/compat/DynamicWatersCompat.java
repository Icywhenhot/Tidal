package net.superkat.wavify.compat;

import net.minecraftforge.fml.ModList;

public final class DynamicWatersCompat {

    public static final String MOD_ID = "dynamicwaters";

    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private DynamicWatersCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }
}
