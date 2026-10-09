#!/usr/bin/env python3
"""Test de la recherche heuristique des couleurs dans un GameController sérialisé (données synthétiques)."""
import os, struct, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools"))
from extract_assets import find_color_pairs, hexc

def colors(cs): return struct.pack("<i", len(cs)) + b"".join(struct.pack("<4f", *c) for c in cs)
head = b"\0" * 12 + b"\1\0\0\0" + struct.pack("<iq", 0, 1234) + struct.pack("<i", 15) + b"GameController\0" + b"\0" * 8 * 12
single = b"".join(struct.pack("<4f", 1, .9, 0, 1) for _ in range(3))  # _yellowColor, _greenColor, _freezeColor
main_c = [(0.69, 0.15, 1, 1), (0, 0.9, 1, 1), (1, 0.18, 0.53, 1)]
sub_c = [(1, 0.902, 0, 1), (1, 1, 1, 1), (0.2, 1, 0.1, 1)]
raw = head + single + colors(main_c) + colors(sub_c) + struct.pack("<i", 7) + b"\x00" * 40
p = find_color_pairs(raw)
assert p and len(p[0]) == 3 and hexc(p[0][0]) == "#B026FF" and hexc(p[1][0]) == "#FFE600", p
assert find_color_pairs(head + struct.pack("<i", 3) + b"\xff" * 48) is None
print("assets_test : recherche des paires de couleurs OK")
