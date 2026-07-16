package com.mamiyaotaru.voxelmap.integration;

public final class AutoFlyHelper {

    private static Boolean present;

    private AutoFlyHelper() {
    }

    public static boolean isPresent() {
        if (present == null) {
            try {
                Class.forName("dev.autofly.AutoFly");
                present = Boolean.TRUE;
            } catch (Throwable t) {
                present = Boolean.FALSE;
            }
        }
        return present;
    }

    public static boolean flyTo(int x, int z) {
        if (!isPresent()) {
            return false;
        }
        try {
            Class.forName("dev.autofly.AutoFly")
                    .getMethod("flyTo", int.class, int.class)
                    .invoke(null, x, z);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
