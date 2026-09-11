package net.superkat.wavify.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final boolean IRIS_LOADED =
            FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    private static Method isShaderPackInUseMethod;
    private static Object irisApiInstance;
    private static boolean lookupAttempted = false;
    private static boolean lookupFailed = false;

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
}
