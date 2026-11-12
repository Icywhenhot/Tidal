package net.superkat.wavify.compat;

import net.neoforged.fml.ModList;
import net.superkat.wavify.config.WavifyConfig;

import java.lang.reflect.Method;
import java.util.Locale;

public final class IrisCompat {
    private static final boolean IRIS_LOADED =
            ModList.get().isLoaded("iris")
            || ModList.get().isLoaded("oculus");

    private static final String[] PACKS = {"rethinking", "complementary", "bsl", "sildur", "makeup", "bliss", "photon", "solas"};
    private static final float[] SINKS = {-0.25f, -0.20f, -0.30f, -0.15f, -0.25f, -0.25f, -0.25f, -0.25f};

    private static Method isShaderPackInUseMethod;
    private static Object irisApiInstance;
    private static boolean lookupAttempted = false;
    private static boolean lookupFailed = false;

    private static Method packNameMethod;
    private static boolean packLookupFailed = false;
    private static String packName = "";
    private static long packCheckedAt = 0L;

    private IrisCompat() {}

    public static boolean isShaderPackActive() {
        if (!IRIS_LOADED || lookupFailed) return false;
        if (!lookupAttempted) {
            lookupAttempted = true;
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Method getInstance = apiClass.getMethod("getInstance");
                irisApiInstance = getInstance.invoke(null);
                isShaderPackInUseMethod = apiClass.getMethod("isShaderPackInUse");
            } catch (Throwable t) {
                lookupFailed = true;
                return false;
            }
        }
        try {
            return (boolean) isShaderPackInUseMethod.invoke(irisApiInstance);
        } catch (Throwable t) {
            lookupFailed = true;
            return false;
        }
    }

    public static float waveSink() {
        if (!isShaderPackActive()) return 0f;
        if (WavifyConfig.autoShaderOffsets) {
            String name = packName();
            for (int i = 0; i < PACKS.length; i++) {
                if (name.contains(PACKS[i])) return SINKS[i];
            }
        }
        return (float) WavifyConfig.shaderWaveYSink;
    }

    public static String packName() {
        if (packLookupFailed) return "";
        long now = System.currentTimeMillis();
        if (now - packCheckedAt < 2000L) return packName;
        packCheckedAt = now;
        try {
            if (packNameMethod == null) {
                packNameMethod = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
            }
            Object raw = packNameMethod.invoke(null);
            packName = raw == null ? "" : raw.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            packLookupFailed = true;
            packName = "";
        }
        return packName;
    }
}
