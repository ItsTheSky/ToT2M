#!/usr/bin/env python3
"""Patch statique de libil2cpp.so (Tomb of the Mask 1.2.28, arm64).

DataManager.StageInfo(int) (RVA 0x50E734) est réécrite en :
    adrp x16, SLOT@page ; ldr x16, [x16, SLOT@lo]   -> pointeur de hook (0 par défaut)
    cbz  x16, fallback ; br x16                       -> si la lib éditeur a posé un hook, on y saute
  fallback:
    ldr x0, [x0, #0x20] ; mov x2, xzr ; b List<StageInfo>.get_Item   -> comportement d'origine

SLOT est 16 octets ajoutés à la fin du .bss (p_memsz du segment RW +0x10).
Sans la lib, le jeu fonctionne exactement comme avant. La lib n'écrit qu'un pointeur
dans une page de données : aucune page de code n'est modifiée à l'exécution.
"""
import struct, sys

FUNC = 0x50E734
SLOT = 0x1343A50
EXPECTED = bytes.fromhex("f657bda9f44f01a9fd7b02a9fd830091")
PATCH = bytes.fromhex("b07100b0102a45f9500000b400021fd6001040f9e2031faae64d1e14")
RW_MEMSZ = 0x1D3060


def main(src, dst):
    b = bytearray(open(src, "rb").read())
    assert b[:4] == b"\x7fELF" and b[4] == 2, "ELF64 attendu"
    if b[FUNC:FUNC + len(PATCH)] == PATCH:
        print("déjà patché")
    else:
        assert b[FUNC:FUNC + 16] == EXPECTED, "prologue inattendu : mauvaise version de libil2cpp.so"
        b[FUNC:FUNC + len(PATCH)] = PATCH
    phoff, = struct.unpack_from("<Q", b, 0x20)
    phentsize, phnum = struct.unpack_from("<HH", b, 0x36)
    loads = [i for i in range(phnum) if struct.unpack_from("<I", b, phoff + i * phentsize)[0] == 1]
    rw = phoff + loads[1] * phentsize
    vaddr, = struct.unpack_from("<Q", b, rw + 0x10)
    memsz, = struct.unpack_from("<Q", b, rw + 0x28)
    if memsz == RW_MEMSZ:
        struct.pack_into("<Q", b, rw + 0x28, memsz + 0x10)
    assert vaddr + RW_MEMSZ == SLOT
    open(dst, "wb").write(b)
    print(f"ok -> {dst} (slot à 0x{SLOT:x})")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
