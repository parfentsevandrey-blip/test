import json, statistics as st
A=json.load(open('all_lots.json'))
SHELL={'Без отделки','Черновая','Без ремонта'}; WB={'Предчистовая (white box)','Чистовая'}
REN={'Дизайнерский','Евроремонт','Под ключ / с мебелью','С ремонтом (тип не указан)','Косметический'}
def grp(f): return 'shell' if f in SHELL else 'wb' if f in WB else 'ren' if f in REN else 'unk'
def w(ls): return sum(x['price'] for x in ls)/sum(x['area'] for x in ls) if ls else None
ORDER=['МАСТЕРС','Прайм Парк','Династия','Царская площадь','Лица','Лайнер']
BAND={n:[x for x in A[n] if 40<=x['area']<=140 and 'лан' not in x['cat']] for n in ORDER}

# effective finish class: sourced imputation for Prime Park developer stock (white box / чистовая)
def eff(n,x):
    g=grp(x['fin'])
    if g=='unk' and n=='Прайм Парк' and x['seller']=='Застройщик': return 'wb'
    return g
REN_CUT, WB_CUT = 120_000, 18_000
def adjusted(n, cut=REN_CUT):
    ls=[x for x in BAND[n] if eff(n,x)!='unk']
    if not ls: return None,0
    num=den=0
    for x in ls:
        g=eff(n,x); a = cut if g=='ren' else WB_CUT if g=='wb' else 0
        num+=max(x['price']-a*x['area'],0); den+=x['area']
    return num/den, len(ls)

R={}
print(f"{'ЖК':16s} {'n':>4s} {'как есть':>10s} {'опред.':>7s} {'привед.':>10s} {'Δ%':>6s}")
for n in ORDER:
    L=BAND[n]; adj,k = adjusted(n)
    R[n]=dict(n=len(L), asis=w(L), adj=adj, k=k,
              lo=min(x['ppm'] for x in L), hi=max(x['ppm'] for x in L),
              area=sum(x['area'] for x in L)/len(L),
              lot=sum(x['price'] for x in L)/len(L),
              ren=sum(1 for x in L if eff(n,x)=='ren'), det=k)
    print(f"{n:16s} {len(L):4d} {w(L):10,.0f} {k:4d}/{len(L):<3d} {adj:10,.0f} {100*(adj/w(L)-1):5.0f}%".replace(',',' '))

M=R['МАСТЕРС']['asis']
print('\n— позиция МАСТЕРС —')
for n in ORDER[1:]:
    print(f"  vs {n:16s} как есть {100*(M/R[n]['asis']-1):+5.0f} %   приведённо {100*(M/R[n]['adj']-1):+5.0f} %")
print(f"\n  МАСТЕРС со скидкой 10 %: {M*0.9:,.0f} ₽/м²".replace(',',' '))
for n in ORDER[1:]:
    print(f"    vs {n:16s} приведённо {100*(M*0.9/R[n]['adj']-1):+5.0f} %")

print('\n— чувствительность приведённой цены к ставке вычета —')
for cut in (80_000,120_000,160_000):
    print(f"  −{cut//1000} тыс: " + ' | '.join(f"{n} {adjusted(n,cut)[0]:,.0f}".replace(',',' ') for n in ORDER))

# finish structure per project (determined only)
print('\n— структура отделки (определённая + импутация ПП-застройщик) —')
FIN={}
for n in ORDER:
    L=[x for x in BAND[n] if eff(n,x)!='unk']
    d={g:[x for x in L if eff(n,x)==g] for g in ('shell','wb','ren')}
    FIN[n]={g:(len(v), w(v)) for g,v in d.items()}
    print(f"  {n:16s} " + '  '.join(f"{g}:{len(v):3d} {(format(w(v),',.0f') if v else '—'):>9s}".replace(',',' ') for g,v in d.items()))
json.dump({'R':R,'FIN':FIN}, open('cmp.json','w'), ensure_ascii=False, indent=1)
