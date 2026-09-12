import math, sys, csv, json, subprocess, os, time
from collections import defaultdict
from cities import CITIES

R_KM = 10.0                    # радиус аналитического диска
CORE_KM = 2.0                  # радиус "исторического ядра"
CELL_M = 250.0                 # ячейка сетки для оценки освоенной территории
FLOOR_H = 3.2                  # м на этаж
BINS = [300.0, 1000.0, 10000.0]  # границы категорий, м²

def tile_xy(lat, lon, z):
    n = 2**z
    x = int((lon+180.0)/360.0*n)
    lr = math.radians(max(-85.05, min(85.05, lat)))
    y = int((1-math.log(math.tan(lr)+1/math.cos(lr))/math.pi)/2*n)
    return x, y

def qkey(x, y, z=9):
    s = ""
    for i in range(z, 0, -1):
        d = 0; m = 1 << (i-1)
        if x & m: d += 1
        if y & m: d += 2
        s += str(d)
    return s

def load_index():
    idx = defaultdict(list)
    with open("ms-links.csv") as f:
        for r in csv.DictReader(f):
            idx[r["QuadKey"]].append((r["Url"], r["Size"], r["Location"]))
    return idx

def tiles_for(lat, lon, r_km):
    dlat = r_km/110.574
    dlon = r_km/(111.320*math.cos(math.radians(lat)))
    ks = set()
    for la in (lat-dlat, lat, lat+dlat):
        for lo in (lon-dlon, lon, lon+dlon):
            ks.add(qkey(*tile_xy(la, lo, 9)))
    return sorted(ks)

def gini(vals):
    if not vals: return None
    v = sorted(vals); n = len(v); s = sum(v)
    if s == 0: return None
    cum = 0.0
    for i, x in enumerate(v, 1): cum += i*x
    return (2*cum)/(n*s) - (n+1)/n

def process_city(city, idx):
    name, en, country, cont, lat, lon = city
    mlat = 110574.0
    mlon = 111320.0*math.cos(math.radians(lat))
    dlat = R_KM*1000/mlat; dlon = R_KM*1000/mlon
    lat0, lat1 = lat-dlat, lat+dlat
    lon0, lon1 = lon-dlon, lon+dlon
    R2 = (R_KM*1000)**2; C2 = (CORE_KM*1000)**2

    areas=[]; core_areas=[]; heights=[]; hpairs=[]; cells=set(); core_cells=set()
    bytes_seen = 0; tiles_used = []

    for k in tiles_for(lat, lon, R_KM):
        for url, size, loc in idx.get(k, []):
            tiles_used.append(f"{loc}/{k} ({size})")
            p = subprocess.Popen(f'curl -sS --max-time 900 --retry 3 "{url}" | gzip -dc',
                                 shell=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                 bufsize=1<<20, text=True)
            for line in p.stdout:
                bytes_seen += len(line)
                i = line.find('[[[')
                if i < 0: continue
                j = line.find(']', i+3)
                try:
                    a, b = line[i+3:j].split(',')
                    plon = float(a); plat = float(b)
                except Exception:
                    continue
                if not (lat0 <= plat <= lat1 and lon0 <= plon <= lon1): continue
                try: feat = json.loads(line)
                except Exception: continue
                ring = feat["geometry"]["coordinates"][0]
                # локальная равновеликая проекция относительно центра города
                xs = [(c[0]-lon)*mlon for c in ring]
                ys = [(c[1]-lat)*mlat for c in ring]
                s = 0.0
                for t in range(len(xs)-1):
                    s += xs[t]*ys[t+1] - xs[t+1]*ys[t]
                area = abs(s)/2.0
                if area < 4.0 or area > 3_000_000: continue
                cx = sum(xs[:-1])/max(1, len(xs)-1); cy = sum(ys[:-1])/max(1, len(ys)-1)
                d2 = cx*cx + cy*cy
                if d2 > R2: continue
                h = feat["properties"].get("height", -1.0)
                areas.append(area)
                cells.add((int(cx//CELL_M), int(cy//CELL_M)))
                if h and h > 0.5:
                    heights.append(h); hpairs.append((area, h))
                if d2 <= C2:
                    core_areas.append(area)
                    core_cells.add((int(cx//CELL_M), int(cy//CELL_M)))
            p.stdout.close(); p.wait()

    if not areas:
        return {"city": name, "en": en, "country": country, "continent": cont,
                "lat": lat, "lon": lon, "n": 0, "tiles": tiles_used}

    n = len(areas)
    bin_names = ["<300", "300-1000", "1000-10000", ">=10000"]
    bc = [0,0,0,0]; ba = [0.0,0.0,0.0,0.0]
    for a in areas:
        if a < BINS[0]: k=0
        elif a < BINS[1]: k=1
        elif a < BINS[2]: k=2
        else: k=3
        bc[k]+=1; ba[k]+=a
    cbc=[0,0,0,0]
    for a in core_areas:
        k = 0 if a<BINS[0] else 1 if a<BINS[1] else 2 if a<BINS[2] else 3
        cbc[k]+=1

    sa = sorted(areas)
    def q(p): return sa[min(n-1, int(p*n))]
    tot = sum(sa)
    top1 = sum(sa[int(0.99*n):]) / tot if tot else 0

    # оценка надземной площади (GFA) по этажности из высот
    floors_by_bin = {}
    for a, h in hpairs:
        k = 0 if a<BINS[0] else 1 if a<BINS[1] else 2 if a<BINS[2] else 3
        floors_by_bin.setdefault(k, []).append(max(1.0, h/FLOOR_H))
    mean_fl = {k: sum(v)/len(v) for k, v in floors_by_bin.items()}
    gfa_bins=[0.0]*4; gfa_cnt=[0,0,0,0]
    for a in areas:
        k = 0 if a<BINS[0] else 1 if a<BINS[1] else 2 if a<BINS[2] else 3
        f = mean_fl.get(k, 1.0)
        g = a*f
        gfa_bins[k]+=g
        kk = 0 if g<BINS[0] else 1 if g<BINS[1] else 2 if g<BINS[2] else 3
        gfa_cnt[kk]+=1

    hs = sorted(heights)
    builtup_km2 = len(cells)*(CELL_M/1000)**2
    core_builtup = len(core_cells)*(CELL_M/1000)**2

    return {
      "city": name, "en": en, "country": country, "continent": cont, "lat": lat, "lon": lon,
      "n": n, "n_core": len(core_areas),
      "bin_names": bin_names, "bin_counts": bc, "bin_area": ba, "core_bin_counts": cbc,
      "gfa_bin_area": gfa_bins, "gfa_bin_counts": gfa_cnt,
      "mean_floors_by_bin": {str(k): round(v,2) for k,v in mean_fl.items()},
      "total_footprint_m2": tot,
      "median_m2": round(q(0.5),1), "mean_m2": round(tot/n,1),
      "p10_m2": round(q(0.10),1), "p90_m2": round(q(0.90),1),
      "p95_m2": round(q(0.95),1), "p99_m2": round(q(0.99),1), "max_m2": round(sa[-1],1),
      "gini": round(gini(sa),4), "top1pct_area_share": round(top1,4),
      "builtup_km2": round(builtup_km2,2), "core_builtup_km2": round(core_builtup,2),
      "disc_km2": round(math.pi*R_KM**2,1),
      "density_per_builtup_km2": round(n/builtup_km2,1) if builtup_km2 else None,
      "coverage_ratio": round(tot/(builtup_km2*1e6),4) if builtup_km2 else None,
      "core_density_per_km2": round(len(core_areas)/core_builtup,1) if core_builtup else None,
      "h_known_share": round(len(hs)/n,4),
      "h_mean": round(sum(hs)/len(hs),2) if hs else None,
      "h_median": round(hs[len(hs)//2],2) if hs else None,
      "h_p99": round(hs[int(0.99*len(hs))],2) if hs else None,
      "h_max": round(hs[-1],2) if hs else None,
      "share_h_ge12": round(sum(1 for x in hs if x>=12)/len(hs),4) if hs else None,
      "share_h_ge24": round(sum(1 for x in hs if x>=24)/len(hs),4) if hs else None,
      "share_h_ge50": round(sum(1 for x in hs if x>=50)/len(hs),4) if hs else None,
      "share_h_ge100": round(sum(1 for x in hs if x>=100)/len(hs),4) if hs else None,
      "tiles": tiles_used,
    }

if __name__ == "__main__":
    idx = load_index()
    only = sys.argv[1:] if len(sys.argv) > 1 else None
    out_path = "results.jsonl"
    done = set()
    if os.path.exists(out_path):
        for l in open(out_path):
            try: done.add(json.loads(l)["city"])
            except Exception: pass
    with open(out_path, "a") as out:
        for c in CITIES:
            if only and c[0] not in only and c[1] not in only: continue
            if c[0] in done:
                print(f"skip {c[0]}", flush=True); continue
            t0 = time.time()
            try:
                r = process_city(c, idx)
            except Exception as e:
                print(f"ERR {c[0]}: {e}", flush=True); continue
            out.write(json.dumps(r, ensure_ascii=False)+"\n"); out.flush()
            print(f"{c[0]:<16} n={r['n']:>8}  {time.time()-t0:6.1f}s  bins={r.get('bin_counts')}", flush=True)
