"""Encodage / décodage minimal d'instructions ARM64 pour les patchs statiques de libil2cpp.so."""

MASK64 = (1 << 64) - 1


def _signed(v, bits):
    return v - (1 << bits) if v & (1 << (bits - 1)) else v


def _check_range(off, bits, scale, what):
    lim = 1 << (bits - 1)
    if off % scale or not (-lim * scale <= off < lim * scale):
        raise ValueError(f"{what} : décalage 0x{off:x} hors de portée")


def b(pc, target):
    off = target - pc
    _check_range(off, 26, 4, "b")
    return 0x14000000 | ((off >> 2) & 0x3FFFFFF)


def bl(pc, target):
    return b(pc, target) | 0x80000000


def br(reg):
    return 0xD61F0000 | (reg << 5)


def adrp(pc, target, rd):
    imm = (target >> 12) - (pc >> 12)
    _check_range(imm, 21, 1, "adrp")
    imm &= 0x1FFFFF
    return 0x90000000 | ((imm & 3) << 29) | ((imm >> 2) << 5) | rd


def adr(pc, target, rd):
    off = target - pc
    _check_range(off, 21, 1, "adr")
    off &= 0x1FFFFF
    return 0x10000000 | ((off & 3) << 29) | ((off >> 2) << 5) | rd


def ldr_x_uoff(rt, rn, imm):
    if imm % 8 or not 0 <= imm < 8 * 4096:
        raise ValueError("ldr : décalage invalide")
    return 0xF9400000 | ((imm // 8) << 10) | (rn << 5) | rt


def cbnz_x(pc, target, rt):
    off = target - pc
    _check_range(off, 19, 4, "cbnz")
    return 0xB5000000 | (((off >> 2) & 0x7FFFF) << 5) | rt


def cbz_x(pc, target, rt):
    return cbnz_x(pc, target, rt) & ~0x01000000


# ------------------------------------------------------------------ classement / relocalisation
def kind(w):
    """Classe d'une instruction du point de vue « dépend du PC »."""
    if (w & 0x7C000000) == 0x14000000:
        return "bl" if w & 0x80000000 else "b"
    if (w & 0x9F000000) == 0x90000000:
        return "adrp"
    if (w & 0x9F000000) == 0x10000000:
        return "adr"
    if (w & 0xFF000010) == 0x54000000:
        return "b.cond"
    if (w & 0x7E000000) == 0x34000000:
        return "cbz"
    if (w & 0x7E000000) == 0x36000000:
        return "tbz"
    if (w & 0x3B000000) == 0x18000000:
        return "ldr_lit"
    return "pic"


def branch_target(pc, w):
    return pc + 4 * _signed(w & 0x3FFFFFF, 26)


def adrp_target(pc, w):
    imm = ((w >> 29) & 3) | (((w >> 5) & 0x7FFFF) << 2)
    return ((pc & ~0xFFF) + (_signed(imm, 21) << 12)) & MASK64


def adr_target(pc, w):
    imm = ((w >> 29) & 3) | (((w >> 5) & 0x7FFFF) << 2)
    return pc + _signed(imm, 21)


def relocate(w, old_pc, new_pc):
    """Recopie l'instruction w (prise à old_pc) pour qu'elle s'exécute à new_pc avec le même effet."""
    k = kind(w)
    if k == "pic":
        return w
    if k == "b":
        return b(new_pc, branch_target(old_pc, w))
    if k == "bl":
        return bl(new_pc, branch_target(old_pc, w))
    if k == "adrp":
        return adrp(new_pc, adrp_target(old_pc, w), w & 0x1F)
    if k == "adr":
        return adr(new_pc, adr_target(old_pc, w), w & 0x1F)
    raise ValueError(f"instruction 0x{w:08x} ({k}) non relocalisable en tête de fonction")
