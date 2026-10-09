package com.sky.totmeditor;

import android.app.Application;

/** Chargée avant toute activité (jeu ou éditeur) : installe la lib native qui attend le moteur IL2CPP. */
public class EditorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // nouveau processus = pas de test en cours : le jeu doit démarrer avec ses vrais niveaux
        new LevelStore(this).clearTestLevel();
        NativeBridge.load(getFilesDir().getAbsolutePath());
    }
}
