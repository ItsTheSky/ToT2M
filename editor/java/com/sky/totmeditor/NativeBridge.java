package com.sky.totmeditor;

/** Pont vers libtotmeditor.so. Toutes les méthodes sont sûres même si la lib n'a pas pu se charger. */
public final class NativeBridge {
    private static boolean loaded;
    private static String loadError = "";

    private NativeBridge() {}

    static synchronized void load(String filesDir) {
        if (loaded) return;
        try {
            System.loadLibrary("totmeditor");
            init(filesDir);
            loaded = true;
        } catch (Throwable t) {
            loadError = t.toString();
        }
    }

    public static String statusText() {
        if (!loaded) return "[--] lib native non chargée : " + loadError;
        try { return status(); } catch (Throwable t) { return "[--] " + t; }
    }

    public static boolean hooked() {
        if (!loaded) return false;
        try { return isHooked(); } catch (Throwable t) { return false; }
    }

    private static native void init(String filesDir);
    private static native String status();
    private static native boolean isHooked();
}
