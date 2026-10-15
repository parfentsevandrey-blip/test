import json, urllib.parse
from pathlib import Path
from playwright.sync_api import sync_playwright
HERE=Path(__file__).resolve().parent
model=json.loads((HERE/'report_model.json').read_text())
with sync_playwright() as p:
    b=p.chromium.launch(channel="chrome", headless=False, args=["--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader"])
    pg=b.new_page(viewport={"width":1200,"height":760}, locale="ru-RU", device_scale_factor=2)
    for i,(t,v) in enumerate(model['targets'].items(), 1):
        data={"self":{"name":t,"lat":v['self']['lat'],"lon":v['self']['lon']},
              "comps":[{"lat":c['lat'],"lon":c['lon']} for c in v['competitors']]}
        pg.goto(f"http://127.0.0.1:8811/map.html?n={i}#"+urllib.parse.quote(json.dumps(data)))
        pg.wait_for_function("window.READY===true", timeout=45000)
        out=HERE/'maps'/f'map_{i}.png'; pg.screenshot(path=str(out)); print(t, out.name)
    b.close()
