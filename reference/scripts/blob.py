import sys,glob,re,os
import xml.etree.ElementTree as ET
d=sys.argv[1]
maps={}
for f in glob.glob(d+"/*.txt"):
    n=os.path.basename(f)[:-4]
    if not re.match(r'^\d+(_\d+)?$',n): continue
    r=ET.parse(f).getroot(); w,h=int(r.get('width')),int(r.get('height'))
    g=[int(t.get('gid'))&0x1FFFFFFF for t in r.find('layer/data').findall('tile')]
    maps[n]=(w,h,r.get('renderorder'),g)
for blob in ['stages','sections']:
    b=open(f"{d}/{blob}.txt",'rb').read()
    print(blob,len(b), b[:6].hex())
    # try parse: records of [?][?][?]...
    for name,(w,h,ro,g) in sorted(maps.items(),key=lambda x:(len(x[0]),x[0]))[:3]:
        pass
    # find positions of each map's raw bytes (rows as-is, or rows reversed)
    hits=[]
    for name,(w,h,ro,g) in maps.items():
        if max(g)>255: continue
        raw=bytes(g)
        rows=[g[i*w:(i+1)*w] for i in range(h)]
        rev=bytes(sum(rows[::-1],[]))
        p=b.find(raw); q=b.find(rev)
        if p>=0 or q>=0: hits.append((min(x for x in (p,q) if x>=0),name,w,h,ro,'asis' if p>=0 else 'rev'))
    hits.sort(); print(len(hits)); 
    for h_ in hits[:8]: print(h_, b[h_[0]-4:h_[0]].hex())
    print(hits[-3:])
