package com.sky.totmeditor;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Stockage des niveaux : <filesDir>/levels/<id>.txt, et niveau de test : <filesDir>/test_level.bin. */
public final class LevelStore {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static List<Level> officialCache;

    private final Context ctx;
    private final File dir;

    public LevelStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        dir = new File(this.ctx.getFilesDir(), "levels");
        if (!dir.isDirectory()) dir.mkdirs();
    }

    public static final class Entry {
        public final String id;
        public final Level level;
        public final long modified;
        Entry(String id, Level level, long modified) { this.id = id; this.level = level; this.modified = modified; }
    }

    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        for (File f : files) {
            if (!f.getName().endsWith(".txt")) continue;
            String id = f.getName().substring(0, f.getName().length() - 4);
            try {
                out.add(new Entry(id, Level.fromText(id, readText(f)), f.lastModified()));
            } catch (Exception ignored) {
                // fichier corrompu : ignoré
            }
        }
        return out;
    }

    public Level load(String id) throws IOException {
        return Level.fromText(id, readText(new File(dir, id + ".txt")));
    }

    public String newId() {
        String base = "niveau_" + System.currentTimeMillis();
        String id = base;
        for (int i = 2; new File(dir, id + ".txt").exists(); i++) id = base + "_" + i;
        return id;
    }

    public void save(String id, Level level) throws IOException {
        File tmp = new File(dir, id + ".tmp");
        writeBytes(tmp, level.toText().getBytes(UTF8));
        File dst = new File(dir, id + ".txt");
        if (!tmp.renameTo(dst)) { writeBytes(dst, level.toText().getBytes(UTF8)); tmp.delete(); }
    }

    public void delete(String id) { new File(dir, id + ".txt").delete(); }

    // ------------------------------------------------------------ niveau de test lu par la lib native
    public File testFile() { return new File(ctx.getFilesDir(), "test_level.bin"); }

    public void setTestLevel(Level l) throws IOException { writeBytes(testFile(), l.toTestBinary()); }

    public boolean clearTestLevel() { return testFile().delete(); }

    public boolean hasTestLevel() { return testFile().isFile(); }

    // ------------------------------------------------------------ niveaux officiels (asset embarqué)
    public synchronized List<Level> officialStages() {
        if (officialCache == null) {
            try (InputStream in = ctx.getAssets().open("totm_editor/stages.bytes")) {
                officialCache = Level.parseStages(readAll(in));
            } catch (IOException e) {
                officialCache = new ArrayList<>();
            }
        }
        return officialCache;
    }

    // ------------------------------------------------------------ utilitaires
    static String readText(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) { return new String(readAll(in), UTF8); }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    static void writeBytes(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); out.getFD().sync(); }
    }
}
