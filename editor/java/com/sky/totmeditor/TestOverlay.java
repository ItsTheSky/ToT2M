package com.sky.totmeditor;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pendant un test, ajoute par-dessus l'activité Unity un bouton « Éditeur » et, à la fin de la partie
 * (victoire ou mort interceptées par la lib native), un panneau « Rejouer / Retour à l'éditeur ».
 * Rien n'est ajouté quand aucun test n'est actif : le jeu reste strictement normal.
 */
final class TestOverlay implements Application.ActivityLifecycleCallbacks {
    private static final String UNITY = "com.unity3d.player.UnityPlayerActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Activity game;
    private FrameLayout root;
    private LinearLayout panel;
    private TextView status, result;
    private int lastState = -1;
    private long waitSince;
    private boolean leaving;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (game == null) return;
            update();
            handler.postDelayed(this, 250);
        }
    };

    @Override public void onActivityResumed(Activity a) {
        if (!UNITY.equals(a.getClass().getName())) return;
        game = a;
        leaving = false;
        lastState = -1;
        waitSince = System.currentTimeMillis();
        if (root == null || root.getContext() != a) build(a);
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override public void onActivityPaused(Activity a) {
        if (a != game) return;
        handler.removeCallbacks(poll);
        game = null;
    }

    @Override public void onActivityDestroyed(Activity a) {
        if (root != null && root.getContext() == a) root = null;
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

    private void build(final Activity a) {
        root = new FrameLayout(a);
        root.setVisibility(View.GONE);
        int p = Ui.dp(a, 10);

        LinearLayout top = Ui.row(a);
        top.setPadding(p, p * 3, p, p);
        top.addView(Ui.button(a, "◀ Éditeur", Ui.YELLOW, new View.OnClickListener() {
            @Override public void onClick(View v) { backToEditor(); }
        }), Ui.wrap(a, 2));
        status = Ui.text(a, "", 12, Ui.YELLOW);
        status.setShadowLayer(4, 0, 0, 0xFF000000);
        top.addView(status, Ui.wrap(a, 4));
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        panel = Ui.column(a);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(p * 2, p * 2, p * 2, p * 2);
        panel.setBackground(Ui.frame(0xEE000000, Ui.YELLOW, Ui.dp(a, 2), Ui.dp(a, 3)));
        result = Ui.title(a, "");
        result.setGravity(Gravity.CENTER);
        panel.addView(result, Ui.wrap(a, 6));
        panel.addView(Ui.primary(a, "↻ Rejouer", new View.OnClickListener() {
            @Override public void onClick(View v) { NativeBridge.send(NativeBridge.C_REPLAY); panel.setVisibility(View.GONE); }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View gap = new View(a);
        panel.addView(gap, new LinearLayout.LayoutParams(1, p));
        panel.addView(Ui.button(a, "◀ Retour à l'éditeur", Ui.CYAN, new View.OnClickListener() {
            @Override public void onClick(View v) { backToEditor(); }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(a, 280), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, lp);

        a.addContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void update() {
        int st = NativeBridge.state();
        boolean active = st != NativeBridge.T_IDLE && new LevelStore(game).hasTestLevel();
        root.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active || leaving) return;
        if (st != lastState) {
            lastState = st;
            waitSince = System.currentTimeMillis();
            boolean end = st == NativeBridge.T_WON || st == NativeBridge.T_DEAD;
            panel.setVisibility(end ? View.VISIBLE : View.GONE);
            if (end) {
                result.setText(st == NativeBridge.T_WON ? "★ Niveau réussi" : "✖ Perdu");
                result.setTextColor(st == NativeBridge.T_WON ? Ui.YELLOW : Ui.MAGENTA);
                if (st == NativeBridge.T_WON && Prefs.autoReturn(game)) {
                    handler.postDelayed(new Runnable() { @Override public void run() { backToEditor(); } }, 1500);
                }
            }
        }
        String s;
        switch (st) {
            case NativeBridge.T_WAIT_MENU:
                s = System.currentTimeMillis() - waitSince > 12000 ? "Si rien ne se passe, lance un stage : ton niveau sera joué" : "Lancement du niveau…";
                break;
            case NativeBridge.T_LAUNCHED: s = "Lancement du niveau…"; break;
            case NativeBridge.T_MANUAL: s = "Lance un stage du mode Stages : ton niveau sera joué"; break;
            case NativeBridge.T_RUNNING: s = "Test en cours · rien n'est enregistré"; break;
            default: s = "";
        }
        status.setText(s);
    }

    /** Ramène le jeu au menu (commande traitée par le tick Unity), puis repasse à la tâche de l'éditeur. */
    private void backToEditor() {
        if (game == null || leaving) return;
        leaving = true;
        final Activity a = game;
        final int before = NativeBridge.send(NativeBridge.C_EXIT);
        final long t0 = System.currentTimeMillis();
        handler.post(new Runnable() {
            @Override public void run() {
                if (NativeBridge.acks() == before && System.currentTimeMillis() - t0 < 700) { handler.postDelayed(this, 50); return; }
                if (!a.moveTaskToBack(true)) {
                    Intent i = new Intent(a, LevelListActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                }
            }
        });
    }
}
