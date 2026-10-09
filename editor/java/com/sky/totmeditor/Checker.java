package com.sky.totmeditor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Validation d'un niveau (problèmes localisés) et simulation simple du déplacement. */
public final class Checker {
    private Checker() {}

    public static final int ERROR = 2, WARN = 1, INFO = 0;

    public static final class Issue {
        public final int severity, x, y;
        public final String message;
        Issue(int severity, int x, int y, String message) { this.severity = severity; this.x = x; this.y = y; this.message = message; }
        @Override public String toString() { return (severity == ERROR ? "[erreur] " : severity == WARN ? "[attention] " : "[info] ") + message; }
    }

    public static List<Issue> check(Level l) {
        List<Issue> out = new ArrayList<>();
        if (l.section) {
            if (l.width != Level.SECTION_W) out.add(new Issue(ERROR, -1, -1, "Une section fait 13 cases de large (" + l.width + " actuellement)"));
            if (l.width * l.height > 255) out.add(new Issue(ERROR, -1, -1, "Une section fait 255 cases maximum (19 lignes)"));
        } else {
            unique(l, out, BlockType.ENTER, "départ");
            unique(l, out, BlockType.EXIT, "sortie");
            if (l.width > 255 || l.height > 255) out.add(new Issue(ERROR, -1, -1, "Taille max 255x255"));
        }
        groups(l, out);

        // pics sans mur adjacent, valeurs inconnues
        TreeMap<Integer, int[]> unknown = new TreeMap<>();
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++) {
                int c = l.cells[y][x];
                if (Cell.is(c, BlockType.SPIKES) && !wallAround(l, x, y, true))
                    out.add(new Issue(WARN, x, y, "Pics sans mur adjacent (" + (x + 1) + "," + (y + 1) + ") : le jeu ne saura pas les orienter"));
                if (Cell.is(c, BlockType.RAW) && !unknown.containsKey(Cell.rawValue(c))) unknown.put(Cell.rawValue(c), new int[]{x, y});
            }
        for (java.util.Map.Entry<Integer, int[]> e : unknown.entrySet())
            out.add(new Issue(INFO, e.getValue()[0], e.getValue()[1], "Valeur brute " + e.getKey() + " (hors enum, conservée telle quelle)"));

        if (!l.section) {
            int[] open = openBorder(l);
            if (open != null) out.add(new Issue(WARN, open[0], open[1], "Bord ouvert en (" + (open[0] + 1) + "," + (open[1] + 1) + ") : le personnage peut sortir du niveau"));
            int stars = l.count(BlockType.STAR);
            if (stars != 3) out.add(new Issue(WARN, -1, -1, stars + " étoile(s) : les niveaux officiels en ont 3"));
            if (l.count(BlockType.ENTER) == 1 && l.count(BlockType.EXIT) == 1) {
                Sim s = simulate(l);
                if (!s.exitReached) {
                    int[] e = l.find(BlockType.EXIT);
                    boolean basic = basicOnly(l);
                    out.add(new Issue(basic ? WARN : INFO, e[0], e[1], basic
                            ? "Sortie inaccessible : en glissant de mur en mur, le personnage ne l'atteint jamais"
                            : "Sortie non atteinte par la simulation simple (elle ignore trampolines, plateformes, glace, murs minutés...)"));
                }
            }
        }
        return out;
    }

    /** Messages d'une sévérité donnée (écrans qui n'ont pas besoin de la position). */
    public static List<String> messages(Level l, int severity) {
        List<String> out = new ArrayList<>();
        for (Issue i : check(l)) if (i.severity == severity) out.add(i.message);
        return out;
    }

    public static boolean hasErrors(List<Issue> issues) {
        for (Issue i : issues) if (i.severity == ERROR) return true;
        return false;
    }

    private static void unique(Level l, List<Issue> out, BlockType t, String what) {
        int n = l.count(t);
        if (n == 1) return;
        int[] p = l.find(t);
        out.add(new Issue(ERROR, p == null ? -1 : p[0], p == null ? -1 : p[1], "Il faut exactement 1 " + what + " (" + n + " actuellement)"));
    }

    /** Groupes appariés : portails (2 par numéro), serpents (entrée + sortie + déclencheur), boules (boule + déclencheur). */
    private static void groups(Level l, List<Issue> out) {
        int[][] cnt = new int[BlockType.values().length][16];
        int[][][] pos = new int[BlockType.values().length][16][];
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++) {
                int c = l.cells[y][x];
                BlockType t = Cell.type(c);
                if (!t.paired) continue;
                int g = Cell.group(c);
                cnt[t.ordinal()][g]++;
                if (pos[t.ordinal()][g] == null) pos[t.ordinal()][g] = new int[]{x, y};
            }
        for (int g = 1; g <= 7; g++) {
            int n = cnt[BlockType.PORTAL.ordinal()][g];
            if (n != 0 && n != 2) {
                int[] p = pos[BlockType.PORTAL.ordinal()][g];
                out.add(new Issue(ERROR, p[0], p[1], "Portail n°" + g + " : " + n + " portail(s), il en faut 2"));
            }
        }
        for (int g = 1; g <= 6; g++) {
            int e = cnt[BlockType.SNAKE_ENTER.ordinal()][g], x = cnt[BlockType.SNAKE_EXIT.ordinal()][g], t = cnt[BlockType.SNAKE_TRIGGER.ordinal()][g];
            if ((e | x | t) != 0 && (e != 1 || x != 1 || t != 1)) {
                int[] p = first(pos, g, BlockType.SNAKE_ENTER, BlockType.SNAKE_EXIT, BlockType.SNAKE_TRIGGER);
                out.add(new Issue(ERROR, p[0], p[1], "Serpent n°" + g + " incomplet : " + e + " entrée, " + x + " sortie, " + t + " déclencheur (il faut 1 de chaque)"));
            }
            int b = cnt[BlockType.LIGHTBALL.ordinal()][g], bt = cnt[BlockType.LIGHTBALL_TRIGGER.ordinal()][g];
            if ((b | bt) != 0 && (b != 1 || bt != 1)) {
                int[] p = first(pos, g, BlockType.LIGHTBALL, BlockType.LIGHTBALL_TRIGGER);
                out.add(new Issue(ERROR, p[0], p[1], "Boule lumineuse n°" + g + " : " + b + " boule, " + bt + " déclencheur (il faut 1 de chaque)"));
            }
        }
    }

    private static int[] first(int[][][] pos, int g, BlockType... ts) {
        for (BlockType t : ts) if (pos[t.ordinal()][g] != null) return pos[t.ordinal()][g];
        return new int[]{-1, -1};
    }

    /** Numéro de groupe libre le plus petit pour un type apparié (pour la palette). */
    public static int nextFreeGroup(Level l, BlockType t) {
        if (t.groups == 0) return 0;
        int[] n = new int[16];
        for (int[] row : l.cells) for (int c : row) if (Cell.type(c) == t) n[Cell.group(c)]++;
        int need = t == BlockType.PORTAL ? 2 : 1;
        for (int g = 1; g <= t.groups; g++) if (n[g] < need) return g;
        return 1;
    }

    /** Un mur (ou, si spikesCount, d'autres pics : les officiels en font des rangées) touche la case. */
    static boolean wallAround(Level l, int x, int y, boolean spikesCount) {
        int[][] d = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] k : d) {
            int nx = x + k[0], ny = y + k[1];
            if (!l.inside(nx, ny)) continue;
            int c = l.cells[ny][nx];
            if (Cell.solid(c) || spikesCount && Cell.is(c, BlockType.SPIKES)) return true;
        }
        return false;
    }

    /** Le niveau n'utilise que murs, vides, points, étoiles, pics, départ et sortie (simulation fiable). */
    public static boolean basicOnly(Level l) {
        for (int[] row : l.cells)
            for (int c : row)
                switch (Cell.type(c)) {
                    case NONE: case WALL: case WALL_EMPTY: case COIN: case DOT: case STAR: case SPIKES: case ENTER: case EXIT: break;
                    default: return false;
                }
        return true;
    }

    static int[] openBorder(Level l) {
        for (int x = 0; x < l.width; x++) {
            if (!Cell.solid(l.cells[0][x])) return new int[]{x, 0};
            if (!Cell.solid(l.cells[l.height - 1][x])) return new int[]{x, l.height - 1};
        }
        for (int y = 0; y < l.height; y++) {
            if (!Cell.solid(l.cells[y][0])) return new int[]{0, y};
            if (!Cell.solid(l.cells[y][l.width - 1])) return new int[]{l.width - 1, y};
        }
        return null;
    }

    // ------------------------------------------------------------ simulation
    /** Résultat : positions d'arrêt atteignables, cases traversées, sortie atteinte. */
    public static final class Sim {
        public boolean exitReached;
        public boolean[][] stops, visited;
        public int coinsTotal, coinsReached, starsReached, moves;
    }

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * Le personnage glisse jusqu'au prochain bloc solide (murs, glace, briques...). Simplifications :
     * ennemis, pics, portails, trampolines et lave ignorés ; le bord du niveau arrête le glissement.
     */
    public static Sim simulate(Level l) {
        Sim s = new Sim();
        s.stops = new boolean[l.height][l.width];
        s.visited = new boolean[l.height][l.width];
        int[] st = l.find(BlockType.ENTER);
        if (st == null) return s;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(st);
        s.stops[st[1]][st[0]] = true;
        s.visited[st[1]][st[0]] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : DIRS) {
                int x = p[0], y = p[1], guard = 0;
                while (l.inside(x + d[0], y + d[1]) && !Cell.solid(l.cells[y + d[1]][x + d[0]]) && guard++ < 4 * (l.width + l.height)) {
                    x += d[0]; y += d[1];
                    s.visited[y][x] = true;
                    int c = l.cells[y][x];
                    if (Cell.is(c, BlockType.EXIT)) s.exitReached = true;
                    if (Cell.is(c, BlockType.PORTAL)) {  // téléportation vers l'autre portail du même numéro, même direction
                        int[] o = partner(l, x, y, Cell.group(c));
                        if (o != null) { x = o[0]; y = o[1]; s.visited[y][x] = true; }
                    }
                }
                if (!s.stops[y][x]) { s.stops[y][x] = true; s.moves++; q.add(new int[]{x, y}); }
            }
        }
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++) {
                int c = l.cells[y][x];
                boolean coin = Cell.is(c, BlockType.COIN) || Cell.is(c, BlockType.DOT);
                if (coin) { s.coinsTotal++; if (s.visited[y][x]) s.coinsReached++; }
                if (Cell.is(c, BlockType.STAR) && s.visited[y][x]) s.starsReached++;
            }
        return s;
    }

    private static int[] partner(Level l, int px, int py, int g) {
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++)
                if ((x != px || y != py) && Cell.is(l.cells[y][x], BlockType.PORTAL) && Cell.group(l.cells[y][x]) == g) return new int[]{x, y};
        return null;
    }

    /** Remplit de points les cases vides que le personnage peut traverser (selon la simulation). */
    public static int fillReachableDots(Level l) {
        Sim s = simulate(l);
        int n = 0, d = l.dotCell();
        for (int y = 0; y < l.height; y++)
            for (int x = 0; x < l.width; x++)
                if (s.visited[y][x] && l.cells[y][x] == Cell.EMPTY) { l.cells[y][x] = d; n++; }
        return n;
    }
}
