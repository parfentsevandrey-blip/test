import xml.etree.ElementTree as ET, sys
sys.path.insert(0,'.')
from parse_all import BASE
NS='{urn:schemas-microsoft-com:office:spreadsheet}'
FILES={'Кутузовский XII':'cb26e6ba-cian____________xii_20260730_14_____.xls',
 'Апартаменты Сити':'7ce832e3-cian___________________20260730_133_____.xls',
 'Дом Дау':'f462fad3-cian________20260730_286_____.xls',
 'Capital Towers':'64d8e1c4-cian_capitaltowers______________20260730_129_____.xls',
 'Бадаевский':'cdf7eb19-cian____________20260730_144______1.xls',
 'Веспер Кутузовский':'8581a047-cian___________________20260730_88_____.xls'}
def rows(path):
    for ws in ET.parse(path).getroot().findall(f'{NS}Worksheet'):
        if not ws.get(f'{NS}Name','').startswith('Все_лоты'): continue
        out=[]
        for row in ws.find(f'{NS}Table').findall(f'{NS}Row'):
            cells=[]; href=None; idx=0
            for c in row.findall(f'{NS}Cell'):
                ix=c.get(f'{NS}Index')
                if ix:
                    ix=int(ix)-1
                    while idx<ix: cells.append(''); idx+=1
                if c.get(f'{NS}HRef'): href=c.get(f'{NS}HRef')
                dt=c.find(f'{NS}Data'); cells.append('' if dt is None else ''.join(dt.itertext())); idx+=1
            out.append((cells,href))
        return out
    return []
def lots(name):
    rs=rows(BASE+FILES[name])
    hi=next(i for i,(c,_) in enumerate(rs) if c and c[0]=='№')
    H={n:i for i,n in enumerate(rs[hi][0])}
    g=lambda c,k: c[H[k]] if k in H and H[k]<len(c) else ''
    out=[]
    for c,href in rs[hi+1:]:
        if not g(c,'Площадь, м²'): continue
        try: out.append(dict(cat=g(c,'Категория'),area=float(g(c,'Площадь, м²')),floor=g(c,'Этаж'),
             corp=g(c,'Корпус / секция'),price=int(g(c,'Цена, ₽')),ppm=int(g(c,'Цена за м², ₽')),
             fin=g(c,'Отделка/ремонт'),seller=g(c,'Тип продавца'),
             url=(href or '').split('?')[0]))
        except ValueError: pass
    return out
if __name__=='__main__':
    import json
    all={n:lots(n) for n in FILES}
    json.dump(all, open('k12_linked.json','w'), ensure_ascii=False)
    our=[x for x in all['Кутузовский XII'] if abs(x['price']-162_500_000)<1e5][0]
    print('НАШ ЛОТ:', our)
    print('\n=== альтернативы 150–175 млн ₽, площадь 85–130 м² ===')
    for n,L in all.items():
        c=[x for x in L if 150e6<=x['price']<=175e6 and 85<=x['area']<=130]
        c.sort(key=lambda x:x['ppm'])
        if not c: continue
        print(f'-- {n}: {len(c)}')
        for x in c[:3]:
            print(f"     {x['cat']:>3s}к {x['area']:6.1f} м² эт.{x['floor']:>6s} {x['price']/1e6:6.1f} млн {x['ppm']:>9,d} ₽/м² {x['fin'] or 'н/д':<14s} {x['url'][:44]}".replace(',',' '))
