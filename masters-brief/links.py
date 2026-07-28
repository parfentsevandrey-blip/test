import xml.etree.ElementTree as ET, json, sys
sys.path.insert(0,'.')
from parse_all import BASE, FILES
FILES.update({'Муза':'0b9f9c6b-cian__________________________________20260728_52_____.xls',
              'Дом на Часовой':'18afbe32-cian______________20260728_51_____.xls'})
NS='{urn:schemas-microsoft-com:office:spreadsheet}'
def rows_with_href(path):
    for ws in ET.parse(path).getroot().findall(f'{NS}Worksheet'):
        if not ws.get(f'{NS}Name','').startswith('Все_лоты'): continue
        tab=ws.find(f'{NS}Table'); out=[]
        for row in tab.findall(f'{NS}Row'):
            cells=[]; href=None; idx=0
            for c in row.findall(f'{NS}Cell'):
                ix=c.get(f'{NS}Index')
                if ix:
                    ix=int(ix)-1
                    while idx<ix: cells.append(''); idx+=1
                h=c.get(f'{NS}HRef')
                if h: href=h
                dt=c.find(f'{NS}Data')
                cells.append('' if dt is None else ''.join(dt.itertext())); idx+=1
            out.append((cells,href))
        return out
    return []

def find(project, area, floor=None, price=None):
    rs=rows_with_href(BASE+FILES[project])
    hi=next(i for i,(c,_) in enumerate(rs) if c and c[0]=='№')
    H={n:i for i,n in enumerate(rs[hi][0])}
    best=None
    for c,href in rs[hi+1:]:
        if len(c)<=H['Цена, ₽'] or not c[H['Площадь, м²']]: continue
        if abs(float(c[H['Площадь, м²']])-area)>0.05: continue
        if floor and c[H['Этаж']]!=floor: continue
        if price and abs(int(c[H['Цена, ₽']])-price)>1000: continue
        best=(c[H['Площадь, м²']], c[H['Этаж']], int(c[H['Цена, ₽']]), int(c[H['Цена за м², ₽']]), c[H['Отделка/ремонт']], href)
    return best

TARGETS=[('МАСТЕРС',80.9,'17/23',61587822),('Прайм Парк',81.7,'39/42',None),
         ('Царская площадь',79.4,'4/18',None),('Династия',72.0,'23/24',None),
         ('Лица',77.4,'15/27',None),('Лайнер',77.1,'8/15',None)]
res={}
for n,a,f,p in TARGETS:
    r=find(n,a,f,p)
    res[n]=r
    print(f'{n:16s}', r[:5] if r else 'НЕ НАЙДЕН', (r[5][:52] if r and r[5] else ''))
json.dump({k:v for k,v in res.items()}, open('ex_links.json','w'), ensure_ascii=False, indent=1)
