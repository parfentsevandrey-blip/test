import json
A=json.load(open('k12_all.json'))
w=lambda g: sum(x['price'] for x in g)/sum(x['area'] for x in g) if g else None
nf=lambda v,d=0: f'{v:,.{d}f}'.replace(',',' ').replace('.',',')
SHELL={'Без отделки','Черновая','Без ремонта'}
WB={'Предчистовая (white box)','Чистовая'}
REN={'Дизайнерский','Евроремонт','Под ключ / с мебелью','С ремонтом (тип не указан)','Косметический'}
grp=lambda f:'shell' if f in SHELL else 'wb' if f in WB else 'ren' if f in REN else 'unk'
OUR={'price':162_500_000,'area':95.0,'ppm':1_710_526}
FIT=250_000
BAND=lambda n:[x for x in A[n] if 85<=x['area']<=115]
ORDER=['Кутузовский XII','Бадаевский','Capital Towers','Дом Дау','Выделенная область']
LABEL={'Выделенная область':'Москва-Сити (вторичка)'}
print('=== сопоставимая выборка 85–115 м² ===')
rows=[]
for n in ORDER:
    L=BAND(n)
    if not L: continue
    fin=[x for x in L if grp(x['fin']) in ('ren','wb')]
    sh=[x for x in L if grp(x['fin'])=='shell']
    ready = (w(fin),0,f'{len(fin)} с отделкой') if fin else ((w(sh),FIT,f'{len(sh)} без отделки + отделка') if sh else (w(L),0,f'{len(L)} отделка н/д'))
    rows.append((LABEL.get(n,n), len(L), w(L), ready))
    print(f"{LABEL.get(n,n):26s} n={len(L):3d}  Ø{w(L):>10,.0f}  готовая: {ready[0]+ready[1]:>10,.0f}  ({ready[2]})".replace(',',' '))
print(f"\nНАШ ЛОТ                    {' ':6s}  Ø{OUR['ppm']:>10,.0f}".replace(',',' '))
for n,cnt,avg,(b,f,note) in rows:
    print(f'  vs {n:26s} {100*(OUR["ppm"]/(b+f)-1):+5.0f} %')
json.dump({'rows':[[n,cnt,avg,b,f,note] for n,cnt,avg,(b,f,note) in rows],'our':OUR,'fit':FIT},
          open('k12_cmp.json','w'), ensure_ascii=False, indent=1)
