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

    /** États du test (cf. TestState dans totmeditor.cpp). */
    public static final int T_IDLE = 0, T_WAIT_MENU = 1, T_LAUNCHED = 2, T_RUNNING = 3, T_WON = 4, T_DEAD = 5, T_EXITING = 6, T_MANUAL = 7;
    public static final int C_REPLAY = 1, C_EXIT = 2;

    public static void startTest(int supportStage, int launchMode) {
        if (loaded) try { beginTest(supportStage, launchMode); } catch (Throwable ignored) { }
    }

    public static void stopTest() {
        if (loaded) try { endTest(); } catch (Throwable ignored) { }
    }

    public static int state() {
        if (!loaded) return T_IDLE;
        try { return testState(); } catch (Throwable t) { return T_IDLE; }
    }

    /** Envoie une commande au tick du jeu ; renvoie le compteur d'acquittements avant l'envoi. */
    public static int send(int cmd) {
        if (!loaded) return -1;
        try { return command(cmd); } catch (Throwable t) { return -1; }
    }

    public static int acks() {
        if (!loaded) return -1;
        try { return commandAck(); } catch (Throwable t) { return -1; }
    }

    public static int hooks() {
        if (!loaded) return 0;
        try { return hookMask(); } catch (Throwable t) { return 0; }
    }

    private static native void init(String filesDir);
    private static native void beginTest(int stage, int mode);
    private static native void endTest();
    private static native int testState();
    private static native int command(int cmd);
    private static native int commandAck();
    private static native int hookMask();
    private static native String status();
    private static native boolean isHooked();
}
