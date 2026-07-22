package com.tiertagger.util;

public final class GameProfileCompat {
    private GameProfileCompat() {}

    public static String getName(Object gameProfile) {
        if (gameProfile == null) return null;

        // Newer mappings may expose record component style name()
        String n = invokeString(gameProfile, "name");
        if (n != null && !n.isBlank()) return n;

        // Older mappings (and authlib) typically expose getName()
        n = invokeString(gameProfile, "getName");
        if (n != null && !n.isBlank()) return n;

        return null;
    }

    public static java.util.UUID getUuid(Object gameProfile) {
        if (gameProfile == null) return null;
        Object v = invoke(gameProfile, "id");
        if (v instanceof java.util.UUID u) return u;
        v = invoke(gameProfile, "getId");
        if (v instanceof java.util.UUID u) return u;
        return null;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName) {
        Object v = invoke(target, methodName);
        return (v instanceof String s) ? s : null;
    }
}
