package net.superkat.wavify.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class FlowMods {

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("dynamicwaters") || FabricLoader.getInstance().isModLoaded("streamsreflowing");

    private FlowMods() {}

    public static boolean isLoaded() {
        return LOADED;
    }
}
