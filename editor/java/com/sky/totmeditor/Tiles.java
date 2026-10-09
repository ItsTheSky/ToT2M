package com.sky.totmeditor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Outils de la palette et rendu des tuiles. */
final class Tiles {
    /** Valeurs brutes LevelTileType utilisées par la palette simple. */
    static final int EMPTY = 0, ENTER = 1, EXIT = 2, WALL = 3, STAR = 4, SPIKES = 8, DOT = 19;

    static final int[] TOOLS = {WALL, EMPTY, DOT, SPIKES, STAR, ENTER, EXIT};
    static final String[] TOOL_NAMES = {"Mur", "Vide", "Point", "Pics", "Étoile", "Départ", "Sortie"};

    static final int C_WALL = 0xFF3B2163, C_WALL_EDGE = 0xFFA06BFF, C_EMPTY = 0xFF0E0E16, C_DOT = 0xFFF5C518,
            C_SPIKE = 0xFFE53935, C_STAR = 0xFFFFFFFF, C_ENTER = 0xFF2ECC71, C_EXIT = 0xFF2E9BFF, C_OTHER = 0xFFFF8A00;

    static int color(int v) {
        switch (v) {
            case WALL: return C_WALL;
            case EMPTY: return C_EMPTY;
            case DOT: return C_DOT;
            case SPIKES: return C_SPIKE;
            case STAR: return C_STAR;
            case ENTER: return C_ENTER;
            case EXIT: return C_EXIT;
            default: return C_OTHER;
        }
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    Tiles() {
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    /** Dessine la case (x, y) du niveau dans le carré [px, py, px+s, py+s]. */
    void draw(Canvas c, Level l, int x, int y, float px, float py, float s) {
        int v = Cell.toRaw(l.get(x, y));
        if (v == WALL) {
            fill.setColor(C_WALL);
            c.drawRect(px, py, px + s, py + s, fill);
            // liseré lumineux côté couloir, façon Tomb of the Mask
            fill.setColor(C_WALL_EDGE);
            float e = Math.max(1f, s * 0.12f);
            if (open(l, x, y - 1)) c.drawRect(px, py, px + s, py + e, fill);
            if (open(l, x, y + 1)) c.drawRect(px, py + s - e, px + s, py + s, fill);
            if (open(l, x - 1, y)) c.drawRect(px, py, px + e, py + s, fill);
            if (open(l, x + 1, y)) c.drawRect(px + s - e, py, px + s, py + s, fill);
            return;
        }
        fill.setColor(C_EMPTY);
        c.drawRect(px, py, px + s, py + s, fill);
        float cx = px + s / 2, cy = py + s / 2;
        switch (v) {
            case EMPTY:
                return;
            case DOT:
                fill.setColor(C_DOT);
                c.drawCircle(cx, cy, s * 0.13f, fill);
                return;
            case STAR:
                fill.setColor(C_STAR);
                star(c, cx, cy, s * 0.42f);
                return;
            case SPIKES:
                spikes(c, l, x, y, px, py, s);
                return;
            case ENTER:
            case EXIT:
                fill.setColor(color(v));
                r.set(px + s * 0.12f, py + s * 0.12f, px + s * 0.88f, py + s * 0.88f);
                c.drawRoundRect(r, s * 0.18f, s * 0.18f, fill);
                label(c, v == ENTER ? "D" : "S", cx, cy, s, 0xFF000000);
                return;
            default:
                // tuile d'un niveau officiel non éditable ici (chauve-souris, canon, portail...) : conservée telle quelle
                fill.setColor(C_OTHER);
                r.set(px + s * 0.1f, py + s * 0.1f, px + s * 0.9f, py + s * 0.9f);
                c.drawRect(r, fill);
                label(c, Integer.toString(v), cx, cy, s * 0.8f, 0xFF000000);
        }
    }

    private static boolean open(Level l, int x, int y) { return l.inside(x, y) && Cell.toRaw(l.get(x, y)) != WALL; }

    private void label(Canvas c, String t, float cx, float cy, float s, int col) {
        text.setColor(col);
        text.setTextSize(s * 0.55f);
        c.drawText(t, cx, cy - (text.descent() + text.ascent()) / 2, text);
    }

    private void star(Canvas c, float cx, float cy, float rad) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? rad : rad * 0.45f;
            float px = cx + (float) (Math.cos(a) * rr), py = cy + (float) (Math.sin(a) * rr);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, fill);
    }

    /** Pics plantés dans le premier mur voisin (le jeu calcule lui-même l'orientation). */
    private void spikes(Canvas c, Level l, int x, int y, float px, float py, float s) {
        fill.setColor(C_SPIKE);
        int dir = l.inside(x, y + 1) && Cell.toRaw(l.get(x, y + 1)) == WALL ? 0
                : l.inside(x, y - 1) && Cell.toRaw(l.get(x, y - 1)) == WALL ? 1
                : l.inside(x - 1, y) && Cell.toRaw(l.get(x - 1, y)) == WALL ? 2
                : l.inside(x + 1, y) && Cell.toRaw(l.get(x + 1, y)) == WALL ? 3 : 0;
        float h = s * 0.45f;
        path.reset();
        for (int i = 0; i < 3; i++) {
            float a = s * (0.1f + 0.27f * i), b = a + s * 0.26f, m = (a + b) / 2;
            switch (dir) {
                case 0: path.moveTo(px + a, py + s); path.lineTo(px + m, py + s - h); path.lineTo(px + b, py + s); break;
                case 1: path.moveTo(px + a, py); path.lineTo(px + m, py + h); path.lineTo(px + b, py); break;
                case 2: path.moveTo(px, py + a); path.lineTo(px + h, py + m); path.lineTo(px, py + b); break;
                default: path.moveTo(px + s, py + a); path.lineTo(px + s - h, py + m); path.lineTo(px + s, py + b); break;
            }
            path.close();
        }
        c.drawPath(path, fill);
    }
}
