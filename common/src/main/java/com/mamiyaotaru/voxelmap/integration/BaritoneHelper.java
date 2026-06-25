package com.mamiyaotaru.voxelmap.integration;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Collection;
import net.minecraft.core.BlockPos;

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

    private static Object primaryBaritone() throws Exception {
        Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
        return Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
    }

    private static Object customGoalProcess(Object baritone) throws Exception {
        return Class.forName("baritone.api.IBaritone").getMethod("getCustomGoalProcess").invoke(baritone);
    }

    private static Object pathingBehavior(Object baritone) throws Exception {
        return Class.forName("baritone.api.IBaritone").getMethod("getPathingBehavior").invoke(baritone);
    }

    public static boolean pathTo(int x, int z) {
        if (!isPresent()) {
            return false;
        }
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) {
                return false;
            }
            Object process = customGoalProcess(baritone);
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

    public static boolean setGoalComposite(Collection<BlockPos> positions) {
        if (!isPresent() || positions == null || positions.isEmpty()) {
            return false;
        }
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) {
                return false;
            }
            Object process = customGoalProcess(baritone);
            if (process == null) {
                return false;
            }
            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Constructor<?> goalBlockCtor = Class.forName("baritone.api.pathing.goals.GoalBlock").getConstructor(int.class, int.class, int.class);
            Object goalArray = Array.newInstance(goalClass, positions.size());
            int i = 0;
            for (BlockPos pos : positions) {
                Array.set(goalArray, i++, goalBlockCtor.newInstance(pos.getX(), pos.getY(), pos.getZ()));
            }
            Object composite = Class.forName("baritone.api.pathing.goals.GoalComposite").getConstructor(goalClass.arrayType()).newInstance(goalArray);
            Class.forName("baritone.api.process.ICustomGoalProcess").getMethod("setGoalAndPath", goalClass).invoke(process, composite);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean cancel() {
        if (!isPresent()) {
            return false;
        }
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) {
                return false;
            }
            Object pathing = pathingBehavior(baritone);
            Class.forName("baritone.api.behavior.IPathingBehavior").getMethod("cancelEverything").invoke(pathing);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isPathing() {
        if (!isPresent()) {
            return false;
        }
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) {
                return false;
            }
            Object pathing = pathingBehavior(baritone);
            Object result = Class.forName("baritone.api.behavior.IPathingBehavior").getMethod("isPathing").invoke(pathing);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }
}
