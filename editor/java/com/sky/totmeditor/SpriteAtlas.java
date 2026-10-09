package com.sky.totmeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Sprites du jeu embarqués au build (assets/totm_editor/atlas.png + atlas.txt, cf. tools/make_atlas.py).
 * Les sprites sont blancs : ils sont teintés par multiplication, comme en jeu. Filtrage nearest.
 */
final class SpriteAtlas {
    private static SpriteAtlas instance;

    private Bitmap bitmap;
    private final HashMap<String, Rect> rects = new HashMap<>();
    private final HashMap<Integer, Paint> paints = new HashMap<>();
    /** Paires de couleurs (principale, secondaire) extraites du GameController, sinon palette par défaut. */
    final List<int[]> colorPairs = new ArrayList<>();
    private final RectF tmp = new RectF();

    static synchronized SpriteAtlas get(Context c) {
        if (instance == null) instance = new SpriteAtlas(c.getApplicationContext());
        return instance;
    }

    private SpriteAtlas(Context c) {
        try (InputStream in = c.getAssets().open("totm_editor/atlas.png")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            bitmap = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            bitmap = null;
        }
        readLines(c, "totm_editor/atlas.txt", new LineHandler() {
            @Override public void line(String[] p) {
                if (p.length == 5) rects.put(p[0], new Rect(i(p[1]), i(p[2]), i(p[1]) + i(p[3]), i(p[2]) + i(p[4])));
            }
        });
        readLines(c, "totm_editor/colors.txt", new LineHandler() {
            @Override public void line(String[] p) {
                if (p.length >= 2 && p[0].startsWith("#")) colorPairs.add(new int[]{0xFF000000 | Integer.parseInt(p[0].substring(1), 16), 0xFF000000 | Integer.parseInt(p[1].substring(1), 16)});
            }
        });
        if (colorPairs.isEmpty()) {
            // palette par défaut (néons TotM) si les couleurs n'ont pas pu être extraites de l'APK
            int[][] def = {{0xFFB026FF, 0xFFFFE600}, {0xFF00E5FF, 0xFFFFE600}, {0xFFFF2E88, 0xFFFFE600}, {0xFF39FF14, 0xFFFFE600}, {0xFFFF7A00, 0xFFFFE600}};
            for (int[] d : def) colorPairs.add(d);
        }
    }

    private interface LineHandler { void line(String[] parts); }

    private static int i(String s) { return Integer.parseInt(s); }

    private static void readLines(Context c, String asset, LineHandler h) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open(asset), "UTF-8"))) {
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#") && !l.matches("#[0-9A-Fa-f]{6}.*")) continue;
                try { h.line(l.split("\\s+")); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
            // asset absent : l'éditeur passe en rendu schéma
        }
    }

    boolean available() { return bitmap != null && !rects.isEmpty(); }

    boolean has(String name) { return rects.containsKey(name); }

    private Paint paint(int tint) {
        Paint p = paints.get(tint);
        if (p == null) {
            p = new Paint();
            p.setFilterBitmap(false);
            p.setAntiAlias(false);
            p.setDither(false);
            if (tint != 0) {
                p.setColorFilter(new PorterDuffColorFilter(tint | 0xFF000000, PorterDuff.Mode.MULTIPLY));
                p.setAlpha(Math.max(1, (tint >>> 24)));
            }
            paints.put(tint, p);
        }
        return p;
    }

    /** Dessine le sprite dans dst. tint = 0 : couleurs d'origine. rot en quarts de tour horaires, flipX = miroir. */
    boolean draw(Canvas c, String name, float l, float t, float r, float b, int tint, int rot, boolean flipX) {
        Rect src = rects.get(name);
        if (src == null || bitmap == null) return false;
        tmp.set(l, t, r, b);
        if (rot == 0 && !flipX) {
            c.drawBitmap(bitmap, src, tmp, paint(tint));
            return true;
        }
        c.save();
        float cx = (l + r) / 2, cy = (t + b) / 2;
        if (rot != 0) c.rotate(90f * rot, cx, cy);
        if (flipX) c.scale(-1, 1, cx, cy);
        c.drawBitmap(bitmap, src, tmp, paint(tint));
        c.restore();
        return true;
    }
}
