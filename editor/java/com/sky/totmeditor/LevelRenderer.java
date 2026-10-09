package com.sky.totmeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Rendu d'un niveau : mode « jeu » (vrais sprites teintés, murs en autotile par quarts de 6x6 comme
 * LevelLoader) ou mode « schéma » (icônes du tileset des devs, couleurs à plat). Aussi les vignettes.
 */
final class LevelRenderer {
    final SpriteAtlas atlas;
    boolean schema;
    int wallFamily = 1;   // 1..5 : jeu de bords de mur (le jeu prend (stage / 10) % 5 + 1)
    int colorPair = 0;

    private final Paint fill = new Paint();
    private final Paint text = new Paint();
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    LevelRenderer(Context c) {
        atlas = SpriteAtlas.get(c);
        text.setTypeface(Ui.FONT);
        text.setTextAlign(Paint.Align.CENTER);
        text.setAntiAlias(false);
        arrow.setColor(0xDDFFFFFF);
    }

    int wallColor() { return atlas.colorPairs.get(colorPair % atlas.colorPairs.size())[0]; }

    int accentColor() { return atlas.colorPairs.get(colorPair % atlas.colorPairs.size())[1]; }

    static int enemyColor() { return 0xFFFF2E5B; }

    /** Couleur à plat d'une case (vignettes, mini-carte, mode schéma). */
    int flatColor(int c) {
        switch (Cell.type(c)) {
            case NONE: return 0xFF000000;
            case WALL: return wallColor();
            case WALL_EMPTY: return (wallColor() & 0x00FFFFFF) | 0x66000000;
            case BONUS_WALL: case BONUS_DOOR: return Ui.MAGENTA;
            case COIN: case DOT: return 0xFF7A6E00;
            case STAR: case EXIT: return Ui.YELLOW;
            case ENTER: return Ui.GREEN;
            case SPIKES: case MSPIKES: case BAT: case CANNON: case FISH: case BONUS_TEETH: return enemyColor();
            case PORTAL: return Ui.groupColor(Cell.group(c));
            case ICE: return 0xFFBDF4FF;
            case RAW: return 0xFFFF7A00;
            default: return Ui.CYAN;
        }
    }

    // ------------------------------------------------------------ case
    void drawCell(Canvas cv, Level l, int x, int y, float px, float py, float s) {
        int c = l.cells[y][x];
        BlockType t = Cell.type(c);
        fill.setColor(0xFF000000);
        cv.drawRect(px, py, px + s, py + s, fill);
        if (t == BlockType.NONE) return;
        if (schema || !atlas.available()) { drawSchema(cv, c, px, py, s); return; }
        float q = s / 2;
        int v = Cell.variant(c);
        switch (t) {
            case WALL: autotile(cv, l, x, y, px, py, s, wallFamily + "_", wallColor()); return;
            case WALL_EMPTY: autotile(cv, l, x, y, px, py, s, wallFamily + "_", (wallColor() & 0x00FFFFFF) | 0x77000000); return;
            case BONUS_WALL: autotile(cv, l, x, y, px, py, s, "Secret_", Ui.MAGENTA); return;
            case SPIKES: spikes(cv, l, x, y, px, py, s, "Spikes_", enemyColor()); return;
            case MSPIKES: sprite(cv, "M_Spikes_attack_1", px, py, s, enemyColor(), 0, false); return;
            case COIN: sprite(cv, "Coin_1", px, py, s, accentColor(), 0, false); return;
            case DOT: sprite(cv, "Dot_game", px, py, s, accentColor(), 0, false); return;
            case STAR: sprite(cv, "Star_anim_1", px, py, s, Ui.YELLOW, 0, false); return;
            case EXIT: sprite(cv, "Exit_1", px, py, s, Ui.YELLOW, 0, false); return;
            case ENTER: case PLAYER: sprite(cv, "0_Char_idle_1", px, py, s, 0, 0, false); return;
            case ICE: sprite(cv, "Ice", px, py, s, 0xFFBDF4FF, 0, false); return;
            case CLOSE_BRICK: sprite(cv, "Ice", px, py, s, 0xFFFF7A00, 0, false); return;
            case TIMER_WALL: sprite(cv, "Timer_wall_0", px, py, s, wallColor(), 0, false); return;
            case FISH: sprite(cv, "fish_hedgehog_1", px, py, s, enemyColor(), 0, false); return;
            case BONUS_DOOR: sprite(cv, "Secret_room_1", px, py, s, Ui.MAGENTA, 0, false); return;
            case ZONE_EXIT: sprite(cv, "Gates_1", px, py + q / 2, s, Ui.YELLOW, 0, false); return;
            case ROTATION: sprite(cv, "Rotor_1", px, py, s, Ui.CYAN, 0, v == 1); return;
            case BONUS_TEETH: sprite(cv, "Teeth_1", px, py, s, enemyColor(), 0, v == 3); dirArrow(cv, px, py, s, v); return;
            case POWERUP: sprite(cv, new String[]{"Coin_addict_1", "Freeze_1", "Magnet_1", "Scores_1"}[v & 3], px, py, s, Ui.YELLOW, 0, false); return;
            case BAT: sprite(cv, "Bat_1", px, py, s, enemyColor(), 0, v == 3); dirArrow(cv, px, py, s, v); return;
            case CANNON: sprite(cv, "Cannon_1", px, py, s, enemyColor(), v, false); dirArrow(cv, px, py, s, v); return;
            case PLATFORM: sprite(cv, "Platform_1", px, py, s, Ui.CYAN, 0, false); dirArrow(cv, px, py, s, v); return;
            case TRAMPLIN: sprite(cv, "Tramplin_1", px, py, s, Ui.CYAN, 0, v == 2 || v == 3); diagArrow(cv, px, py, s, v); return;
            case PORTAL: sprite(cv, "Portal_1", px, py, s, Ui.groupColor(Cell.group(c)), v == 1 ? 1 : 0, false); badge(cv, Cell.group(c), px, py, s); return;
            case LIGHTBALL: sprite(cv, "lightning", px, py, s, Ui.groupColor(Cell.group(c)), 0, false); badge(cv, Cell.group(c), px, py, s); return;
            default:
                drawSchema(cv, c, px, py, s);
        }
    }

    private void sprite(Canvas cv, String name, float px, float py, float s, int tint, int rot, boolean flip) {
        if (!atlas.draw(cv, name, px, py, px + s, py + s, tint, rot, flip)) {
            fill.setColor(tint == 0 ? Ui.CYAN : tint);
            cv.drawRect(px + s * 0.2f, py + s * 0.2f, px + s * 0.8f, py + s * 0.8f, fill);
        }
    }

    private static boolean wallLike(Level l, int x, int y, BlockType self) {
        if (!l.inside(x, y)) return true;   // hors du niveau : fermé (pas de bord dessiné)
        BlockType t = Cell.type(l.cells[y][x]);
        return t == self || (self == BlockType.WALL && t == BlockType.WALL_EMPTY) || (self == BlockType.WALL_EMPTY && t == BlockType.WALL);
    }

    /** Murs : chaque quart de case reçoit un bord, un coin extérieur ou un coin intérieur selon ses voisins. */
    private void autotile(Canvas cv, Level l, int x, int y, float px, float py, float s, String prefix, int tint) {
        BlockType self = Cell.type(l.cells[y][x]);
        float q = s / 2;
        for (int qy = 0; qy < 2; qy++)
            for (int qx = 0; qx < 2; qx++) {
                int dx = qx == 0 ? -1 : 1, dy = qy == 0 ? -1 : 1;
                boolean vOpen = !wallLike(l, x, y + dy, self), hOpen = !wallLike(l, x + dx, y, self), dOpen = !wallLike(l, x + dx, y + dy, self);
                String vs = qy == 0 ? "u" : "d", hs = qx == 0 ? "l" : "r";
                String name;
                if (vOpen && hOpen) name = prefix + "Corner_" + vs + "_" + hs;
                else if (vOpen) name = prefix + "Wall_" + vs;
                else if (hOpen) name = prefix + "Wall_" + hs;
                else if (dOpen) name = prefix + "Inner_" + vs + "_" + hs;
                else continue;
                String pick = variant(name, x * 7 + y * 13 + qx * 3 + qy);
                float l0 = px + qx * q, t0 = py + qy * q;
                if (pick == null || !atlas.draw(cv, pick, l0, t0, l0 + q, t0 + q, tint, 0, false)) {
                    fill.setColor(tint);
                    cv.drawRect(l0, t0, l0 + q, t0 + q, fill);
                }
            }
    }

    /** Les bords de mur existent en 1 à 4 variantes (« _1 » à « _4 ») ; les coins intérieurs sans suffixe. */
    private String variant(String base, int h) {
        int n = 0;
        while (n < 4 && atlas.has(base + "_" + (n + 1))) n++;
        if (n == 0) return atlas.has(base) ? base : null;
        return base + "_" + (1 + Math.abs(h) % n);
    }

    /** Pics plantés dans chaque mur voisin (le jeu les oriente lui-même). */
    private void spikes(Canvas cv, Level l, int x, int y, float px, float py, float s, String prefix, int tint) {
        float q = s / 2;
        boolean any = false;
        if (l.inside(x, y + 1) && Cell.solid(l.cells[y + 1][x])) { any = true; two(cv, prefix + "u", px, py + q, q, true, tint); }
        if (l.inside(x, y - 1) && Cell.solid(l.cells[y - 1][x])) { any = true; two(cv, prefix + "d", px, py, q, true, tint); }
        if (l.inside(x - 1, y) && Cell.solid(l.cells[y][x - 1])) { any = true; two(cv, prefix + "r", px, py, q, false, tint); }
        if (l.inside(x + 1, y) && Cell.solid(l.cells[y][x + 1])) { any = true; two(cv, prefix + "l", px + q, py, q, false, tint); }
        if (!any) two(cv, prefix + "u", px, py + q, q, true, tint);
    }

    private void two(Canvas cv, String name, float l, float t, float q, boolean horiz, int tint) {
        if (horiz) { atlas.draw(cv, name, l, t, l + q, t + q, tint, 0, false); atlas.draw(cv, name, l + q, t, l + 2 * q, t + q, tint, 0, false); }
        else { atlas.draw(cv, name, l, t, l + q, t + q, tint, 0, false); atlas.draw(cv, name, l, t + q, l + q, t + 2 * q, tint, 0, false); }
    }

    private void dirArrow(Canvas cv, float px, float py, float s, int dir) {
        if (s < 14) return;
        float cx = px + s / 2, cy = py + s / 2, a = s * 0.16f, d = s * 0.5f - a * 0.6f;
        path.reset();
        switch (dir & 3) {
            case 0: path.moveTo(cx, cy - d - a / 2); path.lineTo(cx - a, cy - d + a / 2); path.lineTo(cx + a, cy - d + a / 2); break;
            case 1: path.moveTo(cx + d + a / 2, cy); path.lineTo(cx + d - a / 2, cy - a); path.lineTo(cx + d - a / 2, cy + a); break;
            case 2: path.moveTo(cx, cy + d + a / 2); path.lineTo(cx - a, cy + d - a / 2); path.lineTo(cx + a, cy + d - a / 2); break;
            default: path.moveTo(cx - d - a / 2, cy); path.lineTo(cx - d + a / 2, cy - a); path.lineTo(cx - d + a / 2, cy + a); break;
        }
        path.close();
        cv.drawPath(path, arrow);
    }

    /** Diagonales : 0 haut-droite, 1 bas-droite, 2 bas-gauche, 3 haut-gauche. */
    private void diagArrow(Canvas cv, float px, float py, float s, int v) {
        if (s < 14) return;
        float a = s * 0.3f;
        float cx = (v == 0 || v == 1) ? px + s : px, cy = (v == 0 || v == 3) ? py : py + s;
        float sx = cx == px ? 1 : -1, sy = cy == py ? 1 : -1;
        path.reset();
        path.moveTo(cx, cy);
        path.lineTo(cx + sx * a, cy);
        path.lineTo(cx, cy + sy * a);
        path.close();
        cv.drawPath(path, arrow);
    }

    private void badge(Canvas cv, int g, float px, float py, float s) {
        if (g <= 0 || s < 16) return;
        float r = s * 0.22f;
        fill.setColor(0xFF000000);
        cv.drawRect(px + s - 2 * r, py, px + s, py + 2 * r, fill);
        text.setColor(Ui.groupColor(g));
        text.setTextSize(r * 1.8f);
        cv.drawText(Integer.toString(g), px + s - r, py + r - (text.descent() + text.ascent()) / 2, text);
    }

    /** Mode schéma : icône du tileset des devs (valeur brute), sinon carré coloré + libellé. */
    private void drawSchema(Canvas cv, int c, float px, float py, float s) {
        int raw = Cell.toRaw(c);
        if (Cell.is(c, BlockType.WALL)) { fill.setColor(wallColor()); cv.drawRect(px, py, px + s, py + s, fill); return; }
        if (atlas.draw(cv, "dev_" + raw, px, py, px + s, py + s, 0, 0, false)) {
            if (Cell.type(c).groups > 0) badge(cv, Cell.group(c), px, py, s);
            return;
        }
        fill.setColor(flatColor(c));
        cv.drawRect(px + s * 0.08f, py + s * 0.08f, px + s * 0.92f, py + s * 0.92f, fill);
        if (s >= 14) {
            text.setColor(Ui.lum(flatColor(c)) > 140 ? 0xFF000000 : 0xFFFFFFFF);
            text.setTextSize(s * (raw > 99 ? 0.36f : 0.45f));
            cv.drawText(Integer.toString(raw), px + s / 2, py + s / 2 - (text.descent() + text.ascent()) / 2, text);
        }
    }

    // ------------------------------------------------------------ vignettes
    /** Une couleur par case : très rapide, sert aux listes et à la mini-carte (afficher sans filtrage). */
    Bitmap thumbnail(Level l) {
        int[] px = new int[l.width * l.height];
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++) {
                int c = l.cells[y][x];
                int col = flatColor(c);
                if (Cell.isWall(c)) {
                    // n'éclaire que les murs qui bordent un couloir, comme en jeu
                    boolean edge = false;
                    for (int k = -1; k <= 1 && !edge; k++)
                        for (int m = -1; m <= 1 && !edge; m++)
                            if (l.inside(x + k, y + m) && !Cell.solid(l.cells[y + m][x + k])) edge = true;
                    col = edge ? col : 0xFF000000;
                }
                px[y * l.width + x] = col;
            }
        return Bitmap.createBitmap(px, l.width, l.height, Bitmap.Config.ARGB_8888);
    }

    /** Aperçu fidèle en sprites, à `cell` pixels par case (écran d'aperçu, partage d'image). */
    Bitmap preview(Level l, int cell) {
        int maxSide = 2048;
        cell = Math.max(2, Math.min(cell, maxSide / Math.max(l.width, l.height)));
        Bitmap b = Bitmap.createBitmap(l.width * cell, l.height * cell, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++) drawCell(cv, l, x, y, x * cell, y * cell, cell);
        return b;
    }
}
