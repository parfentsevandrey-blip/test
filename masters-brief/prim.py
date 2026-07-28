import json, math
A=json.load(open('all_lots.json'))
P=['МАСТЕРС','Муза','Дом на Часовой']
w=lambda ls: sum(x['price'] for x in ls)/sum(x['area'] for x in ls) if ls else None
nf=lambda v,d=0: f'{v:,.{d}f}'.replace(',',' ').replace('.',',')
print('=== первичка, все лоты (100 % застройщик, 100 % без отделки) ===')
for n in P:
    L=A[n]; a=[x['area'] for x in L]; p=[x['price'] for x in L]
    print(f"{n:16s} n={len(L):3d}  Ø{w(L):9,.0f}  диап {min(x['ppm'] for x in L):8,.0f}–{max(x['ppm'] for x in L):9,.0f}"
          f"  S {min(a):5.1f}–{max(a):6.1f} (ср {sum(a)/len(a):5.1f})  лот {min(p)/1e6:5.1f}–{max(p)/1e6:6.1f} (ср {sum(p)/len(p)/1e6:5.1f}) млн".replace(',',' '))
print('\n=== по типам квартир, ₽/м² ===')
NAME={'Студия':'Студии','1':'1-комн','2':'2-комн','3':'3-комн','4+':'4+ комн'}
print(f"{'Тип':9s}" + ''.join(f'{n:>34s}' for n in P))
for c in ['Студия','1','2','3','4+']:
    row=f'{NAME[c]:9s}'
    for n in P:
        g=[x for x in A[n] if x['cat']==c]
        row += f"{(str(len(g))+' шт · '+nf(w(g))+' · '+nf(sum(x['area'] for x in g)/len(g),1)+' м²') if g else '—':>34s}"
    print(row)
print('\n=== позиция МАСТЕРС ===')
M=w(A['МАСТЕРС'])
for n in P[1:]:
    print(f'  vs {n:16s} {100*(M/w(A[n])-1):+5.0f} %   (со скидкой 10 %: {100*(M*0.9/w(A[n])-1):+5.0f} %)')
print('\n=== сопоставимый бюджет: 2-комн ===')
for n in P:
    g=[x for x in A[n] if x['cat']=='2']
    print(f'  {n:16s} {len(g):2d} шт  S {min(x["area"] for x in g):5.1f}–{max(x["area"] for x in g):5.1f}  '
          f'бюджет {min(x["price"] for x in g)/1e6:5.1f}–{max(x["price"] for x in g)/1e6:5.1f} млн  Ø {w(g):,.0f} ₽/м²'.replace(',',' '))
D=[]
for n in P:
    L=A[n]
    D.append([n,str(len(L)),nf(w(L)),f"{nf(min(x['ppm'] for x in L))}–{nf(max(x['ppm'] for x in L))}",
              f"{nf(min(x['area'] for x in L),1)}–{nf(max(x['area'] for x in L),1)}",
              f"{nf(min(x['price'] for x in L)/1e6,1)}–{nf(max(x['price'] for x in L)/1e6,1)}",
              nf(sum(x['price'] for x in L)/len(L)/1e6,1)])
T=[]
for c in ['Студия','1','2','3','4+']:
    r=[NAME[c]]
    for n in P:
        g=[x for x in A[n] if x['cat']==c]
        r.append(f'{len(g)} шт · {nf(w(g))}' if g else '—')
    T.append(r)
json.dump({'main':D,'byroom':T}, open('prim_tables.json','w'), ensure_ascii=False, indent=1)
