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
    private static final GridView.Mode[] MODES = GridView.Mode.values();
    private static final String[] MODE_NAMES = {"Pinceau", "Rect.", "Ligne", "Remplir", "Pipette", "↻ Tourner"};
    private final Button[] modeButtons = new Button[MODES.length];
    private final List<Button> catButtons = new java.util.ArrayList<>();
    private LinearLayout blockRow;
    private TextView current;
    private Button groupButton;
    private BlockType.Cat cat = BlockType.Cat.STRUCTURE;
    private BlockType curType = BlockType.WALL;
    private int curVariant, curGroup = 1;
    private final ArrayDeque<Level> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Button problemsButton;
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
        selectMode(GridView.Mode.BRUSH);
        selectCat(BlockType.Cat.STRUCTURE);
        selectBlock(BlockType.WALL);
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
        bar.addView(Ui.button(this, "↶", Ui.CYAN, new View.OnClickListener() {
            @Override public void onClick(View v) { undo(); }
        }), Ui.wrap(this, 2));
        bar.addView(Ui.button(this, "↷", Ui.CYAN, new View.OnClickListener() {
            @Override public void onClick(View v) { redo(); }
        }), Ui.wrap(this, 2));
        problemsButton = Ui.button(this, "⚠", Ui.MAGENTA, new View.OnClickListener() {
            @Override public void onClick(View v) { showProblems(); }
        });
        bar.addView(problemsButton, Ui.wrap(this, 2));
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

        // bloc courant : nom, rotation, groupe, gomme
        LinearLayout cur = Ui.row(this);
        cur.setBackgroundColor(Ui.BAR);
        cur.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        current = Ui.text(this, "", 13, Ui.YELLOW);
        cur.addView(current, Ui.weight(this, 2));
        cur.addView(Ui.button(this, "↻", Ui.CYAN, new View.OnClickListener() {
            @Override public void onClick(View v) { rotateBrush(); }
        }), Ui.wrap(this, 2));
        groupButton = Ui.button(this, "N°1", Ui.MAGENTA, new View.OnClickListener() {
            @Override public void onClick(View v) { nextGroup(); }
        });
        cur.addView(groupButton, Ui.wrap(this, 2));
        cur.addView(Ui.button(this, "Gomme", Ui.MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { selectMode(GridView.Mode.BRUSH); selectBlock(BlockType.NONE); }
        }), Ui.wrap(this, 2));
        root.addView(cur);

        // outils
        LinearLayout modes = Ui.row(this);
        for (int i = 0; i < MODES.length; i++) {
            final GridView.Mode m = MODES[i];
            modeButtons[i] = Ui.button(this, MODE_NAMES[i], Ui.CYAN, new View.OnClickListener() {
                @Override public void onClick(View v) { Ui.haptic(v); selectMode(m); }
            });
            modes.addView(modeButtons[i], Ui.wrap(this, 2));
        }
        root.addView(scroll(modes));

        // catégories puis blocs de la catégorie
        LinearLayout cats = Ui.row(this);
        for (final BlockType.Cat c : BlockType.Cat.values()) {
            Button b = Ui.button(this, c.label, Ui.MAGENTA, new View.OnClickListener() {
                @Override public void onClick(View v) { Ui.haptic(v); selectCat(c); }
            });
            catButtons.add(b);
            cats.addView(b, Ui.wrap(this, 2));
        }
        root.addView(scroll(cats));
        blockRow = Ui.row(this);
        root.addView(scroll(blockRow));

        setContentView(root);
        applyStyle();
        updateTitle();
        updateProblems();
    }

    private HorizontalScrollView scroll(View content) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(Ui.BAR);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(content);
        return hs;
    }

    private void selectMode(GridView.Mode m) {
        grid.setMode(m);
        for (int i = 0; i < MODES.length; i++) modeButtons[i].setSelected(MODES[i] == m);
    }

    private void selectCat(BlockType.Cat c) {
        cat = c;
        BlockType.Cat[] all = BlockType.Cat.values();
        for (int i = 0; i < all.length; i++) catButtons.get(i).setSelected(all[i] == c);
        blockRow.removeAllViews();
        for (final BlockType t : BlockType.values()) {
            if (t.cat != c || t == BlockType.RAW) continue;
            if (t == BlockType.DOT && !level.section) continue;  // Dot n'existe qu'en Arcade
            Button b = Ui.button(this, t.label, Ui.YELLOW, new View.OnClickListener() {
                @Override public void onClick(View v) { Ui.haptic(v); selectBlock(t); }
            });
            b.setTag(t);
            b.setSelected(t == curType);
            blockRow.addView(b, Ui.wrap(this, 2));
        }
        if (c == BlockType.Cat.RAW) {
            blockRow.addView(Ui.button(this, "Valeur brute…", Ui.RED, new View.OnClickListener() {
                @Override public void onClick(View v) { rawDialog(); }
            }), Ui.wrap(this, 2));
        }
    }

    private void selectBlock(BlockType t) {
        if (t != curType) {
            curType = t;
            curVariant = t.defaultVariant();
            curGroup = Checker.nextFreeGroup(level, t);
        }
        if (grid.mode() == GridView.Mode.PICKER || grid.mode() == GridView.Mode.ROTATE) selectMode(GridView.Mode.BRUSH);
        updateBrush();
    }

    private void updateBrush() {
        int c = Cell.make(curType, curVariant, curType.groups > 0 ? curGroup : 0);
        grid.setBrush(c);
        current.setText(Cell.describe(c));
        current.setTextColor(curType.groups > 0 ? Ui.groupColor(curGroup) : Ui.YELLOW);
        groupButton.setVisibility(curType.groups > 0 ? View.VISIBLE : View.GONE);
        groupButton.setText("N°" + curGroup);
        for (int i = 0; i < blockRow.getChildCount(); i++) blockRow.getChildAt(i).setSelected(blockRow.getChildAt(i).getTag() == curType);
    }

    /** Variante suivante (direction, côté, sens, orientation) avant de poser le bloc. */
    private void rotateBrush() {
        if (curType == BlockType.RAW) return;
        if (curType.rot == BlockType.Rot.POWER4) curVariant = (curVariant + 1) & 3;
        else curVariant = curType.rotate(curVariant);
        updateBrush();
    }

    private void nextGroup() {
        if (curType.groups == 0) return;
        curGroup = curGroup % Math.max(1, curType.groups) + 1;
        updateBrush();
    }

    private void rawDialog() {
        final NumberPicker np = Ui.picker(this, 0, 255, 16);
        Ui.dialog(this).setTitle("Bloc brut (valeur LevelTileType)").setView(np)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        np.clearFocus();
                        int c = Cell.fromRaw(np.getValue());
                        curType = Cell.type(c); curVariant = Cell.variant(c); curGroup = Math.max(1, Cell.group(c));
                        updateBrush();
                        if (Cell.is(c, BlockType.RAW)) { grid.setBrush(c); current.setText(Cell.describe(c)); }
                    }
                }).show();
    }

    // ------------------------------------------------------------ retour de la grille
    @Override public void onPicked(int c) {
        curType = Cell.type(c); curVariant = Cell.variant(c); curGroup = Math.max(1, Cell.group(c));
        if (curType != BlockType.RAW && curType.cat != cat) selectCat(curType.cat);
        updateBrush();
        if (Cell.is(c, BlockType.RAW)) { grid.setBrush(c); current.setText(Cell.describe(c)); }
        selectMode(GridView.Mode.BRUSH);
        Ui.haptic(grid);
    }

    @Override public void onCursor(int x, int y) { }

    private void updateTitle() {
        title.setText(level.name + "  ·  " + level.width + "×" + level.height + (level.lava > 0 ? "  ·  lave " + level.lava : "") + (dirty ? "  •" : ""));
    }

    // ------------------------------------------------------------ annulation
    @Override public void onStrokeStart() { pushUndo(); }

    @Override public void onLevelChanged() {
        if (!dirty) { dirty = true; updateTitle(); }
    }

    @Override public void onStrokeEnd(boolean changed) {
        if (!changed) { undo.pollLast(); return; }  // appui sans effet : pas d'étape d'annulation
        trimUndo();
        redo.clear();
        // paire complète (ex. 2e portail posé) : le pinceau passe au numéro libre suivant
        if (curType.paired) { curGroup = Checker.nextFreeGroup(level, curType); updateBrush(); }
        updateProblems();
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
        redo.addLast(level.copy(level.name));
        restore(prev);
        dirty = true;
        updateTitle();
        updateProblems();
    }

    private void redo() {
        Level next = redo.pollLast();
        if (next == null) { Ui.toast(this, "Rien à rétablir"); return; }
        undo.addLast(level.copy(level.name));
        restore(next);
        dirty = true;
        updateTitle();
    }

    private void restore(Level prev) {
        boolean resized = prev.width != level.width || prev.height != level.height;
        level.restoreFrom(prev);
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
        m.getMenu().add(0, 7, 0, "Cadrer tout le niveau");
        m.getMenu().add(0, 8, 0, Prefs.grid(this) ? "Masquer la grille" : "Afficher la grille");
        m.getMenu().add(0, 9, 0, Prefs.schema(this) ? "Rendu jeu (sprites)" : "Rendu schéma (icônes des devs)");
        m.getMenu().add(0, 10, 0, "Remplir de points les couloirs atteignables");
        m.getMenu().add(0, 11, 0, "Symétrie : miroir gauche/droite");
        m.getMenu().add(0, 12, 0, "Statistiques et simulation");
        m.getMenu().add(0, 13, 0, "Réglages du test…");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1: sizeDialog(); break;
                    case 2: pushUndo(); trimUndo(); Ui.toast(EditorActivity.this, level.fillDots() + " points ajoutés"); changed(); break;
                    case 3: pushUndo(); trimUndo(); level.sealBorder(); changed(); break;
                    case 4: renameDialog(); break;
                    case 5: save(); Ui.share(EditorActivity.this, level); break;
                    case 6: Ui.alert(EditorActivity.this, "Statut", NativeBridge.statusText()); break;
                    case 7: grid.fit(); break;
                    case 8: Prefs.setGrid(EditorActivity.this, !Prefs.grid(EditorActivity.this)); grid.setGrid(Prefs.grid(EditorActivity.this)); break;
                    case 9: Prefs.setSchema(EditorActivity.this, !Prefs.schema(EditorActivity.this)); grid.renderer().schema = Prefs.schema(EditorActivity.this); grid.invalidate(); break;
                    case 10: pushUndo(); trimUndo(); Ui.toast(EditorActivity.this, Checker.fillReachableDots(level) + " points ajoutés"); changed(); break;
                    case 11: pushUndo(); trimUndo(); level.mirrorX(); changed(); break;
                    case 12: showStats(); break;
                    case 13: testSettings(); break;
                }
                return true;
            }
        });
        m.show();
    }

    // ------------------------------------------------------------ problèmes, statistiques, réglages
    private void updateProblems() {
        List<Checker.Issue> is = Checker.check(level);
        int err = 0, warn = 0;
        for (Checker.Issue i : is) { if (i.severity == Checker.ERROR) err++; else if (i.severity == Checker.WARN) warn++; }
        problemsButton.setText(err + warn == 0 ? "✓" : "⚠ " + (err + warn));
        problemsButton.setTextColor(err > 0 ? Ui.RED : warn > 0 ? Ui.YELLOW : Ui.GREEN);
    }

    /** Liste des problèmes ; un appui centre la vue sur la case fautive. */
    private void showProblems() {
        final List<Checker.Issue> is = Checker.check(level);
        if (is.isEmpty()) { Ui.toast(this, "Aucun problème détecté"); return; }
        String[] items = new String[is.size()];
        for (int i = 0; i < items.length; i++) items[i] = is.get(i).toString();
        Ui.dialog(this).setTitle("Problèmes (" + is.size() + ")")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        Checker.Issue i = is.get(w);
                        if (i.x >= 0) grid.focus(i.x, i.y);
                    }
                }).setPositiveButton("Fermer", null).show();
    }

    private void showStats() {
        Checker.Sim sim = Checker.simulate(level);
        StringBuilder sb = new StringBuilder();
        sb.append(level.width).append(" × ").append(level.height).append(" cases");
        if (level.lava > 0) {
            // vitesse = lava * 0.01 unité/s ; une case = 0,12 unité (sprites de 12 px à 100 px/unité)
            double secs = level.height * 0.12 / (level.lava * 0.01);
            sb.append("\nLave : ").append(level.lava).append(" (≈ ").append(Math.round(secs)).append(" s pour monter tout le niveau)");
        }
        sb.append("\n");
        for (BlockType t : BlockType.values()) {
            int n = level.count(t);
            if (n > 0 && t != BlockType.WALL && t != BlockType.NONE) sb.append("\n").append(t.label).append(" : ").append(n);
        }
        sb.append("\n\nSimulation simple (glissement jusqu'au mur, portails) :")
          .append("\n  sortie ").append(sim.exitReached ? "atteinte" : "non atteinte")
          .append("\n  points traversés ").append(sim.coinsReached).append(" / ").append(sim.coinsTotal)
          .append("\n  étoiles traversées ").append(sim.starsReached).append(" / ").append(level.count(BlockType.STAR))
          .append("\n  positions d'arrêt ").append(sim.moves + 1);
        Ui.alert(this, "Statistiques", sb.toString());
    }

    private void testSettings() {
        LinearLayout l = Ui.column(this);
        int p = Ui.dp(this, 16);
        l.setPadding(p, p, p, 0);
        final NumberPicker st = Ui.picker(this, 1, 300, Prefs.supportStage(this) + 1);
        l.addView(Ui.labeled(this, "Stage support (son style de murs est utilisé)", st));
        final NumberPicker mode = Ui.picker(this, 0, Prefs.LAUNCH_NAMES.length - 1, Prefs.launchMode(this));
        mode.setDisplayedValues(Prefs.LAUNCH_NAMES);
        l.addView(Ui.labeled(this, "Lancement", mode));
        final android.widget.CheckBox auto = new android.widget.CheckBox(this);
        auto.setText("Retour automatique à l'éditeur après une victoire");
        auto.setTextColor(Ui.TEXT);
        auto.setChecked(Prefs.autoReturn(this));
        l.addView(auto);
        Ui.dialog(this).setTitle("Réglages du test").setView(l)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        st.clearFocus(); mode.clearFocus();
                        Prefs.setSupportStage(EditorActivity.this, st.getValue() - 1);
                        Prefs.setLaunchMode(EditorActivity.this, mode.getValue());
                        Prefs.setAutoReturn(EditorActivity.this, auto.isChecked());
                        applyStyle();
                    }
                }).show();
    }

    /** Le jeu prend le jeu de bords de mur (stage / 10) % 5 + 1 du stage support. */
    private void applyStyle() {
        LevelRenderer r = grid.renderer();
        int s = Prefs.supportStage(this);
        r.wallFamily = (s / 10) % 5 + 1;
        r.colorPair = s % Math.max(1, r.atlas.colorPairs.size());
        r.schema = Prefs.schema(this);
        grid.setGrid(Prefs.grid(this));
        grid.invalidate();
    }

    private void changed() {
        dirty = true;
        grid.invalidate();
        updateProblems();
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
                        if (w.getValue() != level.width || h.getValue() != level.height) { level.resize(w.getValue(), h.getValue(), 0, 1); level.sealBorder(); }
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
        List<String> errs = Checker.messages(level, Checker.ERROR);
        if (!errs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : errs) sb.append("• ").append(s).append('\n');
            Ui.alert(this, "Niveau incomplet", sb.toString().trim());
            return;
        }
        List<String> warns = Checker.messages(level, Checker.WARN);
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
        Prefs.setTestingId(this, id);
        NativeBridge.startTest(Prefs.supportStage(this), Prefs.launchMode(this));
        if (Ui.launchGame(this) && Prefs.launchMode(this) == Prefs.LAUNCH_MANUAL) {
            Ui.toast(this, "Lance n'importe quel niveau du mode Stages : c'est « " + level.name + " » qui sera joué.");
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
        NativeBridge.stopTest();
    }
}
