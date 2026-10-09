package com.sky.totmeditor;

/**
 * Blocs logiques manipulés par l'éditeur. Chaque bloc a une « variante » (direction, côté, sens,
 * orientation ou type de bonus) et éventuellement un numéro de groupe ; l'encodage en valeur brute
 * LevelTileType se fait par {@link #raw(int, int)}. Les valeurs inconnues restent des blocs {@link #RAW}.
 */
public enum BlockType {
    //    libellé             catégorie        rotation   groupes appariés  valeurs brutes [variante][groupe-1]
    NONE("Vide", Cat.STRUCTURE, Rot.NONE, 0, false, r(0)),
    WALL("Mur", Cat.STRUCTURE, Rot.NONE, 0, false, r(3)),
    ENTER("Départ", Cat.STRUCTURE, Rot.NONE, 0, false, r(1)),
    EXIT("Sortie", Cat.STRUCTURE, Rot.NONE, 0, false, r(2)),
    WALL_EMPTY("Mur vide", Cat.STRUCTURE, Rot.NONE, 0, false, r(5)),
    ICE("Glace", Cat.STRUCTURE, Rot.NONE, 0, false, r(7)),
    CLOSE_BRICK("Brique", Cat.STRUCTURE, Rot.NONE, 0, false, r(12)),
    TIMER_WALL("Mur minuté", Cat.STRUCTURE, Rot.NONE, 0, false, r(121)),
    BONUS_DOOR("Porte bonus", Cat.STRUCTURE, Rot.NONE, 0, false, r(6)),
    BONUS_WALL("Mur bonus", Cat.STRUCTURE, Rot.NONE, 0, false, r(114)),
    ZONE_EXIT("Fin de zone", Cat.STRUCTURE, Rot.NONE, 0, false, r(43)),

    COIN("Point", Cat.COLLECT, Rot.NONE, 0, false, r(19)),
    DOT("Point arcade", Cat.COLLECT, Rot.NONE, 0, false, r(31)),
    STAR("Étoile", Cat.COLLECT, Rot.NONE, 0, false, r(4)),
    POWERUP("Bonus", Cat.COLLECT, Rot.POWER4, 0, false, new int[][]{{145}, {146}, {147}, {148}}),

    SPIKES("Pics", Cat.TRAP, Rot.NONE, 0, false, r(8)),
    MSPIKES("Pics mobiles", Cat.TRAP, Rot.NONE, 0, false, r(98)),
    BONUS_TEETH("Dents", Cat.TRAP, Rot.SIDE2, 0, false, new int[][]{{0}, {113}, {0}, {115}}),

    BAT("Chauve-souris", Cat.ENEMY, Rot.DIR4, 0, false, new int[][]{{88}, {76}, {89}, {77}}),
    CANNON("Canon", Cat.ENEMY, Rot.DIR4, 0, false, new int[][]{{85}, {74}, {97}, {73}}),
    FISH("Poisson", Cat.ENEMY, Rot.NONE, 0, false, r(102)),
    SNAKE_ENTER("Serpent : entrée", Cat.ENEMY, Rot.NONE, 6, true, new int[][]{{80, 92, 104, 116, 128, 140}}),
    SNAKE_EXIT("Serpent : sortie", Cat.ENEMY, Rot.NONE, 6, true, new int[][]{{81, 93, 105, 117, 129, 141}}),
    SNAKE_TRIGGER("Serpent : déclencheur", Cat.ENEMY, Rot.NONE, 6, true, new int[][]{{82, 94, 106, 118, 130, 142}}),
    MONKEY_TRIGGER("Singe : déclencheur", Cat.ENEMY, Rot.NONE, 3, false, new int[][]{{13, 14, 15}}),

    PORTAL("Portail", Cat.MECHA, Rot.HV2, 7, true, new int[][]{{49, 50, 51, 52, 53, 54, 55}, {61, 62, 63, 64, 65, 66, 67}}),
    PLATFORM("Plateforme", Cat.MECHA, Rot.DIR4, 0, false, new int[][]{{90}, {78}, {91}, {79}}),
    TRAMPLIN("Trampoline", Cat.MECHA, Rot.DIAG4, 0, false, new int[][]{{68}, {56}, {57}, {69}}),
    ROTATION("Rotation", Cat.MECHA, Rot.SENS2, 0, false, new int[][]{{109}, {110}}),
    LIGHTBALL("Boule lumineuse", Cat.MECHA, Rot.NONE, 6, true, new int[][]{{24, 36, 48, 60, 72, 84}}),
    LIGHTBALL_TRIGGER("Boule : déclencheur", Cat.MECHA, Rot.NONE, 6, true, new int[][]{{23, 35, 47, 59, 71, 83}}),

    PLAYER("Joueur", Cat.RAW, Rot.NONE, 0, false, r(255)),
    RAW("Brut", Cat.RAW, Rot.NONE, 0, false, new int[][]{{-1}});

    public enum Cat {
        STRUCTURE("Structure"), COLLECT("Collectibles"), TRAP("Pièges"), ENEMY("Ennemis"), MECHA("Mécanismes"), RAW("Avancé");
        public final String label;
        Cat(String l) { label = l; }
    }

    /** Type de variante : DIR4 = haut/droite/bas/gauche, DIAG4 = HD/BD/BG/HG, SIDE2 = droite/gauche (variantes 1 et 3). */
    public enum Rot {
        NONE(1), DIR4(4), DIAG4(4), SIDE2(4), SENS2(2), HV2(2), POWER4(4);
        public final int count;
        Rot(int c) { count = c; }
    }

    public static final String[] DIR_NAMES = {"haut", "droite", "bas", "gauche"};
    public static final String[] DIAG_NAMES = {"haut-droite", "bas-droite", "bas-gauche", "haut-gauche"};
    public static final String[] POWER_NAMES = {"Pièces x2", "Gel", "Aimant", "Score x2"};

    public final String label;
    public final Cat cat;
    public final Rot rot;
    public final int groups;
    public final boolean paired;
    private final int[][] raw;

    BlockType(String label, Cat cat, Rot rot, int groups, boolean paired, int[][] raw) {
        this.label = label; this.cat = cat; this.rot = rot; this.groups = groups; this.paired = paired; this.raw = raw;
    }

    private static int[][] r(int v) { return new int[][]{{v}}; }

    /** Valeur brute pour (variante, groupe 1..n), -1 si la combinaison n'existe pas. */
    public int raw(int variant, int group) {
        if (variant < 0 || variant >= raw.length) return -1;
        int[] row = raw[variant];
        int g = groups > 0 ? group - 1 : 0;
        if (g < 0 || g >= row.length) return -1;
        return row[g] == 0 && this != NONE ? -1 : row[g];
    }

    public boolean validVariant(int v) { return raw(v, groups > 0 ? 1 : 0) >= 0; }

    /** Variante suivante dans le sens horaire (rotation d'un quart de tour). */
    public int rotate(int v) {
        switch (rot) {
            case DIR4: case DIAG4: return (v + 1) & 3;
            case SIDE2: return v == 1 ? 3 : 1;
            case SENS2: case HV2: return v ^ 1;
            default: return v;
        }
    }

    /** Variante après un miroir gauche/droite. */
    public int mirrorX(int v) {
        switch (rot) {
            case DIR4: return v == 1 ? 3 : v == 3 ? 1 : v;
            case DIAG4: return 3 - v;
            case SIDE2: return v == 1 ? 3 : 1;
            case SENS2: return v ^ 1;
            default: return v;
        }
    }

    /** Variante après un miroir haut/bas. */
    public int mirrorY(int v) {
        switch (rot) {
            case DIR4: return v == 0 ? 2 : v == 2 ? 0 : v;
            case DIAG4: return v == 0 ? 1 : v == 1 ? 0 : v == 2 ? 3 : 2;
            case SENS2: return v ^ 1;
            default: return v;
        }
    }

    public int defaultVariant() { return rot == Rot.SIDE2 ? 1 : 0; }

    public String variantName(int v) {
        switch (rot) {
            case DIR4: return DIR_NAMES[v & 3];
            case DIAG4: return DIAG_NAMES[v & 3];
            case SIDE2: return v == 1 ? "droite" : "gauche";
            case SENS2: return v == 0 ? "horaire" : "anti-horaire";
            case HV2: return v == 0 ? "horizontal" : "vertical";
            case POWER4: return POWER_NAMES[v & 3];
            default: return "";
        }
    }

    // ------------------------------------------------------------ décodage brut -> logique
    private static final int[] DECODE = new int[256];

    static {
        java.util.Arrays.fill(DECODE, -1);
        for (BlockType t : values()) {
            if (t == RAW) continue;
            for (int v = 0; v < t.raw.length; v++)
                for (int g = 0; g < t.raw[v].length; g++) {
                    int rv = t.raw[v][g];
                    if (rv == 0 && t != NONE) continue;
                    if (DECODE[rv] != -1) throw new IllegalStateException("valeur brute en double : " + rv);
                    DECODE[rv] = Cell.make(t, v, t.groups > 0 ? g + 1 : 0);
                }
        }
        for (int v = 0; v < 256; v++) if (DECODE[v] == -1) DECODE[v] = Cell.rawCell(v);
    }

    /** Valeur brute (0..255) -> cellule logique. Les valeurs hors enum deviennent des blocs RAW. */
    public static int decode(int rawValue) { return DECODE[rawValue & 0xFF]; }
}
