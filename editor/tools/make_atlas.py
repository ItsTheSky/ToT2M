#!/usr/bin/env python3
"""Assemble les sprites du jeu utiles à l'éditeur en un atlas (PNG + index texte) chargé par SpriteAtlas.java.

    make_atlas.py <dossier de sprites PNG> <dossier de sortie> [colors.txt]

Entrée : un PNG par sprite, nommé comme le Sprite Unity (sortie de extract_sprites.py ou reference/assets/sprites).
Sortie : atlas.png et atlas.txt (« nom x y l h » par ligne). Le tileset Tiled des devs (sprite « tiles »,
1152x1152, 12x12 tuiles de 96 px) est réduit en icônes de 24 px nommées dev_<valeur> (valeur = index + 1).
"""
import os, sys
from PIL import Image

WALL_PARTS = ["Wall_u", "Wall_d", "Wall_l", "Wall_r", "Corner_u_l", "Corner_u_r", "Corner_d_l", "Corner_d_r"]
INNER = ["Inner_u_l", "Inner_u_r", "Inner_d_l", "Inner_d_r"]


def wanted():
    names = []
    for fam in range(1, 6):
        for p in WALL_PARTS:
            for v in range(1, 5):
                names.append(f"{fam}_{p}_{v}")
        for p in INNER:
            names.append(f"{fam}_{p}")
    names += [f"Secret_{s}" for s in ("u", "d", "l", "r")]
    names += [f"Secret_Corner_{a}_{b}" for a in "ud" for b in "lr"] + [f"Secret_Inner_{a}_{b}" for a in "ud" for b in "lr"]
    names += [f"Spikes_{s}" for s in "udlr"] + [f"M_Spikes_{s}" for s in "udlr"]
    names += [f"Corner_spikes_{a}_{b}" for a in "ud" for b in "lr"]
    names += ["Dot_game", "Coin_1", "Star_anim_1", "Exit_1", "Exit_2", "Bat_1", "Cannon_1", "Platform_1", "Tramplin_1",
              "Ice", "Teeth_1", "Timer_wall_0", "Timer_wall_5", "fish_hedgehog_1", "Rotor_1", "Freeze_1", "Magnet_1",
              "Coin_addict_1", "Scores_1", "lightning", "lightning_1", "M_Spikes_attack_1", "Secret_room_1", "Gates_1",
              "Monkey_idle_1", "Rock", "0_Char_idle_1", "0_Char_arrive_1", "Hero_flight_1", "Torch_1"]
    names += [f"Portal_{i}" for i in range(1, 12)]
    return names


def pack(images, width=512):
    """Rangement en étagères, du plus haut au plus bas ; 1 px de marge."""
    order = sorted(images.items(), key=lambda kv: (-kv[1].size[1], kv[0]))
    x = y = shelf = 0
    pos = {}
    for name, im in order:
        w, h = im.size
        if x + w > width:
            x, y, shelf = 0, y + shelf + 1, 0
        pos[name] = (x, y, w, h)
        x += w + 1
        shelf = max(shelf, h)
    height = 1
    while height < y + shelf:
        height *= 2
    atlas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for name, (px, py, w, h) in pos.items():
        atlas.paste(images[name], (px, py))
    return atlas, pos


def main(src, out, colors=None):
    os.makedirs(out, exist_ok=True)
    images, missing = {}, []
    for n in wanted():
        p = os.path.join(src, n + ".png")
        if os.path.exists(p):
            images[n] = Image.open(p).convert("RGBA")
        else:
            missing.append(n)
    tiles = os.path.join(src, "tiles.png")
    if os.path.exists(tiles):
        t = Image.open(tiles).convert("RGBA")
        for i in range(144):
            c, r = i % 12, i // 12
            ic = t.crop((c * 96, r * 96, c * 96 + 96, r * 96 + 96))
            if ic.getbbox():
                images[f"dev_{i + 1}"] = ic.resize((24, 24), Image.BOX)
    else:
        missing.append("tiles")
    atlas, pos = pack(images)
    atlas.save(os.path.join(out, "atlas.png"), optimize=True)
    with open(os.path.join(out, "atlas.txt"), "w") as f:
        for n in sorted(pos):
            f.write("%s %d %d %d %d\n" % ((n,) + pos[n]))
    if colors and os.path.exists(colors):
        with open(colors) as fi, open(os.path.join(out, "colors.txt"), "w") as fo:
            fo.write(fi.read())
    print(f"atlas {atlas.size[0]}x{atlas.size[1]} : {len(pos)} sprites, {len(missing)} absents {missing[:8]}")
    if len(images) < 50:
        sys.exit("trop peu de sprites trouvés : mauvais dossier ?")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
