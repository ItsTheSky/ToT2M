package com.sky.totmeditor;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages de l'éditeur (fichier séparé des PlayerPrefs du jeu). */
final class Prefs {
    private Prefs() {}

    static final int LAUNCH_AUTO = 0, LAUNCH_PLAY_STORY = 1, LAUNCH_BTN_LEVEL = 2, LAUNCH_MANUAL = 3;
    static final String[] LAUNCH_NAMES = {"Automatique (recommandé)", "PlayStoryGame uniquement", "Bouton du stage uniquement", "Manuel (je lance un stage)"};

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("totm_editor", Context.MODE_PRIVATE); }

    static int supportStage(Context c) { return sp(c).getInt("support_stage", 0); }
    static void setSupportStage(Context c, int v) { sp(c).edit().putInt("support_stage", v).apply(); }

    static int launchMode(Context c) { return sp(c).getInt("launch_mode", LAUNCH_AUTO); }
    static void setLaunchMode(Context c, int v) { sp(c).edit().putInt("launch_mode", v).apply(); }

    static boolean haptics(Context c) { return sp(c).getBoolean("haptics", true); }
    static void setHaptics(Context c, boolean v) { sp(c).edit().putBoolean("haptics", v).apply(); }

    static boolean grid(Context c) { return sp(c).getBoolean("grid", true); }
    static void setGrid(Context c, boolean v) { sp(c).edit().putBoolean("grid", v).apply(); }

    static boolean schema(Context c) { return sp(c).getBoolean("schema", false); }
    static void setSchema(Context c, boolean v) { sp(c).edit().putBoolean("schema", v).apply(); }

    static boolean autoReturn(Context c) { return sp(c).getBoolean("auto_return", false); }
    static void setAutoReturn(Context c, boolean v) { sp(c).edit().putBoolean("auto_return", v).apply(); }

    static int sort(Context c) { return sp(c).getInt("sort", 0); }
    static void setSort(Context c, int v) { sp(c).edit().putInt("sort", v).apply(); }

    /** Niveau en cours de test (pour revenir dessus depuis l'overlay du jeu). */
    static String testingId(Context c) { return sp(c).getString("testing_id", null); }
    static void setTestingId(Context c, String id) { sp(c).edit().putString("testing_id", id).apply(); }
}
