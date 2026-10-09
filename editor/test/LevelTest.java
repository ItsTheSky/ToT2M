import com.sky.totmeditor.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Tests JVM du modèle : java LevelTest <reference/levels> */
public class LevelTest {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  ÉCHEC ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : "reference/levels";
        byte[] stagesBin = Files.readAllBytes(Paths.get(dir, "stages.bytes"));
        byte[] sectionsBin = Files.readAllBytes(Paths.get(dir, "sections.bytes"));

        // ---- codec brut <-> logique
        boolean codec = true;
        int rawCount = 0;
        for (int v = 0; v < 256; v++) {
            int c = Cell.fromRaw(v);
            if (Cell.toRaw(c) != v) codec = false;
            if (Cell.is(c, BlockType.RAW)) rawCount++;
        }
        check(codec, "codec : les 256 valeurs brutes font l'aller-retour (" + (256 - rawCount) + " connues, " + rawCount + " en bloc brut)");
        check(Cell.is(Cell.fromRaw(16), BlockType.RAW) && Cell.is(Cell.fromRaw(229), BlockType.RAW), "valeurs inconnues 16 et 229 -> bloc brut");
        check(Cell.fromRaw(74) == Cell.make(BlockType.CANNON, 1, 0) && Cell.fromRaw(57) == Cell.make(BlockType.TRAMPLIN, 2, 0),
                "Canon droite = 74, Trampoline bas-gauche = 57");
        check(Cell.fromRaw(63) == Cell.make(BlockType.PORTAL, 1, 3) && Cell.fromRaw(117) == Cell.make(BlockType.SNAKE_EXIT, 0, 4),
                "PortalV3 = portail vertical n°3, SnakeExit4 = sortie serpent n°4");

        // ---- rotations et miroirs : cycles et encodages valides
        boolean rot = true;
        for (BlockType t : BlockType.values()) {
            if (t == BlockType.RAW) continue;
            for (int v = 0; v < 4; v++) {
                if (!t.validVariant(v)) continue;
                int c = Cell.make(t, v, t.groups > 0 ? 1 : 0), r = c;
                int period = t.rot == BlockType.Rot.DIR4 || t.rot == BlockType.Rot.DIAG4 ? 4 : t.rot == BlockType.Rot.NONE || t.rot == BlockType.Rot.POWER4 ? 1 : 2;
                for (int k = 0; k < period; k++) { r = Cell.rotate(r); Cell.toRaw(r); }
                if (r != c || Cell.mirrorX(Cell.mirrorX(c)) != c || Cell.mirrorY(Cell.mirrorY(c)) != c) rot = false;
                Cell.toRaw(Cell.mirrorX(c));
                Cell.toRaw(Cell.mirrorY(c));
            }
        }
        check(rot, "rotation (4 quarts de tour = identité) et miroirs (involutions) valides pour tous les blocs orientables");
        check(Cell.toRaw(Cell.rotate(Cell.fromRaw(85))) == 74 && Cell.toRaw(Cell.mirrorX(Cell.fromRaw(56))) == 57
                && Cell.toRaw(Cell.rotate(Cell.fromRaw(113))) == 115 && Cell.toRaw(Cell.rotate(Cell.fromRaw(49))) == 61,
                "Canon haut -> droite, miroir Trampoline BD -> BG, Dents D -> G, Portail H1 -> V1");

        // ---- 300 stages + 517 sections : binaire identique à l'octet
        List<Level> st = Level.parseStages(stagesBin), se = Level.parseSections(sectionsBin);
        ByteArrayOutputStream bs = new ByteArrayOutputStream(), bx = new ByteArrayOutputStream();
        int textOk = 0;
        for (Level l : st) { bs.write(l.toGameBinary()); if (Arrays.equals(Level.fromText("x", l.toText()).toGameBinary(), l.toGameBinary())) textOk++; }
        for (Level l : se) { bx.write(l.toGameBinary()); if (Arrays.equals(Level.fromText("x", l.toText()).toGameBinary(), l.toGameBinary())) textOk++; }
        check(st.size() == 300 && Arrays.equals(bs.toByteArray(), stagesBin), "300 stages : brut -> logique -> brut identique à l'octet");
        check(se.size() == 517 && Arrays.equals(bx.toByteArray(), sectionsBin), "517 sections : brut -> logique -> brut identique à l'octet");
        check(textOk == 817, "817 niveaux : aller-retour texte identique (" + textOk + ")");

        // ---- .txt produits par totm_levels.py
        int py = 0, pyN = 0;
        File[] fs = new File(dir, "stages_txt").listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (int i = 0; i < fs.length; i++) {
                Level l = Level.fromText("py", new String(Files.readAllBytes(fs[i].toPath()), "UTF-8"));
                pyN++;
                if (Arrays.equals(l.toGameBinary(), st.get(i).toGameBinary())) py++;
            }
        }
        File[] fx = new File(dir, "sections_txt").listFiles();
        if (fx != null) {
            Arrays.sort(fx);
            for (int i = 0; i < fx.length; i++) {
                Level l = Level.fromText("py", new String(Files.readAllBytes(fx[i].toPath()), "UTF-8"));
                pyN++;
                if (l.section && Arrays.equals(l.toGameBinary(), se.get(i).toGameBinary())) py++;
            }
        }
        check(pyN == 817 && py == pyN, "fichiers .txt de totm_levels.py : " + py + "/" + pyN + " identiques au binaire officiel");

        // ---- métadonnées en lignes #
        Level m = st.get(0).copy("Mon niveau");
        m.author = "Sky";
        Level m2 = Level.fromText("x", m.toText());
        check(m2.name.equals("Mon niveau") && m2.author.equals("Sky") && !m2.section, "métadonnées nom/auteur conservées");

        // ---- validation des officiels
        int errLevels = 0, warnLevels = 0, sim = 0, basic = 0, basicOk = 0;
        Map<String, Integer> kinds = new TreeMap<>();
        for (Level l : st) {
            List<Checker.Issue> is = Checker.check(l);
            if (Checker.hasErrors(is)) errLevels++;
            boolean w = false;
            for (Checker.Issue i : is) {
                if (i.severity == Checker.WARN) w = true;
                kinds.merge(i.message.replaceAll("[0-9(),]+", "#").replaceAll("#( #)*", "#"), 1, Integer::sum);
            }
            if (w) warnLevels++;
            if (Checker.simulate(l).exitReached) sim++;
            if (Checker.basicOnly(l)) { basic++; if (Checker.simulate(l).exitReached) basicOk++; }
        }
        int secErr = 0;
        for (Level l : se) if (Checker.hasErrors(Checker.check(l))) secErr++;
        check(errLevels == 0 && secErr == 0, "aucune erreur bloquante sur les 300 stages et 517 sections officiels");
        System.out.println("       avertissements sur " + warnLevels + " stages : " + kinds);
        System.out.println("       simulation simple : sortie atteinte dans " + sim + "/300 stages");
        check(basic > 0 && basicOk == basic, "simulation : les " + basic + " officiels sans mécanisme sont jugés faisables");

        // ---- paires incomplètes signalées et localisées
        Level p = Level.starter("p");
        p.cells[10][5] = Cell.make(BlockType.PORTAL, 0, 2);
        p.cells[12][6] = Cell.make(BlockType.SNAKE_ENTER, 0, 1);
        List<Checker.Issue> pi = Checker.check(p);
        int pairErr = 0;
        for (Checker.Issue i : pi) if (i.severity == Checker.ERROR && (i.message.startsWith("Portail n°2") && i.x == 5 && i.y == 10 || i.message.startsWith("Serpent n°1"))) pairErr++;
        check(pairErr == 2, "portail seul et serpent incomplet signalés avec leur case");
        check(Checker.nextFreeGroup(p, BlockType.PORTAL) == 1 && Checker.nextFreeGroup(p, BlockType.SNAKE_ENTER) == 2, "numéro de groupe libre suivant");

        // ---- édition
        Level s = Level.starter("t");
        check(!Checker.hasErrors(Checker.check(s)) && Checker.simulate(s).exitReached, "niveau de départ valide et faisable");
        s.place(5, 5, Cell.make(BlockType.ENTER, 0, 0));
        check(s.count(BlockType.ENTER) == 1 && Cell.is(s.get(5, 5), BlockType.ENTER), "départ unique après déplacement");
        s.resize(25, 40, 0, 1);
        check(s.width == 25 && s.height == 40 && s.count(BlockType.ENTER) == 1 && Cell.is(s.get(7, 15), BlockType.ENTER), "redimension ancrée en bas, centrée en X");
        s.resize(10, 10, 1, -1);
        check(s.width == 10 && s.count(BlockType.EXIT) == 0 && Checker.hasErrors(Checker.check(s)), "réduction ancrée en haut à droite : sortie perdue -> erreur");
        Level mir = st.get(41).copy("m");
        mir.mirrorX(); mir.mirrorX();
        check(Arrays.equals(mir.toGameBinary(), st.get(41).toGameBinary()), "double miroir = identité sur un officiel");
        Level f = Level.starter("f");
        f.cells[3][10] = Cell.EMPTY;
        f.cells[3][11] = Cell.EMPTY;
        int filled = Checker.fillReachableDots(f);
        check(filled >= 1 && Cell.is(f.cells[3][11], BlockType.COIN), "remplissage auto des couloirs atteignables");

        System.out.println(fails == 0 ? "LevelTest : OK" : "LevelTest : " + fails + " ÉCHEC(S)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
