package net.superkat.wavify.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class DynamicWatersCompat {

    public static final String MOD_ID = "dynamicwaters";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private DynamicWatersCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }
}
