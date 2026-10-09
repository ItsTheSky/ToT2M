package com.sky.totmeditor;

/**
 * Cellule logique empaquetée dans un int : type (8 bits) | variante (4 bits) &lt;&lt; 8 | groupe (4 bits) &lt;&lt; 12 |
 * valeur brute (8 bits) &lt;&lt; 16 pour les blocs RAW. Un int par case : copies et annulation bon marché.
 */
public final class Cell {
    private Cell() {}

    private static final BlockType[] TYPES = BlockType.values();

    public static final int EMPTY = make(BlockType.NONE, 0, 0);
    public static final int WALL = make(BlockType.WALL, 0, 0);

    public static int make(BlockType t, int variant, int group) {
        return t.ordinal() | (variant & 0xF) << 8 | (group & 0xF) << 12;
    }

    public static int rawCell(int raw) { return BlockType.RAW.ordinal() | (raw & 0xFF) << 16; }

    public static BlockType type(int c) { return TYPES[c & 0xFF]; }
    public static int variant(int c) { return (c >> 8) & 0xF; }
    public static int group(int c) { return (c >> 12) & 0xF; }
    public static int rawValue(int c) { return (c >> 16) & 0xFF; }

    public static boolean is(int c, BlockType t) { return (c & 0xFF) == t.ordinal(); }

    /** Encodage vers la valeur LevelTileType du jeu. */
    public static int toRaw(int c) {
        BlockType t = type(c);
        if (t == BlockType.RAW) return rawValue(c);
        int r = t.raw(variant(c), group(c));
        if (r < 0) throw new IllegalStateException("bloc invalide : " + describe(c));
        return r;
    }

    public static int fromRaw(int raw) { return BlockType.decode(raw); }

    public static int withVariant(int c, int v) { return (c & ~0xF00) | (v & 0xF) << 8; }
    public static int withGroup(int c, int g) { return (c & ~0xF000) | (g & 0xF) << 12; }

    public static int rotate(int c) { return withVariant(c, type(c).rotate(variant(c))); }
    public static int mirrorX(int c) { return withVariant(c, type(c).mirrorX(variant(c))); }
    public static int mirrorY(int c) { return withVariant(c, type(c).mirrorY(variant(c))); }

    /** Bloc qui arrête un déplacement (le personnage glisse jusqu'au premier de ces blocs). */
    public static boolean solid(int c) {
        switch (type(c)) {
            case WALL: case WALL_EMPTY: case CLOSE_BRICK: case TIMER_WALL: case BONUS_WALL: case ICE: case MSPIKES:
                return true;
            default:
                return false;
        }
    }

    public static boolean isWall(int c) { return is(c, BlockType.WALL); }

    public static String describe(int c) {
        BlockType t = type(c);
        if (t == BlockType.RAW) return "Brut " + rawValue(c);
        StringBuilder sb = new StringBuilder(t.label);
        String v = t.variantName(variant(c));
        if (!v.isEmpty()) sb.append(" (").append(v).append(')');
        if (t.groups > 0) sb.append(" n°").append(group(c));
        return sb.toString();
    }
}
