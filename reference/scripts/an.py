import sys,os,glob,re,collections,hashlib
import xml.etree.ElementTree as ET
d=sys.argv[1]
files=[f for f in glob.glob(d+"/*.txt") if re.match(r'^\d+(_\d+)?$',os.path.basename(f)[:-4])]
tsh=collections.Counter(); layers=collections.Counter(); og=collections.Counter(); enc=collections.Counter(); ro=collections.Counter(); attrs=collections.Counter(); objtypes=collections.Counter(); mapprops=collections.Counter(); sizes=[]
gids=collections.Counter()
for f in files:
    r=ET.parse(f).getroot()
    ro[r.get('renderorder')]+=1
    ts=r.find('tileset'); tsh[hashlib.md5(ET.tostring(ts)).hexdigest()[:8]]+=1
    for p in r.findall('properties/property'): mapprops[p.get('name')]+=1
    for l in r.findall('layer'):
        layers[l.get('name')]+=1
        dd=l.find('data'); enc[(dd.get('encoding'),dd.get('compression'))]+=1
        for t in dd.findall('tile'): gids[int(t.get('gid'))]+=1
    for g in r.findall('objectgroup'):
        og[g.get('name')]+=1
        for o in g.findall('object'):
            objtypes[(o.get('type'),o.get('name'),o.get('gid') is not None)]+=1
    sizes.append((int(r.get('width')),int(r.get('height'))))
print("files",len(files)); print("renderorder",ro); print("tilesets",tsh); print("layers",layers); print("enc",enc); print("objgroups",og); print("objtypes",objtypes.most_common(30)); print("mapprops",mapprops)
print("w range",min(s[0] for s in sizes),max(s[0] for s in sizes),"h range",min(s[1] for s in sizes),max(s[1] for s in sizes))
print("gids used",sorted(gids.items()))
