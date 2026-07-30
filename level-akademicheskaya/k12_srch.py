"""Search Yandex Realty for renovated lots in the location within our budget band."""
import sys, json
from yr import state, walk, REN

def offers_from(url):
    st = state(url)
    if st is None: return []
    seen, out = set(), []
    for lst in walk(st, 'entities'):
        for o in lst:
            if not isinstance(o, dict) or 'offerId' not in o: continue
            oid = o['offerId']
            if oid in seen: continue
            seen.add(oid)
            ph = o.get('fullImages') or o.get('appLargeImages') or o.get('appMiddleImages') or []
            imgs = [x if isinstance(x, str) else (x.get('appLarge') or x.get('orig')) for x in ph]
            pr = o.get('price') or {}
            bl = o.get('building') or {}
            ap = o.get('apartment') or {}
            loc = o.get('location') or {}
            out.append({
                'id': oid, 'price': pr.get('value') or pr.get('valueForWhole'),
                'area': (o.get('area') or {}).get('value'), 'rooms': o.get('roomsTotal'),
                'renRaw': ap.get('renovation'), 'ren': REN.get(ap.get('renovation'), ap.get('renovation')),
                'floor': (o.get('floorsOffered') or [None])[0], 'floors': o.get('floorsTotal'),
                'year': bl.get('builtYear'), 'site': (bl.get('siteName') or (o.get('building') or {}).get('buildingSeries')),
                'addr': loc.get('address'), 'nphoto': len(imgs), 'imgs': imgs[:8],
                'url': 'https://realty.yandex.ru/offer/%s/' % oid,
            })
    return out

if __name__ == '__main__':
    url = sys.argv[1]
    res = offers_from(url)
    res.sort(key=lambda o: -(o['nphoto'] or 0))
    print('total', len(res))
    for o in res[:40]:
        print(f"{o['rooms']}к {o['area']} м²  {(o['price'] or 0)/1e6:7.1f} млн  {o['ren']}  эт.{o['floor']}/{o['floors']}  {o['year']}  {o['site']}  фото:{o['nphoto']}")
        print('    ', o['addr'], o['url'])
    json.dump(res, open(sys.argv[2] if len(sys.argv) > 2 else 'k12_srch.json', 'w'), ensure_ascii=False)
