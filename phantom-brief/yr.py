"""Pull resale listings (photo, price, area, renovation) from Yandex Realty by ЖК page."""
import re, json, subprocess, urllib.parse

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'

def state(url):
    subprocess.run(['curl','-sL','-A',UA,'-H','Accept-Language: ru-RU','--max-time','90',url,'-o','_yr.html'],
                   check=True)
    h=open('_yr.html',encoding='utf-8',errors='ignore').read()
    i=h.find('window.INITIAL_STATE = ')
    if i<0: return None
    j=i+len('window.INITIAL_STATE = ')
    dec=json.JSONDecoder()
    obj,_=dec.raw_decode(h[j:])
    return obj

def walk(o, key):
    if isinstance(o, dict):
        if key in o and isinstance(o[key], list): yield o[key]
        for v in o.values(): yield from walk(v, key)
    elif isinstance(o, list):
        for v in o: yield from walk(v, key)

REN = {'EURO':'евроремонт','COSMETIC_DONE':'косметический','DESIGNER_RENOVATION':'дизайнерский',
       'RENOVATED':'с ремонтом','NEEDS_RENOVATION':'требует ремонта','NORMAL':'обычный',
       'PRIME_RENOVATION':'дизайнерский','GOOD':'хороший','TURNKEY':'под ключ',
       'CLEAN':'чистовая','ROUGH':'черновая','WHITE_BOX':'white box'}

def offers(url):
    st=state(url)
    if st is None: return []
    best=[]
    for lst in walk(st, 'entities'):
        for o in lst:
            if not isinstance(o, dict) or 'offerId' not in o: continue
            best.append(o)
    if not best:
        for lst in walk(st, 'items'):
            for o in lst:
                if isinstance(o, dict) and 'offerId' in o and 'price' in o: best.append(o)
    out=[]
    for o in best:
        img=None
        ph=o.get('fullImages') or o.get('appMiddleImages') or o.get('appLargeImages') or []
        if ph: img=ph[0] if isinstance(ph[0],str) else ph[0].get('appLarge') or ph[0].get('orig')
        pr=o.get('price') or {}
        area=(o.get('area') or {}).get('value')
        out.append({'id':o.get('offerId'),'price':pr.get('value') or pr.get('valueForWhole'),
                    'area':area,'ren':REN.get((o.get('apartment') or {}).get('renovation'), (o.get('apartment') or {}).get('renovation')),
                    'floor':(o.get('floorsOffered') or [None])[0],'floors':o.get('floorsTotal'),'year':(o.get('building') or {}).get('builtYear'),'rooms':o.get('roomsTotal'),
                    'img':img,'url':'https://realty.yandex.ru/offer/%s/'%o.get('offerId'),
                    'photos':len(ph)})
    return out

if __name__=='__main__':
    import sys
    for o in offers(sys.argv[1])[:8]:
        print(o['rooms'],'к', o['area'],'м²', o['price'], o['ren'], 'фото:', o['photos'])
        print('   ', (o['img'] or '')[:100])
