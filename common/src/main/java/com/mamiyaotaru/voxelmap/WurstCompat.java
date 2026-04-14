package com.mamiyaotaru.voxelmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class WurstCompat {
    private WurstCompat() {
    }

    public static void syncWaypointsToggle(boolean enabled) {
        try {
            ModApiBridge bridge = VoxelConstants.getModApiBridge();
            if (bridge == null || !bridge.isModEnabled("wurst")) {
                return;
            }

            Class<?> wurstClientClass = Class.forName("net.wurstclient.WurstClient");
            Object wurstClient = getStaticField(wurstClientClass, "INSTANCE");
            if (wurstClient == null) {
                return;
            }

            Object hackContainer = invokeFirst(wurstClient, "getHax", "getHacks", "getHackList");
            if (hackContainer == null) {
                hackContainer = wurstClient;
            }

            Object waypointsHack = getNamedMember(hackContainer, "waypointsHack", "waypointHack", "waypoints");
            if (waypointsHack == null) {
                return;
            }

            if (!invokeBooleanSetter(waypointsHack, enabled, "setEnabled", "setState", "setChecked")) {
                VoxelConstants.getLogger().debug("Found Wurst waypoint hack, but no supported toggle setter.");
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getStaticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeFirst(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object getNamedMember(Object target, String... names) {
        for (String name : names) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static boolean invokeBooleanSetter(Object target, boolean value, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, boolean.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }
}
