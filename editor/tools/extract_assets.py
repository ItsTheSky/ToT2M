#!/usr/bin/env python3
"""Extrait de l'APK officiel ce dont l'éditeur a besoin (requiert UnityPy).

    extract_assets.py <apk> <dossier de sortie>

Produit :
  stages.bytes, sections.bytes   TextAssets des niveaux officiels
  sprites/<nom>.png              les Sprites Unity listés par make_atlas.wanted() (+ le tileset « tiles »)
  colors.txt                     paires de couleurs du GameController si on les trouve (sinon absent)

Couleurs : les MonoBehaviour n'ont pas de typetree dans un build joueur. On cherche donc, dans les données
brutes du composant dont le script est GameController, deux tableaux Color[] consécutifs de même taille
(mainColors puis subColors, cf. dump.cs) : int32 n puis n x 4 floats dans [0,1] avec alpha = 1.
"""
import io, os, struct, sys, tempfile, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from make_atlas import wanted  # noqa: E402


def color_arrays(raw):
    """Liste de (offset, [(r,g,b,a)...]) pour chaque tableau de couleurs plausible."""
    out, i = [], 0
    while i + 4 <= len(raw):
        n, = struct.unpack_from("<i", raw, i)
        if 2 <= n <= 32 and i + 4 + 16 * n <= len(raw):
            cols = [struct.unpack_from("<4f", raw, i + 4 + 16 * k) for k in range(n)]
            if all(0 <= c <= 1.0001 for col in cols for c in col) and all(abs(col[3] - 1) < 1e-3 for col in cols) \
                    and len(set(cols)) > 1:
                out.append((i, cols))
                i += 4 + 16 * n
                continue
        i += 1  # pas de 1 : ne dépend pas de l'alignement exact des champs précédents
    return out


def find_color_pairs(raw):
    arrs = color_arrays(raw)
    for (o1, a), (o2, b) in zip(arrs, arrs[1:]):
        if len(a) == len(b) and o2 == o1 + 4 + 16 * len(a):
            return a, b
    return None


def hexc(c):
    return "#%02X%02X%02X" % tuple(round(max(0, min(1, v)) * 255) for v in c[:3])


def main(apk, out):
    import UnityPy
    os.makedirs(os.path.join(out, "sprites"), exist_ok=True)
    tmp = tempfile.mkdtemp()
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            if n.startswith("assets/bin/Data/") and not n.endswith((".dll", ".resource")) and "/Managed/" not in n:
                z.extract(n, tmp)
    env = UnityPy.load(os.path.join(tmp, "assets", "bin", "Data"))
    need = set(wanted()) | {"tiles"}
    got, texts = set(), {}
    scripts = {}
    for o in env.objects:
        t = o.type.name
        try:
            if t == "Sprite":
                x = o.read()
                if x.m_Name in need and x.m_Name not in got:
                    x.image.save(os.path.join(out, "sprites", x.m_Name + ".png"))
                    got.add(x.m_Name)
            elif t == "TextAsset":
                x = o.read()
                if x.m_Name in ("stages", "sections"):
                    b = x.m_Script
                    if isinstance(b, str):
                        b = b.encode("utf-8", "surrogateescape")
                    texts[x.m_Name] = b
            elif t == "MonoScript":
                scripts[(os.path.basename(o.assets_file.name), o.path_id)] = o.read().m_ClassName
        except Exception as e:  # un objet illisible ne doit pas bloquer le build
            print(f"  ignoré {t} {o.path_id} : {e}")
    for k, b in texts.items():
        open(os.path.join(out, k + ".bytes"), "wb").write(b)
    print(f"sprites : {len(got)}/{len(need)} ; niveaux : {sorted(texts)}")

    # couleurs du GameController (meilleur effort)
    pairs = None
    for o in env.objects:
        if o.type.name != "MonoBehaviour":
            continue
        try:
            raw = o.get_raw_data()
            # en-tête : PPtr m_GameObject (12), m_Enabled (4 après alignement), PPtr m_Script (12)
            fid, pid = struct.unpack_from("<iq", raw, 16)
            name = None
            for (af, p), cls in scripts.items():
                if p == pid:
                    name = cls
                    break
            if name != "GameController":
                continue
            pairs = find_color_pairs(raw)
            if pairs:
                break
        except Exception:
            continue
    if pairs:
        with open(os.path.join(out, "colors.txt"), "w") as f:
            f.write("# couleurs extraites de GameController : principale secondaire\n")
            for a, b in zip(*pairs):
                f.write(f"{hexc(a)} {hexc(b)}\n")
        print(f"couleurs : {len(pairs[0])} paires trouvées dans GameController")
    else:
        print("couleurs : non trouvées, palette par défaut de l'éditeur")
    if "stages" not in texts:
        sys.exit("TextAsset 'stages' introuvable")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
