package net.superkat.wavify.compat;

import net.neoforged.fml.ModList;

public final class FlowMods {

    private static final boolean LOADED = ModList.get().isLoaded("dynamicwaters") || ModList.get().isLoaded("streamsreflowing");

    private FlowMods() {}

    public static boolean isLoaded() {
        return LOADED;
    }
}
