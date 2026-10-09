package com.sky.totmeditor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Un niveau du mode Stages. tiles[y][x], y = 0 en HAUT (sens d'affichage). */
public final class Level {
    public static final int EMPTY = 0, ENTER = 1, EXIT = 2, WALL = 3, STAR = 4, SPIKES = 8, DOT = 19;
    public static final int MIN_W = 8, MAX_W = 64, MIN_H = 8, MAX_H = 128;

    public String name;
    public int width, height, lava;
    public int[][] tiles;

    public Level(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
        tiles = new int[height][width];
        for (int[] row : tiles) java.util.Arrays.fill(row, WALL);
    }

    public Level copy(String newName) {
        Level l = new Level(newName, width, height);
        l.lava = lava;
        for (int y = 0; y < height; y++) l.tiles[y] = tiles[y].clone();
        return l;
    }

    public int get(int x, int y) { return tiles[y][x]; }

    public boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    /** Pose une tuile ; départ et sortie sont uniques (l'ancien est remplacé par du vide). */
    public boolean set(int x, int y, int v) {
        if (!inside(x, y) || tiles[y][x] == v) return false;
        if (v == ENTER || v == EXIT) {
            for (int[] row : tiles) for (int i = 0; i < row.length; i++) if (row[i] == v) row[i] = EMPTY;
        }
        tiles[y][x] = v;
        return true;
    }

    /** Redimensionne en gardant le contenu ancré en BAS (là où se trouve en général le départ). */
    public void resize(int nw, int nh) {
        int[][] t = new int[nh][nw];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int sy = y - (nh - height);
                t[y][x] = (sy >= 0 && sy < height && x < width) ? tiles[sy][x] : WALL;
            }
        }
        width = nw; height = nh; tiles = t;
        sealBorder();
    }

    public void sealBorder() {
        for (int x = 0; x < width; x++) { tiles[0][x] = WALL; tiles[height - 1][x] = WALL; }
        for (int y = 0; y < height; y++) { tiles[y][0] = WALL; tiles[y][width - 1] = WALL; }
    }

    /** Remplit toutes les cases vides de points (comme les niveaux officiels). */
    public int fillDots() {
        int n = 0;
        for (int[] row : tiles) for (int i = 0; i < row.length; i++) if (row[i] == EMPTY) { row[i] = DOT; n++; }
        return n;
    }

    public int count(int v) {
        int n = 0;
        for (int[] row : tiles) for (int c : row) if (c == v) n++;
        return n;
    }

    /** Erreurs bloquantes avant un test en jeu. */
    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (count(ENTER) != 1) e.add("Il faut exactement 1 départ (" + count(ENTER) + " actuellement)");
        if (count(EXIT) != 1) e.add("Il faut exactement 1 sortie (" + count(EXIT) + " actuellement)");
        if (width > 255 || height > 255) e.add("Taille max 255x255");
        return e;
    }

    /** Avertissements non bloquants. */
    public List<String> warnings() {
        List<String> w = new ArrayList<>();
        boolean open = false;
        for (int x = 0; x < width; x++) open |= tiles[0][x] == EMPTY || tiles[0][x] == DOT || tiles[height - 1][x] == EMPTY || tiles[height - 1][x] == DOT;
        for (int y = 0; y < height; y++) open |= tiles[y][0] == EMPTY || tiles[y][0] == DOT || tiles[y][width - 1] == EMPTY || tiles[y][width - 1] == DOT;
        if (open) w.add("Le bord a des cases ouvertes : le personnage risque de sortir du niveau (menu ⋮ > Fermer les bords)");
        if (count(STAR) == 0) w.add("Aucune étoile (les officiels en ont 3)");
        return w;
    }

    // ------------------------------------------------------------ format jeu
    /** Entrée du TextAsset "stages" : [lava][w][h][tuiles, lignes de bas en haut]. */
    public byte[] toGameBinary() {
        byte[] b = new byte[3 + width * height];
        b[0] = (byte) lava; b[1] = (byte) width; b[2] = (byte) height;
        int i = 3;
        for (int y = height - 1; y >= 0; y--) for (int x = 0; x < width; x++) b[i++] = (byte) tiles[y][x];
        return b;
    }

    /** Découpe le binaire "stages" complet (les 300 niveaux officiels). */
    public static List<Level> parseStages(byte[] data) {
        List<Level> out = new ArrayList<>();
        int i = 0;
        while (i + 3 <= data.length) {
            int lava = data[i] & 0xff, w = data[i + 1] & 0xff, h = data[i + 2] & 0xff;
            if (i + 3 + w * h > data.length) break;
            Level l = new Level("Stage " + (out.size() + 1), w, h);
            l.lava = lava;
            int p = i + 3;
            for (int y = h - 1; y >= 0; y--) for (int x = 0; x < w; x++) l.tiles[y][x] = data[p++] & 0xff;
            out.add(l);
            i += 3 + w * h;
        }
        return out;
    }

    // ------------------------------------------------------------ format texte (compatible totm_levels.py)
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("# kind=stages\n");
        sb.append("# name=").append(name.replace('\n', ' ')).append('\n');
        sb.append("# width=").append(width).append(" height=").append(height).append('\n');
        sb.append("# lava=").append(lava).append("   (0 = pas de lave montante)\n");
        sb.append("# lignes de haut en bas ; valeurs = LevelTileType\n");
        for (int[] row : tiles) {
            for (int x = 0; x < row.length; x++) {
                String s = Integer.toString(row[x]);
                for (int k = s.length(); k < 3; k++) sb.append(' ');
                sb.append(s);
                if (x < row.length - 1) sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static final Pattern KV = Pattern.compile("(\\w+)=(\\d+)");

    public static Level fromText(String fallbackName, String text) {
        String name = fallbackName;
        int lava = 0;
        List<int[]> rows = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                if (line.startsWith("# name=")) { name = line.substring(7).trim(); continue; }
                Matcher m = KV.matcher(line);
                while (m.find()) if (m.group(1).equals("lava")) lava = Integer.parseInt(m.group(2));
                continue;
            }
            String[] parts = line.split("[\\s,]+");
            int[] r = new int[parts.length];
            for (int i = 0; i < parts.length; i++) r[i] = Integer.parseInt(parts[i]);
            rows.add(r);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("aucune ligne de tuiles");
        int w = rows.get(0).length;
        for (int[] r : rows) if (r.length != w) throw new IllegalArgumentException("lignes de longueurs différentes");
        Level l = new Level(name, w, rows.size());
        l.lava = lava;
        for (int y = 0; y < rows.size(); y++) {
            for (int x = 0; x < w; x++) {
                int v = rows.get(y)[x];
                if (v < 0 || v > 255) throw new IllegalArgumentException("valeur hors 0..255 : " + v);
                l.tiles[y][x] = v;
            }
        }
        return l;
    }

    /** Niveau de départ : murs partout, un couloir vertical avec départ en bas et sortie en haut. */
    public static Level starter(String name) {
        Level l = new Level(name, 20, 30);
        int cx = 10;
        for (int y = 3; y <= 26; y++) l.tiles[y][cx] = DOT;
        l.tiles[26][cx] = ENTER;
        l.tiles[3][cx] = EXIT;
        return l;
    }
}
