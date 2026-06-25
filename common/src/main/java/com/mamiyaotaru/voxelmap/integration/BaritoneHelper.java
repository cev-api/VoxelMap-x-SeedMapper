package com.mamiyaotaru.voxelmap.integration;

public final class BaritoneHelper {

    private static Boolean present;

    private BaritoneHelper() {
    }

    public static boolean isPresent() {
        if (present == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                present = Boolean.TRUE;
            } catch (Throwable t) {
                present = Boolean.FALSE;
            }
        }
        return present;
    }

    public static boolean pathTo(int x, int z) {
        if (!isPresent()) {
            return false;
        }
        try {
            Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            Object baritone = Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
            if (baritone == null) {
                return false;
            }
            Object process = Class.forName("baritone.api.IBaritone").getMethod("getCustomGoalProcess").invoke(baritone);
            if (process == null) {
                return false;
            }
            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Object goal = Class.forName("baritone.api.pathing.goals.GoalXZ")
                    .getConstructor(int.class, int.class)
                    .newInstance(x, z);
            Class.forName("baritone.api.process.ICustomGoalProcess").getMethod("setGoalAndPath", goalClass).invoke(process, goal);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
