package com.sky.totmeditor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** « Nouveau depuis un officiel » : grille des 300 stages avec vignettes, recherche par numéro. */
public class OfficialPickerActivity extends Activity {
    public static final String EXTRA_INDEX = "official_index";

    private List<Level> all;
    private final List<Integer> shown = new ArrayList<>();
    private LevelRenderer renderer;
    private final LruCache<Integer, Bitmap> thumbs = new LruCache<Integer, Bitmap>(8 << 20) {
        @Override protected int sizeOf(Integer k, Bitmap b) { return b.getByteCount(); }
    };
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fullscreenDark(this);
        all = new LevelStore(this).officialStages();
        renderer = new LevelRenderer(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        root.addView(Ui.title(this, "Stages officiels"));

        final EditText search = new EditText(this);
        search.setHint("Numéro du stage (1 à " + all.size() + ")");
        search.setInputType(InputType.TYPE_CLASS_NUMBER);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.MUTED);
        search.setTypeface(Ui.FONT);
        search.setBackground(Ui.frame(Ui.PANEL2, Ui.CYAN, Ui.dp(this, 2), Ui.dp(this, 3)));
        search.setPadding(p, p, p, p);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { filter(s.toString().trim()); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.setMargins(0, p, 0, p);
        root.addView(search, sl);

        if (all.isEmpty()) {
            root.addView(Ui.text(this, "Les niveaux officiels ne sont pas embarqués dans cet APK.", 14, Ui.MUTED));
            setContentView(root);
            return;
        }

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 96));
        grid.setVerticalSpacing(Ui.dp(this, 8));
        grid.setHorizontalSpacing(Ui.dp(this, 8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                Ui.haptic(v);
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_INDEX, shown.get(pos)));
                finish();
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        filter("");
    }

    private void filter(String q) {
        shown.clear();
        for (int i = 0; i < all.size(); i++) if (q.isEmpty() || Integer.toString(i + 1).startsWith(q)) shown.add(i);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private Bitmap thumb(int i) {
        Bitmap b = thumbs.get(i);
        if (b == null) { b = renderer.thumbnail(all.get(i)); thumbs.put(i, b); }
        return b;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return shown.get(i); }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) convert;
            if (cell == null) {
                cell = Ui.column(OfficialPickerActivity.this);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                int p = Ui.dp(OfficialPickerActivity.this, 6);
                cell.setPadding(p, p, p, p);
                cell.setBackground(Ui.neon(OfficialPickerActivity.this, Ui.MAGENTA, false));
                ImageView img = new ImageView(OfficialPickerActivity.this);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(OfficialPickerActivity.this, 110)));
                cell.addView(Ui.text(OfficialPickerActivity.this, "", 13, Ui.YELLOW));
                cell.addView(Ui.text(OfficialPickerActivity.this, "", 11, Ui.MUTED));
            }
            int i = shown.get(pos);
            Level l = all.get(i);
            BitmapDrawable d = new BitmapDrawable(getResources(), thumb(i));
            d.setFilterBitmap(false);  // pixels nets
            ((ImageView) cell.getChildAt(0)).setImageDrawable(d);
            ((TextView) cell.getChildAt(1)).setText("STAGE " + (i + 1));
            ((TextView) cell.getChildAt(2)).setText(l.width + "×" + l.height + (l.lava > 0 ? " · lave " + l.lava : ""));
            return cell;
        }
    }
}
