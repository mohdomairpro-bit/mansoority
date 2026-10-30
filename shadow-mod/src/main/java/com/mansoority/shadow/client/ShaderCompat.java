package com.mansoority.shadow.client;

import java.lang.reflect.Method;

/** Oculus/Iris, if installed: is this the shadow-map pass? (found by reflection so it isn't a hard dependency) */
final class ShaderCompat {
    private static boolean looked;
    private static Object api;
    private static Method shadow;

    static boolean shadowPass() {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                shadow = c.getMethod("isRenderingShadowPass");
            } catch (Throwable t) { api = null; }
        }
        if (api == null) return false;
        try { return (Boolean) shadow.invoke(api); } catch (Throwable t) { return false; }
    }

    private ShaderCompat() {}
}
