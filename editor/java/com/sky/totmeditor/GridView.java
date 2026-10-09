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
        /** Pipette : la case touchée devient le bloc courant. */
        void onPicked(int cell);
        /** Case sous le doigt (affichage des coordonnées). */
        void onCursor(int x, int y);
    }

    public enum Mode { BRUSH, RECT, LINE, FILL, PICKER, ROTATE }

    private Level level;
    private int brush = Cell.WALL;
    private Mode mode = Mode.BRUSH;
    private boolean showGrid = true;
    private int startX = -1, startY = -1, endX = -1, endY = -1;
    private Listener listener;
    private final LevelRenderer renderer;
    private final Paint preview = new Paint();
    private final Paint gridPaint = new Paint();

    private float cell = 32, offX, offY;
    private boolean fitted;
    private final ScaleGestureDetector scaler;

    private boolean painting, panning, strokeChanged;
    private int lastCx = -1, lastCy = -1;
    private float lastFx, lastFy;

    public GridView(Context ctx) {
        super(ctx);
        renderer = new LevelRenderer(ctx);
        preview.setStyle(Paint.Style.STROKE);
        preview.setColor(Ui.YELLOW);
        preview.setStrokeWidth(Ui.dp(ctx, 2));
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
    public void setBrush(int cell) { brush = cell; }
    public int brush() { return brush; }
    public void setMode(Mode m) { mode = m; }
    public Mode mode() { return mode; }
    public void setGrid(boolean g) { showGrid = g; invalidate(); }
    public LevelRenderer renderer() { return renderer; }
    public void setListener(Listener l) { listener = l; }

    private int flashX = -1, flashY;
    private long flashUntil;

    /** Centre la vue sur une case et l'encadre quelques secondes (panneau des problèmes). */
    public void focus(int x, int y) {
        if (level == null || !level.inside(x, y)) return;
        cell = Math.max(cell, 28);
        offX = getWidth() / 2f - (x + 0.5f) * cell;
        offY = getHeight() / 2f - (y + 0.5f) * cell;
        fitted = true;
        flashX = x; flashY = y;
        flashUntil = android.os.SystemClock.uptimeMillis() + 2500;
        invalidate();
    }

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
                renderer.drawCell(c, level, x, y, offX + x * cell, offY + y * cell, cell);
        if (showGrid && cell >= 14) {
            for (int x = x0; x <= x1 + 1; x++) c.drawLine(offX + x * cell, offY + y0 * cell, offX + x * cell, offY + (y1 + 1) * cell, gridPaint);
            for (int y = y0; y <= y1 + 1; y++) c.drawLine(offX + x0 * cell, offY + y * cell, offX + (x1 + 1) * cell, offY + y * cell, gridPaint);
        }
        if (startX >= 0 && mode == Mode.RECT) {
            float l = offX + Math.min(startX, endX) * cell, t = offY + Math.min(startY, endY) * cell;
            c.drawRect(l, t, offX + (Math.max(startX, endX) + 1) * cell, offY + (Math.max(startY, endY) + 1) * cell, preview);
        } else if (startX >= 0 && mode == Mode.LINE) {
            c.drawLine(offX + (startX + 0.5f) * cell, offY + (startY + 0.5f) * cell, offX + (endX + 0.5f) * cell, offY + (endY + 0.5f) * cell, preview);
        }
        if (flashX >= 0 && android.os.SystemClock.uptimeMillis() < flashUntil) {
            c.drawRect(offX + flashX * cell, offY + flashY * cell, offX + (flashX + 1) * cell, offY + (flashY + 1) * cell, preview);
            postInvalidateDelayed(100);
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
                down(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (painting && listener != null) listener.onStrokeCancel();
                painting = false; panning = true;
                startX = -1;
                lastFx = focusX(e); lastFy = focusY(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (panning && e.getPointerCount() >= 2) {
                    float fx = focusX(e), fy = focusY(e);
                    offX += fx - lastFx; offY += fy - lastFy;
                    lastFx = fx; lastFy = fy;
                    invalidate();
                } else if (painting) {
                    move(e);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // le doigt restant ne repeint pas : on reste en mode déplacement jusqu'au relâchement complet
                lastFx = focusX(e); lastFy = focusY(e);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (painting && (mode == Mode.RECT || mode == Mode.LINE) && startX >= 0) commitShape();
                startX = -1;
                if (painting && listener != null) listener.onStrokeEnd(strokeChanged);
                painting = panning = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int cx(float sx) { return (int) Math.floor((sx - offX) / cell); }
    private int cy(float sy) { return (int) Math.floor((sy - offY) / cell); }

    private void down(float sx, float sy) {
        int x = cx(sx), y = cy(sy);
        if (listener != null && level.inside(x, y)) listener.onCursor(x, y);
        switch (mode) {
            case BRUSH: paintAt(sx, sy); break;
            case RECT: case LINE: startX = endX = x; startY = endY = y; invalidate(); break;
            case FILL: flood(x, y); break;
            case PICKER:
                if (level.inside(x, y) && listener != null) listener.onPicked(level.get(x, y));
                break;
            case ROTATE:
                if (level.inside(x, y)) {
                    int c = level.get(x, y), r = Cell.rotate(c);
                    if (r != c && level.set(x, y, r)) { strokeChanged = true; invalidate(); if (listener != null) listener.onLevelChanged(); }
                }
                break;
        }
    }

    private void move(MotionEvent e) {
        int x = cx(e.getX()), y = cy(e.getY());
        if (listener != null && level.inside(x, y)) listener.onCursor(x, y);
        if (mode == Mode.BRUSH && !Cell.is(brush, BlockType.ENTER) && !Cell.is(brush, BlockType.EXIT)) {
            for (int i = 0; i < e.getHistorySize(); i++) paintAt(e.getHistoricalX(i), e.getHistoricalY(i));
            paintAt(e.getX(), e.getY());
        } else if ((mode == Mode.RECT || mode == Mode.LINE) && startX >= 0 && (x != endX || y != endY)) {
            endX = x; endY = y;
            invalidate();
        }
    }

    /** Rectangle plein ou ligne, posés au relâchement. */
    private void commitShape() {
        if (mode == Mode.LINE) { line(startX, startY, endX, endY); return; }
        int x0 = Math.min(startX, endX), x1 = Math.max(startX, endX), y0 = Math.min(startY, endY), y1 = Math.max(startY, endY);
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) apply(x, y);
    }

    /** Remplissage 4-connexe de la zone de même bloc. */
    private void flood(int x, int y) {
        if (!level.inside(x, y)) return;
        int target = level.get(x, y);
        if (target == brush) return;
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{x, y});
        int n = 0;
        while (!q.isEmpty() && n < 70000) {
            int[] p = q.poll();
            if (!level.inside(p[0], p[1]) || level.get(p[0], p[1]) != target) continue;
            level.set(p[0], p[1], brush);
            n++;
            q.add(new int[]{p[0] + 1, p[1]}); q.add(new int[]{p[0] - 1, p[1]});
            q.add(new int[]{p[0], p[1] + 1}); q.add(new int[]{p[0], p[1] - 1});
        }
        if (n > 0) { strokeChanged = true; invalidate(); if (listener != null) listener.onLevelChanged(); }
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
        if (level.place(x, y, brush)) {
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
