#!/usr/bin/env python3
"""Test du patch statique sans la vraie libil2cpp.so : image ELF synthétique, patch, puis exécution
émulée (unicorn) des fonctions patchées, slot nul (comportement d'origine) et slot posé (hook).
Requiert : pip install unicorn keystone-engine capstone"""
import os, struct, sys, tempfile
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "tools"))
import patch_il2cpp as P  # noqa: E402
import arm64  # noqa: E402
from keystone import Ks, KS_ARCH_ARM64, KS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_PROT_ALL
from unicorn.arm64_const import UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X8, UC_ARM64_REG_X30, UC_ARM64_REG_SP, UC_ARM64_REG_PC

ks = Ks(KS_ARCH_ARM64, KS_MODE_LITTLE_ENDIAN)
spec = P.load_spec()
SIZE = 0x1348000
BASE = 0x40000000  # adresse de chargement « aléatoire » : tout le code doit être indépendant de la position


def asm(code, addr):
    enc, _ = ks.asm(code, addr)
    return bytes(enc)


def put(img, addr, code):
    b = asm(code, addr)
    img[addr:addr + len(b)] = b
    return len(b)


def build_image():
    img = bytearray(SIZE)
    img[:4] = b"\x7fELF"; img[4] = 2; img[5] = 1
    struct.pack_into("<HH", img, 0x10, 3, 183)
    struct.pack_into("<Q", img, 0x20, 0x40)
    struct.pack_into("<HH", img, 0x36, 56, 2)
    rw_vaddr = spec["slot_base"] - spec["rw_memsz"]
    struct.pack_into("<IIQQQQQQ", img, 0x40, 1, 5, 0, 0, 0, 0x1100000, 0x1100000, 0x4000)
    struct.pack_into("<IIQQQQQQ", img, 0x40 + 56, 1, 6, rw_vaddr - 0x4000, rw_vaddr, rw_vaddr, 0x100, spec["rw_memsz"], 0x4000)
    # StageInfo d'origine, réassemblée depuis reference/notes (les cibles bl sont des stubs)
    si = """stp x22, x21, [sp, #-0x30]!; stp x20, x19, [sp, #0x10]; stp x29, x30, [sp, #0x20]; add x29, sp, #0x20;
            adrp x22, #0x12f8000; ldrb w9, [x22, #0x699]; mov w19, w1; mov x21, x0; mov x20, x8; tbnz w9, #0, #0x50e774;
            adrp x8, #0x125f000; ldr x8, [x8, #0xb8]; ldr w0, [x8]; bl #0x428974; mov w8, #1; strb w8, [x22, #0x699];
            ldr x21, [x21, #0x20]; cbnz x21, #0x50e784; mov x0, xzr; bl #0x45156c; adrp x8, #0x1262000; ldr x8, [x8, #0xbb0];
            mov w1, w19; ldp x29, x30, [sp, #0x20]; mov x0, x21; ldr x2, [x8]; mov x8, x20; ldp x20, x19, [sp, #0x10];
            ldp x22, x21, [sp], #0x30; b #0xca1ee4"""
    n = put(img, 0x50E734, si)
    assert n == 30 * 4 and img[0x50E734:0x50E744] == spec["si_expected"], "StageInfo réassemblée != octets attendus"
    # List<StageInfo>.get_Item factice : écrit index*3 dans le résultat (x8) ; vérifie x2 == 0
    put(img, 0xCA1EE4, "mov w9, #3; mul w9, w1, w9; str x9, [x8]; str x2, [x8, #8]; ret")
    # fonctions accrochées, avec des 1res instructions de chaque classe gérée
    h = {k["name"]: k["rva"] for k in spec["hooks"]}
    f = h["GameStateController_Update"]
    put(img, f - 4, "ret"); put(img, f, "stp x29, x30, [sp, #-16]!; mov x29, sp; add x0, x0, #1; ldp x29, x30, [sp], #16; ret")
    f = h["GameController_StageCompleted"]
    put(img, f - 4, "ret"); put(img, f, f"adrp x1, #0x{0x5F2000 + 0x3000:x}; add x0, x1, #0x10; ret")
    f = h["GameController_ProcessPlayerDeath"]
    put(img, f - 4, "ret"); put(img, f, "b #0x700000")
    put(img, 0x700000, "add x0, x0, x1; ret")
    f = h["GameController_SaveGameResults"]
    put(img, f - 4, "ret"); put(img, f, "sub sp, sp, #16; mov x0, #5; add sp, sp, #16; ret")
    return img


def run(mem, addr, x0=0, x1=0):
    mu = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    mu.mem_map(BASE, SIZE, UC_PROT_ALL)
    mu.mem_write(BASE, bytes(mem))
    mu.mem_map(0x10000000, 0x100000, UC_PROT_ALL)  # pile + zone « lib native »
    mu.mem_write(0x10080000, bytes(HOOKS))
    stop = 0x100F0000
    mu.reg_write(UC_ARM64_REG_SP, 0x10070000)
    mu.reg_write(UC_ARM64_REG_X30, stop)
    mu.reg_write(UC_ARM64_REG_X0, x0)
    mu.reg_write(UC_ARM64_REG_X1, x1)
    mu.reg_write(UC_ARM64_REG_X8, 0x10060000)
    mu.emu_start(BASE + addr, stop, count=10000)
    assert mu.reg_read(UC_ARM64_REG_PC) == stop, "n'est pas revenu à l'appelant"
    return mu.reg_read(UC_ARM64_REG_X0), mu.mem_read(0x10060000, 16)


# « lib native » : hook qui remplace (renvoie 0x77) et hook qui appelle l'original puis ajoute 0x100
HOOK_REPLACE = 0x10080000
HOOK_WRAP = 0x10080100
HOOKS = bytearray(0x200)


def make_hooks():
    HOOKS[0:8] = asm("mov x0, #0x77; ret", HOOK_REPLACE)


def wrap_hook(tramp):
    t = BASE + tramp
    code = (f"stp x29, x30, [sp, #-16]!; movz x16, #{t & 0xFFFF}; movk x16, #{(t >> 16) & 0xFFFF}, lsl #16; "
            "blr x16; add x0, x0, #0x100; ldp x29, x30, [sp], #16; ret")
    b = asm(code, HOOK_WRAP)
    HOOKS[0x100:0x100 + len(b)] = b


def set_slot(mem, slot, val):
    struct.pack_into("<Q", mem, slot, val)


def main():
    img = build_image()
    orig = {}
    make_hooks()
    tmp = tempfile.mkdtemp()
    src, dst = os.path.join(tmp, "in.so"), os.path.join(tmp, "out.so")
    open(src, "wb").write(img)
    for k in spec["hooks"]:
        orig[k["name"]] = run(img, k["rva"], 10, 20)[0]
    P.patch(src, dst)
    pat = bytearray(open(dst, "rb").read())
    # idempotence
    P.patch(dst, dst + "2")
    assert open(dst + "2", "rb").read() == bytes(pat), "re-patch non idempotent"
    # memsz étendu
    rw = P.rw_segment(pat)
    assert struct.unpack_from("<Q", pat, rw + 0x28)[0] == spec["rw_memsz"] + spec["slot_area"]
    ok = 0
    for k in spec["hooks"]:
        n = k["name"]
        r = run(pat, k["rva"], 10, 20)[0]
        assert r == orig[n], f"{n} : slot nul, {r:#x} != {orig[n]:#x}"
        set_slot(pat, k["slot"], HOOK_REPLACE)
        assert run(pat, k["rva"], 10, 20)[0] == 0x77, f"{n} : hook non appelé"
        wrap_hook(k["tramp"])
        set_slot(pat, k["slot"], HOOK_WRAP)
        r = run(pat, k["rva"], 10, 20)[0]
        assert r == orig[n] + 0x100, f"{n} : trampoline {r:#x} != {orig[n] + 0x100:#x}"
        set_slot(pat, k["slot"], 0)
        print(f"  {n:36} original={orig[n]:#x} : slot nul OK, hook OK, trampoline OK")
        ok += 1
    # StageInfo : slot nul -> get_Item(index) avec x2 = 0 ; slot posé -> hook
    _, res = run(pat, spec["si_rva"], 0x10050000, 7)
    v, x2 = struct.unpack("<QQ", res)
    assert v == 21 and x2 == 0, (v, x2)
    set_slot(pat, spec["slot_base"], HOOK_REPLACE)
    assert run(pat, spec["si_rva"], 0, 7)[0] == 0x77
    print(f"  StageInfo : repli get_Item OK, hook OK")
    # aucune autre différence que les octets attendus
    diff = [i for i in range(0, SIZE, 4) if img[i:i + 4] != pat[i:i + 4]]
    allowed = {spec["si_rva"] + 4 * i for i in range(7)} | {spec["cave_start"] + 4 * i for i in range((spec["cave_end"] - spec["cave_start"]) // 4)} \
        | {k["rva"] for k in spec["hooks"]} | {rw + 0x28, rw + 0x2C}
    bad = [hex(i) for i in diff if i not in allowed and i < spec["slot_base"]]
    assert not bad, f"octets modifiés hors zone : {bad[:10]}"
    # en-tête C cohérent
    P.header(os.path.join(tmp, "h.h"))
    print(f"patch_test : {ok} hooks + StageInfo OK, {len(diff)} mots modifiés, tous dans les zones prévues")


if __name__ == "__main__":
    main()
