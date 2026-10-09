import UnityPy, sys, collections, os
d=sys.argv[1]
env=UnityPy.load(d)
c=collections.Counter(); ta=[]
for o in env.objects:
    t=o.type.name; c[t]+=1
    if t=="TextAsset":
        try:
            x=o.read(); ta.append((x.m_Name, len(x.m_Script), os.path.basename(o.assets_file.name)))
        except Exception as e: ta.append(("ERR",str(e),""))
print(c.most_common(40))
print(len(ta))
for t in sorted(ta)[:400]: print(t)
