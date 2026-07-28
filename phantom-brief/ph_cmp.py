import json
A=json.load(open('ph_all.json'))
w=lambda g: sum(x['price'] for x in g)/sum(x['area'] for x in g) if g else None
nf=lambda v,d=0: f'{v:,.{d}f}'.replace(',',' ').replace('.',',')
SHELL={'Без отделки','Черновая','Без ремонта'}
WB={'Предчистовая (white box)','Чистовая'}
REN={'Дизайнерский','Евроремонт','Под ключ / с мебелью','С ремонтом (тип не указан)','Косметический'}
grp=lambda f: 'shell' if f in SHELL else 'wb' if f in WB else 'ren' if f in REN else 'unk'
ORDER=['Turgenev','Николь','ФАНТОМ','Zvonarsky Deluxe','Дом Франка']
META={'ФАНТОМ':('Sense','первичка','III кв. 2027','Сухаревская'),
      'Turgenev':('НеоСтрой','первич. и вторич.','сдан 2023','Сретенский б-р'),
      'Николь':('—','первичка','2028','Пл. Революции'),
      'Дом Франка':('Трансстройинвест','первичка','2026','Тургеневская'),
      'Zvonarsky Deluxe':('—','вторичка','сдан 2019','Трубная')}
BAND=lambda n:[x for x in A[n] if 60<=x['area']<=200]
t1=[]; t2=[]
for n in ORDER:
    L=BAND(n); d,mk,dd,me=META[n]
    t1.append([n, mk, str(len(L)),
        f"{nf(min(x['area'] for x in L),1)}–{nf(max(x['area'] for x in L),1)}",
        f"{nf(min(x['price'] for x in L)/1e6,1)}–{nf(max(x['price'] for x in L)/1e6,1)}",
        f"{nf(min(x['ppm'] for x in L))}–{nf(max(x['ppm'] for x in L))}", nf(w(L))])
    g={k:[x for x in L if grp(x['fin'])==k] for k in ('shell','wb','ren','unk')}
    cell=lambda k: f"{len(g[k])} шт · {nf(w(g[k]))}" if g[k] else '—'
    t2.append([n, cell('shell'), cell('wb'), cell('ren'), cell('unk')])
print('— сопоставимая выборка 60–200 м² —')
for r in t1: print(' | '.join(r))
print()
for r in t2: print(' | '.join(r))
FIT=300_000
ready={}
for n in ORDER:
    L=BAND(n)
    fin=[x for x in L if grp(x['fin']) in ('ren','wb')]     # чистовая и дизайнерская — обе «готово»
    sh=[x for x in L if grp(x['fin'])=='shell']
    if fin:  ready[n]=(w(fin), 0,   f'{len(fin)} лотов с готовой отделкой')
    elif sh: ready[n]=(w(sh), FIT,  f'{len(sh)} лотов без отделки + отделка')
    else:    ready[n]=(w(L),  0,    f'{len(L)} лота, отделка не указана')
print('\n— метр готовой квартиры (отделка 300 тыс. ₽/м²) —')
for n,(b,f,note) in sorted(ready.items(), key=lambda kv:-(kv[1][0]+kv[1][1])):
    print(f'  {n:18s} {nf(b+f):>11s}  ({note})')
json.dump({'t1':t1,'t2':t2,'ready':{k:list(v) for k,v in ready.items()},'fit':FIT},
          open('ph_cmp.json','w'), ensure_ascii=False, indent=1)
