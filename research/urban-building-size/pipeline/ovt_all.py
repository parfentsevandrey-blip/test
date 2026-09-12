import duckdb, os, math, json, sys, time
from cities import CITIES
REL='s3://overturemaps-us-west-2/release/2026-08-19.0/theme=buildings/type=building/*.parquet'
R_KM=10.0; CORE_KM=2.0; CELL_M=250.0; FLOOR_H=3.2; BINS=[300.0,1000.0,10000.0]
TARGETS={c[0]:(c[1],c[2],c[3],c[4],c[5]) for c in CITIES}
def conn():
    c=duckdb.connect(); c.execute("INSTALL httpfs; LOAD httpfs; INSTALL spatial; LOAD spatial;")
    p=os.environ.get("HTTPS_PROXY","").replace("http://","")
    if p: c.execute(f"SET http_proxy='{p}'")
    c.execute("SET ca_cert_file='/root/.ccr/ca-bundle.crt'")
    c.execute("SET s3_region='us-west-2'; SET s3_use_ssl=true;")
    try: c.execute("CREATE SECRET ovt (TYPE s3, PROVIDER config, KEY_ID '', SECRET '', REGION 'us-west-2');")
    except Exception: pass
    return c
def gini(v):
    if not v: return None
    v=sorted(v); n=len(v); s=sum(v)
    if not s: return None
    cum=sum(i*x for i,x in enumerate(v,1))
    return (2*cum)/(n*s)-(n+1)/n
def run(c,name,meta):
    en,country,cont,lat,lon=meta
    mlat=110574.0; mlon=111320.0*math.cos(math.radians(lat))
    dlat=R_KM*1000/mlat; dlon=R_KM*1000/mlon
    q=f"""SELECT ST_Area(geometry)*{mlon*mlat} AS a,
                 (ST_X(ST_Centroid(geometry))- ({lon}))*{mlon} AS cx,
                 (ST_Y(ST_Centroid(geometry))- ({lat}))*{mlat} AS cy,
                 height, num_floors
          FROM read_parquet('{REL}')
          WHERE bbox.xmin BETWEEN {lon-dlon} AND {lon+dlon}
            AND bbox.ymin BETWEEN {lat-dlat} AND {lat+dlat}"""
    rows=c.execute(q).fetchall()
    R2=(R_KM*1000)**2; C2=(CORE_KM*1000)**2
    areas=[];core=[];hs=[];hp=[];cells=set();ccells=set()
    for a,cx,cy,h,nf in rows:
        if a is None or a<4 or a>3_000_000: continue
        d2=cx*cx+cy*cy
        if d2>R2: continue
        hh = h if (h and h>0.5) else (nf*FLOOR_H if nf else None)
        areas.append(a); cells.add((int(cx//CELL_M),int(cy//CELL_M)))
        if hh: hs.append(hh); hp.append((a,hh))
        if d2<=C2: core.append(a); ccells.add((int(cx//CELL_M),int(cy//CELL_M)))
    if not areas: return {"city":name,"n":0}
    n=len(areas); bc=[0]*4; ba=[0.0]*4
    def bi(x): return 0 if x<BINS[0] else 1 if x<BINS[1] else 2 if x<BINS[2] else 3
    for a in areas: k=bi(a); bc[k]+=1; ba[k]+=a
    cbc=[0]*4
    for a in core: cbc[bi(a)]+=1
    fb={}
    for a,h in hp: fb.setdefault(bi(a),[]).append(max(1.0,h/FLOOR_H))
    mf={k:sum(v)/len(v) for k,v in fb.items()}
    gfa=[0.0]*4; gc=[0]*4
    for a in areas:
        k=bi(a); g=a*mf.get(k,1.0); gfa[k]+=g; gc[bi(g)]+=1
    sa=sorted(areas); tot=sum(sa)
    def qq(p): return sa[min(n-1,int(p*n))]
    hs.sort()
    bkm=len(cells)*(CELL_M/1000)**2; ckm=len(ccells)*(CELL_M/1000)**2
    return {"city":name,"en":en,"country":country,"continent":cont,"lat":lat,"lon":lon,
      "n":n,"n_core":len(core),"bin_names":["<300","300-1000","1000-10000",">=10000"],
      "bin_counts":bc,"bin_area":ba,"core_bin_counts":cbc,"gfa_bin_area":gfa,"gfa_bin_counts":gc,
      "mean_floors_by_bin":{str(k):round(v,2) for k,v in mf.items()},
      "total_footprint_m2":tot,"median_m2":round(qq(.5),1),"mean_m2":round(tot/n,1),
      "p10_m2":round(qq(.1),1),"p90_m2":round(qq(.9),1),"p95_m2":round(qq(.95),1),
      "p99_m2":round(qq(.99),1),"max_m2":round(sa[-1],1),"gini":round(gini(sa),4),
      "top1pct_area_share":round(sum(sa[int(.99*n):])/tot,4),
      "builtup_km2":round(bkm,2),"core_builtup_km2":round(ckm,2),"disc_km2":round(math.pi*R_KM**2,1),
      "density_per_builtup_km2":round(n/bkm,1) if bkm else None,
      "coverage_ratio":round(tot/(bkm*1e6),4) if bkm else None,
      "core_density_per_km2":round(len(core)/ckm,1) if ckm else None,
      "h_known_share":round(len(hs)/n,4),"h_mean":round(sum(hs)/len(hs),2) if hs else None,
      "h_median":round(hs[len(hs)//2],2) if hs else None,
      "h_p99":round(hs[int(.99*len(hs))],2) if hs else None,"h_max":round(hs[-1],2) if hs else None,
      "share_h_ge12":round(sum(1 for x in hs if x>=12)/len(hs),4) if hs else None,
      "share_h_ge24":round(sum(1 for x in hs if x>=24)/len(hs),4) if hs else None,
      "share_h_ge50":round(sum(1 for x in hs if x>=50)/len(hs),4) if hs else None,
      "share_h_ge100":round(sum(1 for x in hs if x>=100)/len(hs),4) if hs else None,
      "source":"Overture Maps 2026-08-19.0"}
if __name__=="__main__":
    c=conn(); done=set()
    if os.path.exists("results_ovt_all.jsonl"):
        done={json.loads(l)["city"] for l in open("results_ovt_all.jsonl")}
    with open("results_ovt_all.jsonl","a") as f:
        for name,meta in TARGETS.items():
            if name in done: print("skip",name,flush=True); continue
            t=time.time()
            try: r=run(c,name,meta)
            except Exception as e: print("ERR",name,str(e)[:300],flush=True); continue
            f.write(json.dumps(r,ensure_ascii=False)+"\n"); f.flush()
            print(f"{name:<18} n={r['n']:>8} {time.time()-t:6.1f}s bins={r.get('bin_counts')}",flush=True)
