package com.sky.totmeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

/** Petits utilitaires d'UI (tout est construit en code, sans ressources XML). */
final class Ui {
    static final int BG = 0xFF12101C, BAR = 0xFF1E1A2E, ACCENT = 0xFFF5C518, TEXT = 0xFFEDEAF6, MUTED = 0xFF9A93B5;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    static Button button(Context c, String label, int bg, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(lum(bg) > 140 ? Color.BLACK : TEXT);
        b.setTextSize(14);
        b.setBackground(round(bg, dp(c, 10), 0, 0));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(dp(c, 40)); b.setMinimumHeight(dp(c, 40));
        b.setOnClickListener(l);
        return b;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static LinearLayout.LayoutParams wrap(Context c, float marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = dp(c, marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    static NumberPicker picker(Context c, int min, int max, int value) {
        NumberPicker p = new NumberPicker(c);
        p.setMinValue(min); p.setMaxValue(max); p.setValue(Math.max(min, Math.min(max, value)));
        p.setWrapSelectorWheel(false);
        return p;
    }

    static LinearLayout labeled(Context c, String label, View v) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = new TextView(c);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.addView(v);
        return l;
    }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }

    static void alert(Context c, String title, String msg) {
        new AlertDialog.Builder(c).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    /** Ouvre le jeu (UnityPlayerActivity) dans sa propre tâche. */
    static boolean launchGame(Activity a) {
        // même intent que l'icône du jeu : Android ramène la tâche existante au lieu d'en créer une 2e
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(a.getPackageName(), "com.unity3d.player.UnityPlayerActivity");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            alert(a, "Impossible de lancer le jeu", e.toString());
            return false;
        }
    }

    static void share(Activity a, Level l) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, l.name);
        i.putExtra(Intent.EXTRA_TEXT, l.toText());
        a.startActivity(Intent.createChooser(i, "Exporter « " + l.name + " »"));
    }

    private static int lum(int c) { return (int) (Color.red(c) * 0.3 + Color.green(c) * 0.59 + Color.blue(c) * 0.11); }
}
