import os, re, base64, io, json, sys
from PIL import Image, ImageOps

SPEC = {
 # name: (w, h, quality)   h=None -> keep aspect at width
 "utrecht-umbrella": (900,1180,68), "denhaag-gms": (900,1180,68), "beurstraverse": (900,1180,68),
 "utrecht-street": (900,600,70), "vacant-window": (900,600,70),
 "blokker-breda": (760,507,70), "wibra-dev": (760,507,70),
 "ha-zwolle": (760,507,70), "jf-store": (620,668,70), "polette-utr": (560,747,70), "ha-oogmeting": (620,466,72),
 "medimarket": (860,645,70), "medimarket-2": (700,525,70), "rituals-gouda": (700,394,70),
 "normal-gron": (820,461,70), "sostrene": (700,525,70),
 "jysk-rdam": (860,484,70), "dk-breda": (520,693,70),
 "annemax-utrecht": (860,573,70), "teds-beukenplein": (700,467,70), "bagels-nl": (620,465,70),
 "poke-rdam": (820,461,70), "soju-ams": (600,403,74), "cotti-shinkong": (620,465,70),
 "bubble-ams": (620,349,70), "pp-spread": (620,414,70),
 "tpl-store": (860,573,70), "vintage-delft": (660,371,70), "kringloop": (660,440,70),
 "denhaag-spui": (1180,787,70),
 "nailsalon": (700,467,70), "tattoo-street": (620,413,70),
 "tattoo-belfast": (1000,667,72),
 "vacant-shop": (760,505,70),
 "eyesmore-ulm": (420,420,70), "teds-hero": (420,315,70), "pp-shoot": (360,540,70),
 "annemax-coffee": (420,280,70), "cotti-xiamen": (420,315,70), "soju-2": (420,282,74),
 "jf-hero": (860,573,70), "bagels-zwolle": (420,315,70), "drogist": (620,414,70),
 "wibra-2024": (420,280,70), "kfc-korean": (420,280,70), "repair-nl": (420,236,70),
 "hansanders-shop": (420,315,72), "teds-interior": (420,280,70), "sostrene2": (420,315,70),
}
SRC = {}
for f in os.listdir("assets"):
    SRC[f.rsplit(".",1)[0]] = "assets/"+f

def make(key):
    name, spec = key, None
    if "@" in key:
        name, dim = key.split("@",1)
        parts = dim.split("x")
        spec = (int(parts[0]), int(parts[1]), int(parts[2]) if len(parts)>2 else 70)
    if name not in SRC: return None
    w,h,q = spec or SPEC.get(name, (760,None,70))
    im = Image.open(SRC[name])
    im = ImageOps.exif_transpose(im).convert("RGB")
    if h:
        im = ImageOps.fit(im, (w,h), Image.LANCZOS, centering=(0.5,0.42))
    else:
        im.thumbnail((w, 10000), Image.LANCZOS)
    buf = io.BytesIO(); im.save(buf, "WEBP", quality=q, method=6)
    return "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode()

def build(tpl="deck.template.html", out="deck.html", localfonts=False):
    src = open(tpl, encoding="utf-8").read()
    if localfonts:
        src = re.sub(r'<link rel="preconnect"[^>]*>\s*', '', src)
        src = re.sub(r'<link rel="stylesheet" href="https://fonts\.googleapis[^>]*>',
                     '<link rel="stylesheet" href="fonts/local.css">', src)
    used, missing, total = {}, set(), 0
    def sub(m):
        nonlocal total
        n = m.group(1)
        if n not in used:
            d = make(n)
            if d is None: missing.add(n); used[n] = ""
            else: used[n] = d
        return used[n]
    out_html = re.sub(r"\{\{IMG:([a-zA-Z0-9_@x-]+)\}\}", sub, src)
    open(out,"w",encoding="utf-8").write(out_html)
    size = os.path.getsize(out)
    print(f"built {out}: {size/1024/1024:.2f} MB  | images {len([u for u in used.values() if u])}"
          f" | missing {sorted(missing) if missing else 'none'}")
    for n,d in sorted(used.items(), key=lambda kv:-len(kv[1]))[:6]:
        print(f"   {n:20s} {len(d)/1024:7.0f} KB")
    return size

if __name__ == "__main__":
    if len(sys.argv)>1 and sys.argv[1]=="probe":
        tot=0
        for n in SPEC:
            d=make(n)
            if d: tot+=len(d); print(f"{n:20s} {len(d)/1024:7.1f} KB")
            else: print(f"{n:20s} MISSING")
        print(f"TOTAL {tot/1024/1024:.2f} MB")
    elif len(sys.argv)>1 and sys.argv[1]=="local":
        build(out="deck.local.html", localfonts=True)
    else:
        build()
