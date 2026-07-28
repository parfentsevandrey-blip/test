import json, statistics as st
d=json.load(open('data.json'))
rows=d['Все_лоты']
hi=next(i for i,r in enumerate(rows) if r and r[0]=='№')
H={n:i for i,n in enumerate(rows[hi])}
lots=[]
for r in rows[hi+1:]:
    if len(r)<20 or not r[H['Площадь, м²']]: continue
    lots.append(dict(cat=r[H['Категория']], area=float(r[H['Площадь, м²']]),
        fl=r[H['Этаж']], sec=r[H['Корпус / секция']],
        price=int(r[H['Цена, ₽']]), ppm=int(r[H['Цена за м², ₽']])))
if __name__=='__main__':
    print('header row',hi,'total',len(lots))
    order=['Студия','1','2','3']
    tot_a=sum(l['area'] for l in lots); tot_p=sum(l['price'] for l in lots)
    for c in order:
        g=[l for l in lots if l['cat']==c]
        a=[l['area'] for l in g]; p=[l['price'] for l in g]; m=[l['ppm'] for l in g]
        print(f"{c:7s} n={len(g):3d} S={min(a):6.1f}-{max(a):6.1f} (ср {sum(a)/len(a):6.1f}) "
              f"P={min(p)/1e6:6.2f}-{max(p)/1e6:7.2f} млн (ср {sum(p)/len(p)/1e6:6.2f}) "
              f"ppm={min(m):7d}-{max(m):7d} ср_взв={int(sum(p)/sum(a)):7d} мед={int(st.median(m)):7d}")
    print(f"ИТОГО n={len(lots)} S={min(l['area'] for l in lots):.1f}-{max(l['area'] for l in lots):.1f} "
          f"P={min(l['price'] for l in lots)/1e6:.2f}-{max(l['price'] for l in lots)/1e6:.2f} "
          f"ppm_взв={int(tot_p/tot_a)} S_общ={tot_a:.0f} объём={tot_p/1e9:.2f} млрд")
    fn=lambda s:int(s.split('/')[0]); ft=lambda s:int(s.split('/')[1])
    print('\nвысотность секций:', sorted({ft(l['fl']) for l in lots}),
          ' этажи в продаже:', min(fn(l['fl']) for l in lots),'-',max(fn(l['fl']) for l in lots))
    secs={}
    for l in lots: secs.setdefault(l['sec'],[]).append(l)
    print()
    for s in sorted(secs):
        g=secs[s]
        print(f"{s:10s} n={len(g):3d} этажность={sorted({ft(x['fl']) for x in g})} "
              f"ppm={int(sum(x['price'] for x in g)/sum(x['area'] for x in g)):7d} "
              f"P={min(x['price'] for x in g)/1e6:.1f}-{max(x['price'] for x in g)/1e6:.1f} млн "
              f"cats={sorted({x['cat'] for x in g})}")
    print()
    for a,b,lab in [(2,5,'2–5'),(6,10,'6–10'),(11,15,'11–15'),(16,23,'16–23')]:
        g=[l for l in lots if a<=fn(l['fl'])<=b]
        if g: print(f"этаж {lab:6s} n={len(g):3d} ppm_взв={int(sum(x['price'] for x in g)/sum(x['area'] for x in g)):7d}")
