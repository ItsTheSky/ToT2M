#!/usr/bin/env python3
"""Patch statique de libil2cpp.so (Tomb of the Mask 1.2.28, arm64), piloté par hooks.json.

    patch_il2cpp.py patch  <in.so> <out.so> [hooks.json]
    patch_il2cpp.py header <out.h> [hooks.json]       en-tête C pour la lib native

1. DataManager.StageInfo(int) (RVA 0x50E734), patch historique, inchangé :
       adrp x16, SLOT0 ; ldr x16,[x16,lo] ; cbz x16, fb ; br x16
   fb: ldr x0,[x0,#0x20] ; mov x2,xzr ; b List<StageInfo>.get_Item
2. Hooks génériques : la 1re instruction de la fonction F devient « b CAVE_k ».
   La cave (fin morte de l'ancien StageInfo) contient, pour chaque hook :
       CAVE_k+0  adrp x16, SLOT_k@page
       CAVE_k+4  ldr  x16, [x16, SLOT_k@lo]
       CAVE_k+8  cbnz x16, BR_X16           (BR_X16 = « br x16 » partagé en tête de cave)
       CAVE_k+12 <1re instruction d'origine de F, relocalisée>   <- « trampoline » : appelle l'original
       CAVE_k+16 b F+4
   Slot nul (aucune lib) : exactement le code d'origine. La lib native n'écrit que des pointeurs
   dans les slots (.bss étendu) : aucune page de code n'est modifiée à l'exécution.
"""
import json, os, struct, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import arm64  # noqa: E402

HOOK_SIZE = 20


def load_spec(path=None):
    s = json.load(open(path or os.path.join(HERE, "hooks.json")))

    def h(v):
        return int(v, 16) if isinstance(v, str) else v
    spec = {
        "rw_memsz": h(s["rw_memsz"]), "slot_base": h(s["slot_base"]), "slot_area": h(s["slot_area"]),
        "si_rva": h(s["stageinfo"]["rva"]), "si_expected": bytes.fromhex(s["stageinfo"]["expected"]),
        "si_patch": bytes.fromhex(s["stageinfo"]["patch"]),
        "cave_start": h(s["cave"]["start"]), "cave_end": h(s["cave"]["end"]), "hooks": [],
    }
    br_at = spec["cave_start"]
    for i, hk in enumerate(s["hooks"]):
        cave = br_at + 4 + i * HOOK_SIZE
        slot = spec["slot_base"] + 8 * hk["slot"]
        if not 0 < hk["slot"] < spec["slot_area"] // 8:
            raise SystemExit(f"slot {hk['slot']} hors de la zone")
        spec["hooks"].append({"name": hk["name"], "rva": h(hk["rva"]), "slot": slot, "cave": cave,
                              "tramp": cave + 12})
    if br_at + 4 + len(spec["hooks"]) * HOOK_SIZE > spec["cave_end"]:
        raise SystemExit("trop de hooks pour la cave")
    return spec


def cave_words(spec, hk, first_word):
    c, br_at = hk["cave"], spec["cave_start"]
    return [
        arm64.adrp(c, hk["slot"], 16),
        arm64.ldr_x_uoff(16, 16, hk["slot"] & 0xFFF),
        arm64.cbnz_x(c + 8, br_at, 16),
        arm64.relocate(first_word, hk["rva"], c + 12),
        arm64.b(c + 16, hk["rva"] + 4),
    ]


def rd32(b, off):
    return struct.unpack_from("<I", b, off)[0]


def wr32(b, off, w):
    struct.pack_into("<I", b, off, w)


def rw_segment(b):
    assert b[:4] == b"\x7fELF" and b[4] == 2 and struct.unpack_from("<H", b, 0x12)[0] == 183, "ELF64 aarch64 attendu"
    phoff, = struct.unpack_from("<Q", b, 0x20)
    phentsize, phnum = struct.unpack_from("<HH", b, 0x36)
    loads = [phoff + i * phentsize for i in range(phnum) if rd32(b, phoff + i * phentsize) == 1]
    first = loads[0]
    assert struct.unpack_from("<Q", b, first + 8)[0] == struct.unpack_from("<Q", b, first + 0x10)[0] == 0, \
        "1er segment : offset fichier = VA attendu"
    return loads[1]


def patch(src, dst, spec_path=None):
    spec = load_spec(spec_path)
    b = bytearray(open(src, "rb").read())
    rw = rw_segment(b)
    si = spec["si_rva"]
    log = []

    # 1. StageInfo (patch d'origine) ; sert aussi de preuve de version
    already = b[si:si + len(spec["si_patch"])] == spec["si_patch"]
    if not already:
        if b[si:si + 16] != spec["si_expected"]:
            raise SystemExit("prologue de StageInfo inattendu : mauvaise version de libil2cpp.so")
        b[si:si + len(spec["si_patch"])] = spec["si_patch"]
        log.append(f"StageInfo @0x{si:x} patchée")
    else:
        log.append("StageInfo déjà patchée")

    # 2. .bss étendu pour les slots
    vaddr, = struct.unpack_from("<Q", b, rw + 0x10)
    memsz, = struct.unpack_from("<Q", b, rw + 0x28)
    if vaddr + spec["rw_memsz"] != spec["slot_base"]:
        raise SystemExit("segment RW inattendu")
    want = spec["rw_memsz"] + spec["slot_area"]
    if memsz not in (spec["rw_memsz"], spec["rw_memsz"] + 0x10, want):
        raise SystemExit(f"p_memsz RW inattendu : 0x{memsz:x}")
    struct.pack_into("<Q", b, rw + 0x28, want)

    # 3. cave + hooks
    br_at = spec["cave_start"]
    wr32(b, br_at, arm64.br(16))
    for hk in spec["hooks"]:
        f = hk["rva"]
        site = arm64.b(f, hk["cave"])
        w0 = rd32(b, f)
        if w0 == site:
            # déjà patché : l'instruction d'origine est dans la cave (relocalisée)
            orig = arm64.relocate(rd32(b, hk["cave"] + 12), hk["cave"] + 12, f)
            log.append(f"{hk['name']} déjà patché")
        else:
            orig = w0
            prev = rd32(b, f - 4)
            if prev not in (0xD65F03C0, 0xD503201F) and arm64.kind(prev) != "b" and (prev & 0xFFFFFC1F) != 0xD61F0000 and (prev & 0xFFE0001F) != 0xD4200000:
                log.append(f"  attention : l'instruction avant {hk['name']} (0x{prev:08x}) n'est ni ret ni b")
            try:
                arm64.relocate(orig, f, hk["cave"] + 12)
            except ValueError as e:
                raise SystemExit(f"{hk['name']} : {e}")
            log.append(f"{hk['name']} @0x{f:x} : 1re instr 0x{orig:08x} ({arm64.kind(orig)}) -> cave 0x{hk['cave']:x}")
        for i, w in enumerate(cave_words(spec, hk, orig)):
            wr32(b, hk["cave"] + 4 * i, w)
        wr32(b, f, site)

    open(dst, "wb").write(b)
    print("\n".join(log))
    print(f"ok -> {dst} (slots 0x{spec['slot_base']:x}..0x{spec['slot_base'] + spec['slot_area']:x})")


def header(out, spec_path=None):
    spec = load_spec(spec_path)
    L = ["// Généré par tools/patch_il2cpp.py depuis hooks.json : ne pas modifier.", "#pragma once", "#include <stdint.h>",
         f"#define TOTM_RVA_STAGEINFO 0x{spec['si_rva']:X}u", f"#define TOTM_SLOT_BASE 0x{spec['slot_base']:X}u",
         f"#define TOTM_CAVE_BR 0x{spec['cave_start']:X}u",
         "struct TotmHookDesc { const char *name; uint32_t rva, slot, cave, tramp; uint32_t site_word, cave_word0, cave_word1; };",
         "enum {"]
    for i, hk in enumerate(spec["hooks"]):
        L.append(f"    HOOK_{hk['name']} = {i},")
    L += [f"    HOOK_COUNT = {len(spec['hooks'])}", "};", "static const TotmHookDesc TOTM_HOOKS[HOOK_COUNT] = {"]
    for hk in spec["hooks"]:
        w = cave_words(spec, hk, 0xD503201F)
        L.append(f"    {{\"{hk['name']}\", 0x{hk['rva']:X}u, 0x{hk['slot']:X}u, 0x{hk['cave']:X}u, 0x{hk['tramp']:X}u, "
                 f"0x{arm64.b(hk['rva'], hk['cave']):08X}u, 0x{w[0]:08X}u, 0x{w[1]:08X}u}},")
    L.append("};")
    open(out, "w").write("\n".join(L) + "\n")
    print(f"en-tête -> {out}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) >= 3 and a[0] == "patch":
        patch(a[1], a[2], a[3] if len(a) > 3 else None)
    elif len(a) >= 2 and a[0] == "header":
        header(a[1], a[2] if len(a) > 2 else None)
    elif len(a) == 2:  # compatibilité avec l'ancien appel « patch_il2cpp.py in out »
        patch(a[0], a[1])
    else:
        sys.exit(__doc__)
