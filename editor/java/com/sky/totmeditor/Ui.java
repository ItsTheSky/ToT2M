package com.sky.totmeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

/** Thème « Tomb of the Mask » (noir, néons jaune / magenta / cyan) et petits utilitaires d'UI en code. */
final class Ui {
    static final int BG = 0xFF000000, PANEL = 0xFF0B0B12, PANEL2 = 0xFF14141F;
    static final int YELLOW = 0xFFFFE600, MAGENTA = 0xFFFF2E88, CYAN = 0xFF00E5FF, GREEN = 0xFF39FF14, RED = 0xFFFF3B3B;
    static final int TEXT = 0xFFF2F2F2, MUTED = 0xFF8A8AA0;
    static final int BAR = PANEL2, ACCENT = YELLOW;
    static final int[] GROUP_COLORS = {0xFFFF2E88, 0xFF00E5FF, 0xFFFFE600, 0xFF39FF14, 0xFFFF7A00, 0xFFB026FF, 0xFFFFFFFF};

    /** Police « pixel » : monospace grasse, sans anticrénelage. */
    static final Typeface FONT = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static int groupColor(int g) { return GROUP_COLORS[(Math.max(1, g) - 1) % GROUP_COLORS.length]; }

    static void fullscreenDark(Activity a) {
        a.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = a.getWindow();
        if (Build.VERSION.SDK_INT >= 21) { w.setStatusBarColor(BG); w.setNavigationBarColor(BG); }
    }

    /** Cadre néon : fond sombre, bord coloré, coins presque carrés (style pixel). */
    static GradientDrawable frame(int fill, int stroke, int strokePx, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(strokePx, stroke);
        return d;
    }

    static GradientDrawable round(int color, float radiusPx, int strokeColor, int strokePx) {
        return frame(color, strokeColor, strokePx, Math.round(radiusPx));
    }

    static StateListDrawable neon(Context c, int accent, boolean filled) {
        StateListDrawable s = new StateListDrawable();
        int st = dp(c, 2), r = dp(c, 3);
        s.addState(new int[]{android.R.attr.state_pressed}, frame(accent, accent, st, r));
        s.addState(new int[]{android.R.attr.state_selected}, frame(accent, accent, st, r));
        s.addState(new int[]{}, frame(filled ? accent : PANEL2, accent, st, r));
        return s;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(FONT);
        t.getPaint().setAntiAlias(false);
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 22, YELLOW);
        t.setShadowLayer(dp(c, 6), 0, 0, YELLOW);
        return t;
    }

    /** Gros bouton néon (≥ 48 dp de haut : atteignable au pouce). */
    static Button button(Context c, String label, int accent, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(true);
        b.setTypeface(FONT);
        b.getPaint().setAntiAlias(false);
        b.setTextSize(14);
        b.setTextColor(lum(accent) > 140 ? accent : TEXT);
        b.setBackground(neon(c, accent, false));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    static Button primary(Context c, String label, View.OnClickListener l) {
        Button b = button(c, label, YELLOW, l);
        b.setBackground(neon(c, YELLOW, true));
        b.setTextColor(Color.BLACK);
        return b;
    }

    static LinearLayout.LayoutParams wrap(Context c, float marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = dp(c, marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    static LinearLayout.LayoutParams weight(Context c, float marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        int m = dp(c, marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static NumberPicker picker(Context c, int min, int max, int value) {
        NumberPicker p = new NumberPicker(c);
        p.setMinValue(min); p.setMaxValue(max); p.setValue(Math.max(min, Math.min(max, value)));
        p.setWrapSelectorWheel(false);
        return p;
    }

    static LinearLayout labeled(Context c, String label, View v) {
        LinearLayout l = column(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = text(c, label, 13, MUTED);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.addView(v);
        return l;
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }

    static void alert(Context c, String title, String msg) {
        dialog(c).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    static void haptic(View v) {
        if (Prefs.haptics(v.getContext())) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    /** Ouvre le jeu (UnityPlayerActivity) dans sa propre tâche, comme l'icône du jeu. */
    static boolean launchGame(Activity a) {
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

    static void shareText(Activity a, String subject, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, text);
        a.startActivity(Intent.createChooser(i, "Partager « " + subject + " »"));
    }

    static void share(Activity a, Level l) { shareText(a, l.name, l.toText()); }

    static void copy(Context c, String label, String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
    }

    static String paste(Context c) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence t = clip.getItemAt(0).coerceToText(c);
        return t == null ? null : t.toString();
    }

    static int lum(int c) { return (int) (Color.red(c) * 0.3 + Color.green(c) * 0.59 + Color.blue(c) * 0.11); }

    static boolean isTabletLandscape(Context c) {
        android.content.res.Configuration cf = c.getResources().getConfiguration();
        return cf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && cf.smallestScreenWidthDp >= 600;
    }
}
