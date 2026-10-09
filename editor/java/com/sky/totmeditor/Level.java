package com.sky.totmeditor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un niveau (stage du mode Stages ou section du mode Arcade) en blocs logiques ({@link Cell}).
 * cells[y][x], y = 0 en HAUT (sens d'affichage). L'encodage brut ne sert qu'au binaire et au texte.
 */
public final class Level {
    public static final int MIN_W = 5, MAX_W = 255, MIN_H = 5, MAX_H = 255, SECTION_W = 13;
    public static final String[] SECTION_TYPES = {"Begin", "Easy", "Spikes", "Bats", "Cannons", "Fishes", "Platforms",
            "Tramplins", "Hard", "Portals", "BonusEnter", "BonusRun", "Snake", "Tutorial", "BonusPipes"};

    public String name;
    public String author = "";
    public boolean section;     // false = stage
    public int sectionType = 1;
    public int width, height, lava;
    public int[][] cells;

    public Level(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
        cells = new int[height][width];
        for (int[] row : cells) java.util.Arrays.fill(row, Cell.WALL);
    }

    public Level copy(String newName) {
        Level l = new Level(newName, width, height);
        l.author = author; l.section = section; l.sectionType = sectionType; l.lava = lava;
        for (int y = 0; y < height; y++) l.cells[y] = cells[y].clone();
        return l;
    }

    /** Copie le contenu (taille, cases, lave) depuis un instantané, sans toucher au nom. */
    public void restoreFrom(Level o) {
        width = o.width; height = o.height; lava = o.lava; sectionType = o.sectionType; section = o.section;
        cells = new int[height][];
        for (int y = 0; y < height; y++) cells[y] = o.cells[y].clone();
    }

    public int get(int x, int y) { return cells[y][x]; }

    public boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    public boolean set(int x, int y, int c) {
        if (!inside(x, y) || cells[y][x] == c) return false;
        cells[y][x] = c;
        return true;
    }

    /** Pose en gardant le départ et la sortie uniques (l'ancien est remplacé par du vide). */
    public boolean place(int x, int y, int c) {
        if (!inside(x, y) || cells[y][x] == c) return false;
        BlockType t = Cell.type(c);
        if (t == BlockType.ENTER || t == BlockType.EXIT) {
            for (int[] row : cells) for (int i = 0; i < row.length; i++) if (Cell.is(row[i], t)) row[i] = Cell.EMPTY;
        }
        cells[y][x] = c;
        return true;
    }

    /** Redimensionne. ax/ay : ancrage (-1 gauche/haut, 0 centre, 1 droite/bas). Les nouvelles cases sont des murs. */
    public void resize(int nw, int nh, int ax, int ay) {
        int dx = ax < 0 ? 0 : ax > 0 ? nw - width : (nw - width) / 2;
        int dy = ay < 0 ? 0 : ay > 0 ? nh - height : (nh - height) / 2;
        int[][] t = new int[nh][nw];
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++) {
                int sx = x - dx, sy = y - dy;
                t[y][x] = (sy >= 0 && sy < height && sx >= 0 && sx < width) ? cells[sy][sx] : Cell.WALL;
            }
        width = nw; height = nh; cells = t;
    }

    public void sealBorder() {
        for (int x = 0; x < width; x++) { cells[0][x] = Cell.WALL; cells[height - 1][x] = Cell.WALL; }
        for (int y = 0; y < height; y++) { cells[y][0] = Cell.WALL; cells[y][width - 1] = Cell.WALL; }
    }

    /** Point « naturel » du mode : Coin (19) en stage, Dot (31) en section arcade. */
    public int dotCell() { return Cell.make(section ? BlockType.DOT : BlockType.COIN, 0, 0); }

    /** Remplit toutes les cases vides de points (comme les niveaux officiels). */
    public int fillDots() {
        int n = 0, d = dotCell();
        for (int[] row : cells) for (int i = 0; i < row.length; i++) if (row[i] == Cell.EMPTY) { row[i] = d; n++; }
        return n;
    }

    public int count(BlockType t) {
        int n = 0;
        for (int[] row : cells) for (int c : row) if (Cell.is(c, t)) n++;
        return n;
    }

    public int[] find(BlockType t) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (Cell.is(cells[y][x], t)) return new int[]{x, y};
        return null;
    }

    public void mirrorX() {
        for (int[] row : cells)
            for (int i = 0; i < row.length / 2; i++) { int a = row[i]; row[i] = row[row.length - 1 - i]; row[row.length - 1 - i] = a; }
        for (int[] row : cells) for (int i = 0; i < row.length; i++) row[i] = Cell.mirrorX(row[i]);
    }

    // ------------------------------------------------------------ format jeu
    /** Tuiles brutes, lignes de BAS en HAUT (ordre du jeu). */
    public byte[] rawTiles() {
        byte[] b = new byte[width * height];
        int i = 0;
        for (int y = height - 1; y >= 0; y--) for (int x = 0; x < width; x++) b[i++] = (byte) Cell.toRaw(cells[y][x]);
        return b;
    }

    /** Stage : [lava][w][h][tuiles] ; section : [type][w*h][tuiles]. */
    public byte[] toGameBinary() {
        byte[] t = rawTiles();
        int head = section ? 2 : 3;
        byte[] b = new byte[head + t.length];
        if (section) { b[0] = (byte) sectionType; b[1] = (byte) t.length; }
        else { b[0] = (byte) lava; b[1] = (byte) width; b[2] = (byte) height; }
        System.arraycopy(t, 0, b, head, t.length);
        return b;
    }

    /** Entrée « stages » servie par la lib native : une section est jouée comme un stage de 13 de large. */
    public byte[] toTestBinary() {
        byte[] t = rawTiles();
        byte[] b = new byte[3 + t.length];
        b[0] = (byte) (section ? 0 : lava); b[1] = (byte) width; b[2] = (byte) height;
        System.arraycopy(t, 0, b, 3, t.length);
        return b;
    }

    private static Level fromRaw(String name, int w, int h, byte[] data, int off) {
        Level l = new Level(name, w, h);
        int p = off;
        for (int y = h - 1; y >= 0; y--) for (int x = 0; x < w; x++) l.cells[y][x] = Cell.fromRaw(data[p++] & 0xff);
        return l;
    }

    /** Découpe le binaire "stages" complet (les 300 niveaux officiels). */
    public static List<Level> parseStages(byte[] data) {
        List<Level> out = new ArrayList<>();
        int i = 0;
        while (i + 3 <= data.length) {
            int lava = data[i] & 0xff, w = data[i + 1] & 0xff, h = data[i + 2] & 0xff;
            if (i + 3 + w * h > data.length) break;
            Level l = fromRaw("Stage " + (out.size() + 1), w, h, data, i + 3);
            l.lava = lava;
            out.add(l);
            i += 3 + w * h;
        }
        return out;
    }

    /** Découpe le binaire "sections" complet (les 517 morceaux du mode Arcade). */
    public static List<Level> parseSections(byte[] data) {
        List<Level> out = new ArrayList<>();
        int i = 0;
        while (i + 2 <= data.length) {
            int type = data[i] & 0xff, n = data[i + 1] & 0xff;
            if (i + 2 + n > data.length || n % SECTION_W != 0) break;
            Level l = fromRaw("Section " + out.size(), SECTION_W, n / SECTION_W, data, i + 2);
            l.section = true;
            l.sectionType = type;
            out.add(l);
            i += 2 + n;
        }
        return out;
    }

    // ------------------------------------------------------------ format texte (compatible totm_levels.py)
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("# kind=").append(section ? "sections" : "stages").append('\n');
        sb.append("# name=").append(oneLine(name)).append('\n');
        if (author != null && !author.isEmpty()) sb.append("# author=").append(oneLine(author)).append('\n');
        sb.append("# width=").append(width).append(" height=").append(height).append('\n');
        if (section) {
            sb.append("# type=").append(sectionType).append(" (")
                    .append(sectionType < SECTION_TYPES.length ? SECTION_TYPES[sectionType] : "?").append(")\n");
        } else {
            sb.append("# lava=").append(lava).append("   (0 = pas de lave montante, sinon vitesse = lava*0.01)\n");
        }
        sb.append("# lignes de haut en bas ; valeurs = LevelTileType\n");
        for (int[] row : cells) {
            for (int x = 0; x < row.length; x++) {
                String s = Integer.toString(Cell.toRaw(row[x]));
                for (int k = s.length(); k < 3; k++) sb.append(' ');
                sb.append(s);
                if (x < row.length - 1) sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String oneLine(String s) { return s.replace('\n', ' ').replace('\r', ' '); }

    private static final Pattern KV = Pattern.compile("(\\w+)=(\\d+)");

    public static Level fromText(String fallbackName, String text) {
        String name = fallbackName, author = "";
        int lava = 0, type = 1;
        boolean section = false;
        List<int[]> rows = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                if (line.startsWith("# name=")) { name = line.substring(7).trim(); continue; }
                if (line.startsWith("# author=")) { author = line.substring(9).trim(); continue; }
                if (line.startsWith("# kind=")) { section = line.substring(7).trim().startsWith("section"); continue; }
                Matcher m = KV.matcher(line);
                while (m.find()) {
                    if (m.group(1).equals("lava")) lava = Integer.parseInt(m.group(2));
                    if (m.group(1).equals("type")) type = Integer.parseInt(m.group(2));
                }
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
        if (w > 255 || rows.size() > 255) throw new IllegalArgumentException("taille max 255x255");
        Level l = new Level(name, w, rows.size());
        l.lava = lava; l.author = author; l.section = section; l.sectionType = type;
        for (int y = 0; y < rows.size(); y++) {
            for (int x = 0; x < w; x++) {
                int v = rows.get(y)[x];
                if (v < 0 || v > 255) throw new IllegalArgumentException("valeur hors 0..255 : " + v);
                l.cells[y][x] = Cell.fromRaw(v);
            }
        }
        return l;
    }

    /** Niveau de départ : murs partout, un couloir vertical avec départ en bas et sortie en haut. */
    public static Level starter(String name) {
        Level l = new Level(name, 20, 30);
        int cx = 10;
        for (int y = 3; y <= 26; y++) l.cells[y][cx] = l.dotCell();
        l.cells[26][cx] = Cell.make(BlockType.ENTER, 0, 0);
        l.cells[3][cx] = Cell.make(BlockType.EXIT, 0, 0);
        l.cells[14][cx] = Cell.make(BlockType.STAR, 0, 0);
        l.cells[8][cx] = Cell.make(BlockType.STAR, 0, 0);
        l.cells[20][cx] = Cell.make(BlockType.STAR, 0, 0);
        return l;
    }
}
