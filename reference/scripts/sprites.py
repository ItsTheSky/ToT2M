import UnityPy, sys, os, collections, json
d,out=sys.argv[1],sys.argv[2]; os.makedirs(out+'/sprites',exist_ok=True); os.makedirs(out+'/textures',exist_ok=True)
env=UnityPy.load(d); names=collections.Counter(); idx=[]
for o in env.objects:
    t=o.type.name
    if t not in ('Sprite','Texture2D'): continue
    try:
        x=o.read(); n=x.m_Name or f'unnamed_{o.path_id}'
        names[(t,n)]+=1; fn=n if names[(t,n)]==1 else f'{n}__{names[(t,n)]}'
        fn=''.join(c if c.isalnum() or c in '-_.' else '_' for c in fn)
        sub='sprites' if t=='Sprite' else 'textures'
        img=x.image; img.save(f'{out}/{sub}/{fn}.png')
        e={'type':t,'name':n,'file':f'{sub}/{fn}.png','path_id':o.path_id,'assets_file':os.path.basename(o.assets_file.name),'size':list(img.size)}
        if t=='Sprite':
            try: e['texture']=x.m_RD.texture.read().m_Name
            except Exception: pass
            try: e['pixelsToUnits']=x.m_PixelsToUnits
            except Exception: pass
        idx.append(e)
    except Exception as ex:
        idx.append({'type':t,'error':str(ex),'path_id':o.path_id})
json.dump(idx,open(out+'/index.json','w'),indent=1,ensure_ascii=False)
print(len(idx), sum(1 for e in idx if 'error' in e))
