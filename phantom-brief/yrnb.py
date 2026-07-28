"""List new-building sites (name, developer, price, deadline) from a Yandex Realty page."""
import sys, json; sys.path.insert(0,'.')
import yr

def sites(url):
    st = yr.state(url)
    out = {}
    def walk(o):
        if isinstance(o, dict):
            if 'name' in o and ('buildingClass' in o or 'deliveryDates' in o or 'developers' in o):
                out[o.get('id') or o['name']] = o
            for v in o.values(): walk(v)
        elif isinstance(o, list):
            for v in o: walk(v)
    walk(st)
    return out

if __name__ == '__main__':
    for k, s in sites(sys.argv[1]).items():
        devs = ', '.join(d.get('name','') for d in (s.get('developers') or []))
        pr = (s.get('price') or {})
        dd = (s.get('deliveryDates') or [{}])[-1]
        print(f"{s.get('name','?'):34s} | {s.get('buildingClass','?'):12s} | {devs[:26]:26s} | "
              f"от {(pr.get('from') or 0)/1e6:7.1f} млн | {dd.get('quarter','?')} кв. {dd.get('year','?')} | "
              f"{(s.get('location') or {}).get('address','')[:44]}")
