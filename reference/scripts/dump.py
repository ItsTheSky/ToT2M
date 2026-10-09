import UnityPy, sys, os, collections
d,out=sys.argv[1],sys.argv[2]; os.makedirs(out,exist_ok=True)
env=UnityPy.load(d); c=collections.Counter(); names=collections.Counter()
for o in env.objects:
    c[o.type.name]+=1
    if o.type.name=="TextAsset":
        x=o.read(); n=x.m_Name; names[n]+=1
        fn=n if names[n]==1 else f"{n}__dup{names[n]}"
        data=x.m_Script
        if isinstance(data,str): data=data.encode('utf-8','surrogateescape')
        open(os.path.join(out,fn+".txt"),"wb").write(data)
print(c.most_common(40)); print(sum(names.values()), [k for k,v in names.items() if v>1][:20])
