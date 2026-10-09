package com.sky.totmeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;

/** Écran d'édition d'un niveau. */
public class EditorActivity extends Activity implements GridView.Listener {
    public static final String EXTRA_ID = "level_id";
    private static final int MAX_UNDO = 60;

    private LevelStore store;
    private String id;
    private Level level;
    private GridView grid;
    private TextView title;
    private final Button[] toolButtons = new Button[Tiles.TOOLS.length];
    private final ArrayDeque<Level> undo = new ArrayDeque<>();
    private boolean dirty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        store = new LevelStore(this);
        id = getIntent().getStringExtra(EXTRA_ID);
        try {
            level = store.load(id);
        } catch (Exception e) {
            Ui.toast(this, "Niveau illisible : " + e.getMessage());
            finish();
            return;
        }
        buildUi();
        selectTool(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // barre du haut
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.BAR);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        title = Ui.text(this, "", 15, Ui.TEXT);
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(Ui.button(this, "Annuler", 0xFF3A3352, new View.OnClickListener() {
            @Override public void onClick(View v) { undo(); }
        }), Ui.wrap(this, 3));
        bar.addView(Ui.button(this, "Cadrer", 0xFF3A3352, new View.OnClickListener() {
            @Override public void onClick(View v) { grid.fit(); }
        }), Ui.wrap(this, 3));
        final Button more = Ui.button(this, "⋮", 0xFF3A3352, null);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        bar.addView(more, Ui.wrap(this, 3));
        bar.addView(Ui.button(this, "▶ Tester", Ui.ACCENT, new View.OnClickListener() {
            @Override public void onClick(View v) { testInGame(); }
        }), Ui.wrap(this, 3));
        root.addView(bar);

        // grille
        grid = new GridView(this);
        grid.setLevel(level);
        grid.setListener(this);
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // palette
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(Ui.BAR);
        LinearLayout pal = new LinearLayout(this);
        pal.setOrientation(LinearLayout.HORIZONTAL);
        pal.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        for (int i = 0; i < Tiles.TOOLS.length; i++) {
            final int idx = i;
            Button t = Ui.button(this, Tiles.TOOL_NAMES[i], 0xFF2A2540, new View.OnClickListener() {
                @Override public void onClick(View v) { selectTool(idx); }
            });
            toolButtons[i] = t;
            pal.addView(t, Ui.wrap(this, 3));
        }
        hs.addView(pal);
        root.addView(hs);

        TextView hint = Ui.text(this, "1 doigt : peindre · 2 doigts : déplacer / zoomer", 12, Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(Ui.BAR);
        hint.setPadding(0, 0, 0, Ui.dp(this, 6));
        root.addView(hint);

        setContentView(root);
        updateTitle();
    }

    private void selectTool(int idx) {
        grid.setTool(Tiles.TOOLS[idx]);
        for (int i = 0; i < toolButtons.length; i++) {
            int col = Tiles.color(Tiles.TOOLS[i]);
            boolean sel = i == idx;
            toolButtons[i].setBackground(Ui.round(sel ? col : 0xFF2A2540, Ui.dp(this, 10), col, Ui.dp(this, sel ? 0 : 2)));
            int lum = (int) (Color.red(col) * 0.3 + Color.green(col) * 0.59 + Color.blue(col) * 0.11);
            toolButtons[i].setTextColor(sel && lum > 140 ? Color.BLACK : Ui.TEXT);
        }
    }

    private void updateTitle() {
        title.setText(level.name + "  ·  " + level.width + "×" + level.height + (level.lava > 0 ? "  ·  lave " + level.lava : "") + (dirty ? "  •" : ""));
    }

    // ------------------------------------------------------------ annulation
    @Override public void onStrokeStart() { pushUndo(); }

    @Override public void onLevelChanged() {
        if (!dirty) { dirty = true; updateTitle(); }
    }

    @Override public void onStrokeEnd(boolean changed) {
        if (!changed) undo.pollLast();  // appui sans effet : pas d'étape d'annulation
        else trimUndo();
    }

    @Override public void onStrokeCancel() {
        Level prev = undo.pollLast();
        if (prev != null) restore(prev);
    }

    /** Empile un instantané ; la limite n'est appliquée qu'une fois la modification confirmée. */
    private void pushUndo() { undo.addLast(level.copy(level.name)); }

    private void trimUndo() { while (undo.size() > MAX_UNDO) undo.removeFirst(); }

    private void undo() {
        Level prev = undo.pollLast();
        if (prev == null) { Ui.toast(this, "Rien à annuler"); return; }
        restore(prev);
        dirty = true;
        updateTitle();
    }

    private void restore(Level prev) {
        boolean resized = prev.width != level.width || prev.height != level.height;
        level.width = prev.width; level.height = prev.height; level.lava = prev.lava; level.tiles = prev.tiles;
        if (resized) grid.setLevel(level); else grid.invalidate();  // ne pas perdre le zoom en cours
    }

    // ------------------------------------------------------------ menu
    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Taille et lave…");
        m.getMenu().add(0, 2, 0, "Remplir les vides de points");
        m.getMenu().add(0, 3, 0, "Fermer les bords (murs)");
        m.getMenu().add(0, 4, 0, "Renommer…");
        m.getMenu().add(0, 5, 0, "Exporter (texte)");
        m.getMenu().add(0, 6, 0, "Statut du hook");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1: sizeDialog(); break;
                    case 2: pushUndo(); trimUndo(); Ui.toast(EditorActivity.this, level.fillDots() + " points ajoutés"); changed(); break;
                    case 3: pushUndo(); trimUndo(); level.sealBorder(); changed(); break;
                    case 4: renameDialog(); break;
                    case 5: save(); Ui.share(EditorActivity.this, level); break;
                    case 6: Ui.alert(EditorActivity.this, "Statut", NativeBridge.statusText()); break;
                }
                return true;
            }
        });
        m.show();
    }

    private void changed() {
        dirty = true;
        grid.invalidate();
        updateTitle();
    }

    private void sizeDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        final NumberPicker w = Ui.picker(this, Level.MIN_W, Level.MAX_W, level.width);
        final NumberPicker h = Ui.picker(this, Level.MIN_H, Level.MAX_H, level.height);
        final NumberPicker lv = Ui.picker(this, 0, 40, level.lava);
        l.addView(Ui.labeled(this, "Largeur", w), Ui.wrap(this, 8));
        l.addView(Ui.labeled(this, "Hauteur", h), Ui.wrap(this, 8));
        l.addView(Ui.labeled(this, "Lave", lv), Ui.wrap(this, 8));
        new AlertDialog.Builder(this)
                .setTitle("Taille du niveau")
                .setMessage("Le contenu reste ancré en bas. Lave : 0 = pas de lave montante (officiels : 10 à 20).")
                .setView(l)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        w.clearFocus(); h.clearFocus(); lv.clearFocus();  // valide une saisie clavier en cours
                        pushUndo(); trimUndo();
                        if (w.getValue() != level.width || h.getValue() != level.height) level.resize(w.getValue(), h.getValue());
                        level.lava = lv.getValue();
                        grid.setLevel(level);
                        changed();
                    }
                }).show();
    }

    private void renameDialog() {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setText(level.name);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Nom du niveau").setView(e)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        String n = e.getText().toString().trim();
                        if (!n.isEmpty()) { level.name = n; changed(); }
                    }
                }).show();
    }

    // ------------------------------------------------------------ sauvegarde et test
    private boolean save() { return save(false); }

    private boolean save(boolean quiet) {
        try {
            store.save(id, level);
            dirty = false;
            updateTitle();
            return true;
        } catch (Exception e) {
            if (quiet || isFinishing()) Ui.toast(this, "Échec de la sauvegarde : " + e.getMessage());
            else Ui.alert(this, "Échec de la sauvegarde", e.toString());
            return false;
        }
    }

    private void testInGame() {
        List<String> errs = level.validate();
        if (!errs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : errs) sb.append("• ").append(s).append('\n');
            Ui.alert(this, "Niveau incomplet", sb.toString().trim());
            return;
        }
        List<String> warns = level.warnings();
        if (!warns.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : warns) sb.append("• ").append(s).append('\n');
            new AlertDialog.Builder(this).setTitle("Attention").setMessage(sb.toString().trim())
                    .setNegativeButton("Corriger", null)
                    .setPositiveButton("Tester quand même", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int which) { launchTest(); }
                    }).show();
            return;
        }
        launchTest();
    }

    private void launchTest() {
        if (!save()) return;
        try {
            store.setTestLevel(level);
        } catch (Exception e) {
            Ui.alert(this, "Impossible d'écrire le niveau de test", e.toString());
            return;
        }
        if (Ui.launchGame(this)) {
            Ui.toast(this, "Lance n'importe quel niveau du mode Stages : c'est « " + level.name + " » qui sera joué (la progression de ce stage sera comptée).\nReviens dans l'éditeur pour désactiver le test.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dirty && level != null) save(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // retour du jeu : on désactive le niveau de test pour que le jeu redevienne normal
        if (store != null) store.clearTestLevel();
    }
}
