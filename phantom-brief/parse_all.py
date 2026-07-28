import xml.etree.ElementTree as ET, json, glob, re
NS='{urn:schemas-microsoft-com:office:spreadsheet}'
def sheets(path):
    out={}
    for ws in ET.parse(path).getroot().findall(f'{NS}Worksheet'):
        name=ws.get(f'{NS}Name'); tab=ws.find(f'{NS}Table')
        if tab is None: continue
        rows=[]
        for row in tab.findall(f'{NS}Row'):
            cells=[]; idx=0
            for c in row.findall(f'{NS}Cell'):
                ix=c.get(f'{NS}Index')
                if ix:
                    ix=int(ix)-1
                    while idx<ix: cells.append(''); idx+=1
                dt=c.find(f'{NS}Data')
                cells.append('' if dt is None else ''.join(dt.itertext())); idx+=1
            rows.append(cells)
        out[name]=rows
    return out

FILES={
 'МАСТЕРС':'da6caa93-cian_______________________20260728_169_______________.xls',
 'Прайм Парк':'5b9a46a6-cian__________primepark_20260728_341_______________.xls',
 'Царская площадь':'45176a9f-cian________________20260728_65_____.xls',
 'Лайнер':'ae074e5b-cian________20260728_62_____.xls',
 'Династия':'fe811b60-cian__________20260728_39_____.xls',
 'Лица':'89fd9a0a-cian______20260728_13_____.xls',
}
BASE='/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/'
def lots_of(path):
    sh=sheets(path)
    key=next(k for k in sh if k.startswith('Все_лоты'))
    rows=sh[key]
    hi=next(i for i,r in enumerate(rows) if r and r[0]=='№')
    H={n:i for i,n in enumerate(rows[hi])}
    g=lambda r,n: r[H[n]] if n in H and H[n]<len(r) else ''
    out=[]
    for r in rows[hi+1:]:
        if not g(r,'Площадь, м²'): continue
        try: area=float(g(r,'Площадь, м²')); price=int(g(r,'Цена, ₽')); ppm=int(g(r,'Цена за м², ₽'))
        except ValueError: continue
        out.append(dict(cat=g(r,'Категория'), area=area, price=price, ppm=ppm,
                        fin=g(r,'Отделка/ремонт'), seller=g(r,'Тип продавца'),
                        floor=g(r,'Этаж'), metro=g(r,'Метро'), tometro=g(r,'До метро, мин'),
                        year=g(r,'Год дома'), corp=g(r,'Корпус / секция')))
    return out, sh
if __name__=='__main__':
    all={}
    for name,f in FILES.items():
        L,sh=lots_of(BASE+f)
        all[name]=L
        from collections import Counter
        print(f'\n=== {name}: {len(L)} лотов | листы: {list(sh)}')
        print('  отделка:', dict(Counter(x["fin"] for x in L)))
        print('  продавец:', dict(Counter(x["seller"] for x in L)))
        print('  метро:', dict(Counter(x["metro"] for x in L)), '| до метро:', sorted({x["tometro"] for x in L}))
        print('  год дома:', dict(Counter(x["year"] for x in L)))
    json.dump(all, open('all_lots.json','w'), ensure_ascii=False)
