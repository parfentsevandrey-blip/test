import json, math
L=json.load(open('phantom_lots.json'))
w=lambda g: sum(x['price'] for x in g)/sum(x['area'] for x in g)
nf=lambda v,d=0: f'{v:,.{d}f}'.replace(',',' ').replace('.',',')
fn=lambda s:int(s.split('/')[0]); ft=lambda s:int(s.split('/')[1])
NAME={'1':'1-комнатные','2':'2-комнатные','3':'3-комнатные','4+':'4+ комнатные'}
W=w(L)
types=[]
for c in ['1','2','3','4+']:
    g=[x for x in L if x['cat']==c]
    types.append([NAME[c], str(len(g)),
        f"{nf(min(x['area'] for x in g),1)}–{nf(max(x['area'] for x in g),1)}",
        f"{nf(min(x['price'] for x in g)/1e6,1)}–{nf(max(x['price'] for x in g)/1e6,1)}",
        nf(sum(x['price'] for x in g)/len(g)/1e6,1),
        f"{nf(min(x['ppm'] for x in g))}–{nf(max(x['ppm'] for x in g))}", nf(w(g))])
types.append(['ИТОГО / средневзв.', str(len(L)),
    f"{nf(min(x['area'] for x in L),1)}–{nf(max(x['area'] for x in L),1)}",
    f"{nf(min(x['price'] for x in L)/1e6,1)}–{nf(max(x['price'] for x in L)/1e6,1)}",
    nf(sum(x['price'] for x in L)/len(L)/1e6,1),
    f"{nf(min(x['ppm'] for x in L))}–{nf(max(x['ppm'] for x in L))}", nf(W)])
corps=[]
for c in sorted({x['corp'] for x in L}):
    g=[x for x in L if x['corp']==c]; p=w(g)
    corps.append([c.replace('Корпус ','').capitalize(), str(sorted({ft(x['floor']) for x in g})[0]), str(len(g)),
        f"{nf(min(x['area'] for x in g),1)}–{nf(max(x['area'] for x in g),1)}",
        f"{nf(min(x['price'] for x in g)/1e6,1)}–{nf(max(x['price'] for x in g)/1e6,1)}",
        nf(p), f"{'+' if p>=W else '−'}{abs(round((p/W-1)*100))}%"])
floors=[]
for a,b,lab in [(1,3,'1–3'),(4,6,'4–6'),(7,9,'7–9')]:
    g=[x for x in L if a<=fn(x['floor'])<=b]
    if not g: continue
    p=w(g); floors.append([lab, str(len(g)), nf(p), f"{'+' if p>=W else '−'}{abs(round((p/W-1)*100))}%"])
meta={'lots':len(L),'area':nf(sum(x['area'] for x in L)),'volume':nf(sum(x['price'] for x in L)/1e9,1),
      'w':nf(W),'avg':nf(sum(x['price'] for x in L)/len(L)/1e6,1),
      'simple':nf(sum(x['ppm'] for x in L)/len(L)),
      'min':nf(min(x['price'] for x in L)/1e6,1),'max':nf(max(x['price'] for x in L)/1e6,1)}
json.dump({'types':types,'corps':corps,'floors':floors,'meta':meta},
          open('ph_tables.json','w'), ensure_ascii=False, indent=1)
for t in (types,corps,floors): 
    for r in t: print(' | '.join(r))
    print()
print(meta)
