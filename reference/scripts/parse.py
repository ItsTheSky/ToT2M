import sys,collections
d=sys.argv[1]
b=open(f"{d}/stages.txt",'rb').read(); i=0; recs=[]
while i<len(b):
    f,w,h=b[i],b[i+1],b[i+2]; recs.append((f,w,h)); i+=3+w*h
print("stages",len(recs),"end",i,len(b)); print(collections.Counter(r[0] for r in recs))
print([ (n+1,r) for n,r in enumerate(recs) if r[0]!=0][:40])
b=open(f"{d}/sections.txt",'rb').read(); i=0; recs=[]
while i<len(b):
    g,n=b[i],b[i+1]; recs.append((g,n)); i+=2+n
print("sections",len(recs),"end",i,len(b)); print(collections.Counter(recs))
