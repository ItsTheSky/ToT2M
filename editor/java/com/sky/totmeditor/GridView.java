package com.sky.totmeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Grille éditable : un doigt peint, deux doigts déplacent et zooment. */
public class GridView extends View {
    public interface Listener {
        void onStrokeStart();
        void onLevelChanged();
        /** Un 2e doigt est arrivé pendant un trait : le trait devait être un geste de zoom, on l'annule. */
        void onStrokeCancel();
        /** Fin d'un trait ; changed = au moins une case modifiée. */
        void onStrokeEnd(boolean changed);
    }

    private Level level;
    private int tool = Level.WALL;
    private Listener listener;
    private final Tiles tiles = new Tiles();
    private final Paint gridPaint = new Paint();

    private float cell = 32, offX, offY;
    private boolean fitted;
    private final ScaleGestureDetector scaler;

    private boolean painting, panning, strokeChanged;
    private int lastCx = -1, lastCy = -1;
    private float lastFx, lastFy;

    public GridView(Context ctx) {
        super(ctx);
        gridPaint.setColor(0x22FFFFFF);
        gridPaint.setStrokeWidth(1);
        scaler = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float ns = clamp(cell * d.getScaleFactor(), 6, 160);
                float k = ns / cell;
                offX = d.getFocusX() - (d.getFocusX() - offX) * k;
                offY = d.getFocusY() - (d.getFocusY() - offY) * k;
                cell = ns;
                invalidate();
                return true;
            }
        });
        scaler.setQuickScaleEnabled(false);  // sinon double-tap + glisser zoome en peignant
    }

    public void setLevel(Level l) { level = l; fitted = false; requestLayout(); invalidate(); }
    public void setTool(int t) { tool = t; }
    public void setListener(Listener l) { listener = l; }

    public void fit() {
        if (level == null || getWidth() == 0 || getHeight() == 0) return;
        cell = Math.max(2f, Math.min(getWidth() / (float) level.width, getHeight() / (float) level.height));
        offX = (getWidth() - cell * level.width) / 2;
        offY = (getHeight() - cell * level.height) / 2;
        fitted = true;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fit(); }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF05050A);
        if (level == null) return;
        if (!fitted) fit();
        int x0 = Math.max(0, (int) Math.floor(-offX / cell)), y0 = Math.max(0, (int) Math.floor(-offY / cell));
        int x1 = Math.min(level.width - 1, (int) ((getWidth() - offX) / cell));
        int y1 = Math.min(level.height - 1, (int) ((getHeight() - offY) / cell));
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                tiles.draw(c, level, x, y, offX + x * cell, offY + y * cell, cell);
        if (cell >= 14) {
            for (int x = x0; x <= x1 + 1; x++) c.drawLine(offX + x * cell, offY + y0 * cell, offX + x * cell, offY + (y1 + 1) * cell, gridPaint);
            for (int y = y0; y <= y1 + 1; y++) c.drawLine(offX + x0 * cell, offY + y * cell, offX + (x1 + 1) * cell, offY + y * cell, gridPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (level == null) return false;
        scaler.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                painting = true; panning = false; strokeChanged = false;
                lastCx = lastCy = -1;
                if (listener != null) listener.onStrokeStart();
                paintAt(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (painting && listener != null) listener.onStrokeCancel();
                painting = false; panning = true;
                lastFx = focusX(e); lastFy = focusY(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (panning && e.getPointerCount() >= 2) {
                    float fx = focusX(e), fy = focusY(e);
                    offX += fx - lastFx; offY += fy - lastFy;
                    lastFx = fx; lastFy = fy;
                    invalidate();
                } else if (painting && tool != Level.ENTER && tool != Level.EXIT) {
                    for (int i = 0; i < e.getHistorySize(); i++) paintAt(e.getHistoricalX(i), e.getHistoricalY(i));
                    paintAt(e.getX(), e.getY());
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // le doigt restant ne repeint pas : on reste en mode déplacement jusqu'au relâchement complet
                lastFx = focusX(e); lastFy = focusY(e);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (painting && listener != null) listener.onStrokeEnd(strokeChanged);
                painting = panning = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void paintAt(float sx, float sy) {
        if (cell <= 0) return;
        int cx = (int) Math.floor((sx - offX) / cell), cy = (int) Math.floor((sy - offY) / cell);
        if (lastCx >= 0) line(lastCx, lastCy, cx, cy); else apply(cx, cy);
        lastCx = cx; lastCy = cy;
    }

    /** Bresenham : pas de trous quand on glisse vite. */
    private void line(int xa, int ya, int xb, int yb) {
        int dx = Math.abs(xb - xa), dy = -Math.abs(yb - ya), sx = xa < xb ? 1 : -1, sy = ya < yb ? 1 : -1, err = dx + dy;
        while (true) {
            apply(xa, ya);
            if (xa == xb && ya == yb) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; xa += sx; }
            if (e2 <= dx) { err += dx; ya += sy; }
        }
    }

    private void apply(int x, int y) {
        if (level.set(x, y, tool)) {
            strokeChanged = true;
            invalidate();
            if (listener != null) listener.onLevelChanged();
        }
    }

    private static float focusX(MotionEvent e) {
        float s = 0;
        int n = e.getPointerCount(), skip = e.getActionMasked() == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1, k = 0;
        for (int i = 0; i < n; i++) if (i != skip) { s += e.getX(i); k++; }
        return k == 0 ? 0 : s / k;
    }

    private static float focusY(MotionEvent e) {
        float s = 0;
        int n = e.getPointerCount(), skip = e.getActionMasked() == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1, k = 0;
        for (int i = 0; i < n; i++) if (i != skip) { s += e.getY(i); k++; }
        return k == 0 ? 0 : s / k;
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
