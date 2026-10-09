package com.sky.totmeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Écran d'accueil de l'éditeur : liste des niveaux enregistrés. */
public class LevelListActivity extends Activity {
    private LevelStore store;
    private TextView status;
    private final List<LevelStore.Entry> entries = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        store = new LevelStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 14);
        root.setPadding(p, p, p, p);

        TextView title = Ui.text(this, "TotM Editor", 26, Ui.ACCENT);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);
        status = Ui.text(this, "", 12, Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 10));
        root.addView(status);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(Ui.button(this, "+ Nouveau", Ui.ACCENT, new View.OnClickListener() {
            @Override public void onClick(View v) { create(Level.starter("Nouveau niveau")); }
        }), Ui.wrap(this, 3));
        row1.addView(Ui.button(this, "Depuis un officiel", 0xFF3A3352, new View.OnClickListener() {
            @Override public void onClick(View v) { pickOfficial(); }
        }), Ui.wrap(this, 3));
        row1.addView(Ui.button(this, "Coller", 0xFF3A3352, new View.OnClickListener() {
            @Override public void onClick(View v) { importClipboard(); }
        }), Ui.wrap(this, 3));
        root.addView(row1);

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0));
        list.setDividerHeight(Ui.dp(this, 6));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int pos, long rowId) { open(entries.get(pos).id); }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long rowId) {
                itemMenu(view, entries.get(pos));
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lp.topMargin = Ui.dp(this, 8);
        root.addView(list, lp);

        root.addView(Ui.button(this, "Jouer au jeu (niveaux normaux)", 0xFF2A2540, new View.OnClickListener() {
            @Override public void onClick(View v) { store.clearTestLevel(); Ui.launchGame(LevelListActivity.this); }
        }), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.clearTestLevel();
        NativeBridge.stopTest();
        refresh();
    }

    private void refresh() {
        entries.clear();
        entries.addAll(store.list());
        adapter.notifyDataSetChanged();
        status.setText(NativeBridge.statusText() + "\nAppui long sur un niveau : dupliquer, exporter, supprimer.");
    }

    private void create(Level l) {
        String id = store.newId();
        try {
            store.save(id, l);
            open(id);
        } catch (Exception e) {
            Ui.alert(this, "Erreur", e.toString());
        }
    }

    private void open(String id) {
        startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_ID, id));
    }

    private void pickOfficial() {
        final List<Level> off = store.officialStages();
        if (off.isEmpty()) { Ui.alert(this, "Indisponible", "Les niveaux officiels ne sont pas embarqués dans cet APK."); return; }
        final NumberPicker np = Ui.picker(this, 1, off.size(), 1);
        new AlertDialog.Builder(this).setTitle("Copier un niveau officiel").setView(np)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Copier", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        np.clearFocus();
                        Level src = off.get(np.getValue() - 1);
                        create(src.copy("Stage " + np.getValue() + " (copie)"));
                    }
                }).show();
    }

    private void importClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0) { Ui.toast(this, "Presse-papiers vide"); return; }
        CharSequence t = clip.getItemAt(0).coerceToText(this);
        try {
            create(Level.fromText("Niveau importé", t.toString()));
        } catch (Exception e) {
            Ui.alert(this, "Texte non reconnu", "Colle un niveau exporté par l'éditeur ou par totm_levels.py.\n\n" + e.getMessage());
        }
    }

    private void itemMenu(View anchor, final LevelStore.Entry en) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Dupliquer");
        m.getMenu().add(0, 2, 0, "Exporter (texte)");
        m.getMenu().add(0, 3, 0, "Supprimer");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        try { store.save(store.newId(), en.level.copy(en.level.name + " (copie)")); } catch (Exception e) { Ui.alert(LevelListActivity.this, "Erreur", e.toString()); }
                        refresh();
                        break;
                    case 2:
                        Ui.share(LevelListActivity.this, en.level);
                        break;
                    case 3:
                        new AlertDialog.Builder(LevelListActivity.this).setTitle("Supprimer « " + en.level.name + " » ?")
                                .setNegativeButton("Annuler", null)
                                .setPositiveButton("Supprimer", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int which) { store.delete(en.id); refresh(); }
                                }).show();
                        break;
                }
                return true;
            }
        });
        m.show();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int i) { return entries.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convert;
            if (row == null) {
                Context c = LevelListActivity.this;
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.VERTICAL);
                int p = Ui.dp(c, 12);
                row.setPadding(p, p, p, p);
                row.addView(Ui.text(c, "", 17, Ui.TEXT));
                row.addView(Ui.text(c, "", 12, Ui.MUTED));
                ListView.LayoutParams lp = new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(lp);
            }
            LevelStore.Entry e = entries.get(i);
            row.setBackground(Ui.round(0xFF1E1A2E, Ui.dp(row.getContext(), 10), 0, 0));
            ((TextView) row.getChildAt(0)).setText(e.level.name);
            List<String> errs = Checker.messages(e.level, Checker.ERROR);
            ((TextView) row.getChildAt(1)).setText(e.level.width + "×" + e.level.height
                    + " · " + (e.level.count(BlockType.COIN) + e.level.count(BlockType.DOT)) + " points · " + e.level.count(BlockType.STAR) + " étoiles"
                    + (errs.isEmpty() ? " · prêt" : " · incomplet")
                    + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(e.modified)));
            row.setGravity(Gravity.START);
            return row;
        }
    }
}
