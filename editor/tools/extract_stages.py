#!/usr/bin/env python3
"""Extrait le TextAsset "stages" (binaire des 300 niveaux) d'un APK. Requiert UnityPy."""
import io, sys, zipfile
import UnityPy

apk, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if not name.startswith("assets/bin/Data/") or name.endswith((".resource", ".dll", ".dat")):
            continue
        data = z.read(name)
        if len(data) < 20 or b"stages" not in data:
            continue
        try:
            env = UnityPy.load(data)
        except Exception:
            continue
        for o in env.objects:
            if o.type.name == "TextAsset":
                ta = o.read()
                if ta.m_Name == "stages":
                    b = ta.m_Script
                    if isinstance(b, str):
                        b = b.encode("utf-8", "surrogateescape")
                    open(out, "wb").write(b)
                    print(f"stages : {len(b)} octets depuis {name}")
                    sys.exit(0)
sys.exit("TextAsset 'stages' introuvable")
