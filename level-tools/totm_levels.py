#!/usr/bin/env python3
"""
totm_levels.py - Outil de niveaux pour Tomb of the Mask (Android, v1.2.28, Unity IL2CPP)

Les niveaux sont des TextAssets Unity chargés via Resources.Load :
  - "stages"   : les 300 niveaux du mode Stages (histoire)
  - "sections" : les 517 morceaux de 13 colonnes assemblés par le mode Arcade (infini)

Format binaire (décodé depuis DataManager.LoadStages / LoadSections) :
  stages   = suite de [lavaSpeed:u8][largeur:u8][hauteur:u8][largeur*hauteur octets]
  sections = suite de [SectionType:u8][nb_cases:u8][nb_cases octets]   (largeur fixe 13)
  Les lignes sont stockées de BAS en HAUT (y Unity vers le haut).
  Chaque octet est une valeur de l'enum LevelTileType (voir TILES).

Commandes :
  extract <apk> <dossier>          extrait stages/sections en .txt + .tmx + .png, plus tiles.png pour Tiled
  render  <fichier.txt|.tmx> <out.png>
  build   <dossier> <out.bytes> [--kind stages|sections]   reconstruit le binaire depuis les .txt/.tmx
  patch   <apk> <out.apk> [--stages f.bytes] [--sections f.bytes]   réinjecte dans l'APK (non signé)

Dépendances : pip install UnityPy pillow
"""
import argparse, os, re, sys, zipfile, shutil, tempfile, io
import xml.etree.ElementTree as ET

# ---------------------------------------------------------------- tuiles
# Valeurs tirées de l'enum LevelTileType (dump IL2CPP)
TILES = {
    0: "None", 1: "Enter", 2: "Exit", 3: "Wall", 4: "Star", 5: "WallEmpty", 6: "BonusDoor",
    7: "Ice", 8: "Spikes", 12: "CloseBrick", 13: "MonkeysTrigger1", 14: "MonkeysTrigger2",
    15: "MonkeysTrigger3", 19: "Coin", 23: "LightBallTrigger1", 24: "LightBall1", 31: "Dot",
    35: "LightBallTrigger2", 36: "LightBall2", 43: "ZoneExit", 47: "LightBallTrigger3",
    48: "LightBall3", 49: "PortalH1", 50: "PortalH2", 51: "PortalH3", 52: "PortalH4",
    53: "PortalH5", 54: "PortalH6", 55: "PortalH7", 56: "TramplinDR", 57: "TramplinDL",
    59: "LightBallTrigger4", 60: "LightBall4", 61: "PortalV1", 62: "PortalV2", 63: "PortalV3",
    64: "PortalV4", 65: "PortalV5", 66: "PortalV6", 67: "PortalV7", 68: "TramplinUR",
    69: "TramplinUL", 71: "LightBallTrigger5", 72: "LightBall5", 73: "CannonLeft",
    74: "CannonRight", 76: "BatRight", 77: "BatLeft", 78: "PlatformRight", 79: "PlatformLeft",
    80: "SnakeEnter1", 81: "SnakeExit1", 82: "SnakeTrigger1", 83: "LightBallTrigger6",
    84: "LightBall6", 85: "CannonUp", 88: "BatUp", 89: "BatDown", 90: "PlatformUp",
    91: "PlatformDown", 92: "SnakeEnter2", 93: "SnakeExit2", 94: "SnakeTrigger2", 97: "CannonDown",
    98: "MSpikes", 102: "Fish", 104: "SnakeEnter3", 105: "SnakeExit3", 106: "SnakeTrigger3",
    109: "RotationCW", 110: "RotationCCW", 113: "BonusTeethRight", 114: "BonusWall",
    115: "BonusTeethLeft", 116: "SnakeEnter4", 117: "SnakeExit4", 118: "SnakeTrigger4",
    121: "TimerWall", 128: "SnakeEnter5", 129: "SnakeExit5", 130: "SnakeTrigger5",
    140: "SnakeEnter6", 141: "SnakeExit6", 142: "SnakeTrigger6", 145: "PowerupCoinAddict",
    146: "PowerupFreeze", 147: "PowerupMagnet", 148: "PowerupScoreBoost", 255: "Player",
}
SECTION_TYPES = ["Begin", "Easy", "Spikes", "Bats", "Cannons", "Fishes", "Platforms", "Tramplins",
                 "Hard", "Portals", "BonusEnter", "BonusRun", "Snake", "Tutorial", "BonusPipes"]
SECTION_WIDTH = 13


def color_of(v):
    n = TILES.get(v, "")
    fixed = {"None": (18, 18, 24), "Wall": (70, 40, 110), "WallEmpty": (50, 30, 80),
             "Coin": (240, 200, 40), "Dot": (230, 210, 120), "Star": (255, 255, 255),
             "Enter": (40, 220, 90), "Exit": (40, 160, 255), "Ice": (150, 220, 255),
             "Spikes": (230, 40, 40), "MSpikes": (255, 110, 60), "Fish": (255, 120, 200),
             "TimerWall": (130, 90, 60), "CloseBrick": (160, 110, 70), "ZoneExit": (0, 255, 255),
             "BonusDoor": (255, 0, 255), "BonusWall": (110, 60, 150)}
    if n in fixed: return fixed[n]
    for k, c in (("Bat", (200, 0, 120)), ("Cannon", (255, 60, 0)), ("Portal", (0, 200, 200)),
                 ("Tramplin", (120, 255, 0)), ("Platform", (190, 160, 255)), ("Snake", (0, 130, 0)),
                 ("LightBall", (255, 240, 150)), ("Monkeys", (180, 100, 0)), ("Rotation", (255, 170, 0)),
                 ("Powerup", (255, 255, 0)), ("Teeth", (230, 60, 100))):
        if k in n: return c
    return (255, 0, 0)  # inconnu


# ---------------------------------------------------------------- binaire
def parse_stages(b):
    i, out = 0, []
    while i < len(b):
        lava, w, h = b[i], b[i + 1], b[i + 2]
        out.append({"lava": lava, "w": w, "h": h, "tiles": bytes(b[i + 3:i + 3 + w * h])}); i += 3 + w * h
    return out


def parse_sections(b):
    i, out = 0, []
    while i < len(b):
        t, n = b[i], b[i + 1]
        out.append({"type": t, "w": SECTION_WIDTH, "h": n // SECTION_WIDTH, "tiles": bytes(b[i + 2:i + 2 + n])}); i += 2 + n
    return out


def build_stages(levels):
    o = bytearray()
    for L in levels:
        assert L["w"] < 256 and L["h"] < 256 and len(L["tiles"]) == L["w"] * L["h"]
        o += bytes([L["lava"], L["w"], L["h"]]) + L["tiles"]
    return bytes(o)


def build_sections(levels):
    o = bytearray()
    for L in levels:
        assert L["w"] == SECTION_WIDTH and len(L["tiles"]) < 256
        o += bytes([L["type"], len(L["tiles"])]) + L["tiles"]
    return bytes(o)


def rows_top_down(L):
    """Le binaire est stocké bas -> haut ; on renvoie les lignes de haut en bas (affichage)."""
    w, h, t = L["w"], L["h"], L["tiles"]
    return [list(t[y * w:(y + 1) * w]) for y in range(h)][::-1]


def from_rows_top_down(rows):
    return bytes(v for r in rows[::-1] for v in r)


# ---------------------------------------------------------------- texte
def to_txt(L, kind):
    head = [f"# kind={kind}", f"# width={L['w']} height={L['h']}"]
    if kind == "stages": head.append(f"# lava={L['lava']}   (0 = pas de lave montante, sinon vitesse = lava*0.01)")
    else: head.append(f"# type={L['type']} ({SECTION_TYPES[L['type']] if L['type'] < len(SECTION_TYPES) else '?'})")
    head.append("# lignes de haut en bas ; valeurs = LevelTileType (voir TILES)")
    return "\n".join(head + [" ".join(f"{v:3d}" for v in r) for r in rows_top_down(L)]) + "\n"


def from_txt(path):
    meta, rows = {}, []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line: continue
        if line.startswith("#"):
            for k, v in re.findall(r"(\w+)=(\d+)", line): meta.setdefault(k, int(v))
            continue
        rows.append([int(x) for x in line.split()])
    w = len(rows[0]); assert all(len(r) == w for r in rows), f"{path}: lignes de longueurs différentes"
    return {"lava": meta.get("lava", 0), "type": meta.get("type", 1), "w": w, "h": len(rows),
            "tiles": from_rows_top_down(rows)}


# ---------------------------------------------------------------- Tiled (.tmx)
def to_tmx(L, kind):
    rows = rows_top_down(L)
    props = f'<property name="lava" type="int" value="{L["lava"]}"/>' if kind == "stages" \
        else f'<property name="type" type="int" value="{L["type"]}"/>'
    csv = ",\n".join(",".join(str(v) for v in r) for r in rows)
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.0" orientation="orthogonal" renderorder="right-down" width="{L['w']}" height="{L['h']}" tilewidth="96" tileheight="96">
 <properties>{props}</properties>
 <tileset firstgid="1" name="tiles" tilewidth="96" tileheight="96" tilecount="144" columns="12">
  <image source="tiles.png" width="1152" height="1152"/>
 </tileset>
 <layer name="Tile Layer 1" width="{L['w']}" height="{L['h']}">
  <data encoding="csv">
{csv}
  </data>
 </layer>
</map>
'''


def from_tmx(path):
    r = ET.parse(path).getroot()
    w, h = int(r.get("width")), int(r.get("height"))
    props = {p.get("name"): p.get("value") for p in r.findall("properties/property")}
    d = r.find("layer/data")
    if d.get("encoding") == "csv":
        vals = [int(x) for x in re.split(r"[,\s]+", d.text.strip()) if x]
    elif d.get("encoding") is None:
        vals = [int(t.get("gid")) for t in d.findall("tile")]
    else:
        sys.exit(f"{path}: encodage {d.get('encoding')} non supporté (utilise CSV dans Tiled)")
    vals = [v & 0x1FFFFFFF for v in vals]  # retire les flags de flip de Tiled
    assert max(vals) < 256, f"{path}: tuile > 255"
    rows = [vals[y * w:(y + 1) * w] for y in range(h)]
    return {"lava": int(props.get("lava", 0) or 0), "type": int(props.get("type", 1) or 1),
            "w": w, "h": h, "tiles": from_rows_top_down(rows)}


def load_level(path):
    return from_tmx(path) if path.endswith(".tmx") else from_txt(path)


# ---------------------------------------------------------------- rendu
def render(L, out, px=16):
    from PIL import Image, ImageDraw
    rows = rows_top_down(L)
    im = Image.new("RGB", (L["w"] * px, L["h"] * px), (0, 0, 0)); d = ImageDraw.Draw(im)
    for y, r in enumerate(rows):
        for x, v in enumerate(r):
            c = color_of(v); x0, y0 = x * px, y * px
            n = TILES.get(v, "")
            if n in ("Coin", "Dot", "Star"):
                d.rectangle([x0, y0, x0 + px - 1, y0 + px - 1], fill=color_of(0))
                s = px // 3 if n != "Star" else px // 5
                d.ellipse([x0 + s, y0 + s, x0 + px - 1 - s, y0 + px - 1 - s], fill=c)
            else:
                d.rectangle([x0, y0, x0 + px - 1, y0 + px - 1], fill=c)
    im.save(out)
    return im


def make_tileset(out):
    """tiles.png 12x12 tuiles de 96 px : la tuile (gid-1) porte la couleur + le nom de la valeur gid."""
    from PIL import Image, ImageDraw
    im = Image.new("RGBA", (1152, 1152), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for gid in range(1, 145):
        tid = gid - 1; x0, y0 = (tid % 12) * 96, (tid // 12) * 96
        name = TILES.get(gid)
        if not name: continue
        c = color_of(gid)
        d.rectangle([x0 + 1, y0 + 1, x0 + 94, y0 + 94], fill=c + (255,), outline=(0, 0, 0, 255))
        lum = c[0] * .3 + c[1] * .59 + c[2] * .11
        tc = (0, 0, 0) if lum > 140 else (255, 255, 255)
        d.text((x0 + 5, y0 + 5), str(gid), fill=tc)
        for k, part in enumerate(re.findall(r"[A-Z][a-z]*|\d+", name)[:4]):
            d.text((x0 + 5, y0 + 25 + k * 14), part, fill=tc)
    im.save(out)


# ---------------------------------------------------------------- APK
def _load_asset_bytes(apk):
    import UnityPy
    tmp = tempfile.mkdtemp()
    with zipfile.ZipFile(apk) as z:
        names = [n for n in z.namelist() if n.startswith("assets/bin/Data/")]
        z.extractall(tmp, names)
    env = UnityPy.load(os.path.join(tmp, "assets/bin/Data"))
    found = {}
    for o in env.objects:
        if o.type.name == "TextAsset":
            ta = o.read()
            if ta.m_Name in ("stages", "sections"):
                data = ta.m_Script
                if isinstance(data, str): data = data.encode("utf-8", "surrogateescape")
                found[ta.m_Name] = (data, os.path.basename(o.assets_file.name))
    shutil.rmtree(tmp)
    return found


def cmd_extract(a):
    from PIL import Image
    found = _load_asset_bytes(a.apk)
    os.makedirs(a.out, exist_ok=True)
    make_tileset(os.path.join(a.out, "tiles.png"))
    for kind, (data, fname) in found.items():
        open(os.path.join(a.out, f"{kind}.bytes"), "wb").write(data)
        levels = parse_stages(data) if kind == "stages" else parse_sections(data)
        dk = os.path.join(a.out, kind); os.makedirs(dk, exist_ok=True)
        shutil.copy(os.path.join(a.out, "tiles.png"), dk)
        counters = {}
        for i, L in enumerate(levels):
            if kind == "stages": name = f"stage_{i + 1:03d}"
            else:
                k = counters.get(L["type"], 0); counters[L["type"]] = k + 1
                name = f"section_{i:03d}_{SECTION_TYPES[L['type']].lower()}_{k:02d}"
            open(os.path.join(dk, name + ".txt"), "w").write(to_txt(L, kind))
            open(os.path.join(dk, name + ".tmx"), "w").write(to_tmx(L, kind))
            render(L, os.path.join(dk, name + ".png"))
        print(f"{kind}: {len(levels)} niveaux -> {dk}  (asset Unity : {fname})")


def cmd_render(a):
    render(load_level(a.level), a.out, a.px); print("ok", a.out)


def cmd_build(a):
    files = sorted(f for f in os.listdir(a.dir) if f.endswith(a.ext))
    levels = [load_level(os.path.join(a.dir, f)) for f in files]
    data = build_stages(levels) if a.kind == "stages" else build_sections(levels)
    open(a.out, "wb").write(data)
    print(f"{len(levels)} niveaux ({a.ext}) -> {a.out} ({len(data)} octets)")


def cmd_patch(a):
    import UnityPy
    repl = {}
    if a.stages: repl["stages"] = open(a.stages, "rb").read()
    if a.sections: repl["sections"] = open(a.sections, "rb").read()
    found = _load_asset_bytes(a.apk)
    with zipfile.ZipFile(a.apk) as zin:
        new_entries = {}
        for kind, data in repl.items():
            fname = found[kind][1]; entry = f"assets/bin/Data/{fname}"
            env = UnityPy.load(zin.read(entry))
            for o in env.objects:
                if o.type.name == "TextAsset":
                    ta = o.read()
                    if ta.m_Name == kind:
                        ta.m_Script = data.decode("utf-8", "surrogateescape"); ta.save()
            af = list(env.files.values())[0]
            new_entries[entry] = af.save()
            print(f"{kind}: {entry} remplacé ({len(data)} octets de niveaux)")
        with zipfile.ZipFile(a.out, "w") as zout:
            for info in zin.infolist():
                if info.filename.startswith("META-INF/"): continue  # ancienne signature
                data = new_entries.get(info.filename, None)
                if data is None: data = zin.read(info.filename)
                zi = zipfile.ZipInfo(info.filename, info.date_time)
                zi.compress_type = info.compress_type; zi.external_attr = info.external_attr
                zout.writestr(zi, data)
    print(f"APK non signé -> {a.out}\n"
          "Ensuite : zipalign -p -f 4 in.apk aligned.apk && apksigner sign --ks debug.keystore aligned.apk")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    s = p.add_subparsers(dest="cmd", required=True)
    e = s.add_parser("extract"); e.add_argument("apk"); e.add_argument("out"); e.set_defaults(f=cmd_extract)
    r = s.add_parser("render"); r.add_argument("level"); r.add_argument("out"); r.add_argument("--px", type=int, default=16); r.set_defaults(f=cmd_render)
    b = s.add_parser("build"); b.add_argument("dir"); b.add_argument("out")
    b.add_argument("--kind", choices=["stages", "sections"], default="stages")
    b.add_argument("--ext", choices=[".txt", ".tmx"], default=".txt"); b.set_defaults(f=cmd_build)
    pa = s.add_parser("patch"); pa.add_argument("apk"); pa.add_argument("out")
    pa.add_argument("--stages"); pa.add_argument("--sections"); pa.set_defaults(f=cmd_patch)
    a = p.parse_args(); a.f(a)


if __name__ == "__main__":
    main()
