# -*- coding: utf-8 -*-
import json, html
r = json.load(open('report_enrich.json', encoding='utf-8'))
U = lambda s: html.unescape(str(s)) if s is not None else ''

CATMAP = {'chips':'chips','data':'data','ai':'ai','quantum':'ai','geopolitics':'chips'}

# ---- hero (6) with icons; order for color variety ----
hs = {i:s for i,s in enumerate(r['heroStats'])}
# pick order: 100%, 35.66EB, $674, €615M, €32.7, >110B  -> by matching value keywords
def find(hstats, kw):
    for s in hstats:
        if kw in U(s['value']): return s
    return None
order_kw = ['100%','ЭБ','674','€32,7','615','110']
icons =    ['chip','cloud','spark','euro','atom','globe']
HERO=[]
used=set()
for kw,ic in zip(order_kw, icons):
    s=find(r['heroStats'], kw)
    if s and id(s) not in used:
        used.add(id(s)); HERO.append((U(s['value']), U(s['labelRu']), ic, CATMAP.get(s['cat'],'chips')))
# fallback: append any missed
for s in r['heroStats']:
    if id(s) not in used: HERO.append((U(s['value']), U(s['labelRu']),'spark',CATMAP.get(s['cat'],'chips')))
HERO=HERO[:6]

# ---- did you know (4) ----
dyk_cat=['chips','chips','data','ai']
DYK=[]
for i,d in enumerate(r['didYouKnow'][:4]):
    DYK.append((U(d['titleRu']), U(d['textRu']), d.get('icon','spark'), dyk_cat[i%4]))

# ---- timeline: select 8 ----
keep=['1984','2003','2006','2015','2016','2023','2025','2026']
tl_by_year={U(e['year']):e for e in r['timeline']}
TL=[]
for y in keep:
    e=tl_by_year.get(y)
    if e: TL.append((U(e['year']), U(e['titleRu']), U(e['detailRu']), CATMAP.get(e['cat'],'chips')))

# ---- supply chain (4) ----
SC=[(U(s['stageRu']), U(s['playersRu']), U(s['dutchRu']), s.get('icon','spark')) for s in r['supplyChain'][:4]]

# ---- geopolitics: lead + 4 points ----
gpts=r['geopolitics']['points']
def gp_pick(kw):
    for p in gpts:
        if kw in U(p['titleRu']): return p
    return None
gp_sel=[gp_pick('EUV для Китая') or gpts[0], gp_pick('бизнеса под ударом') or gp_pick('Треть'), gp_pick('сырьём'), gp_pick('Nexperia')]
gp_sel=[p for p in gp_sel if p][:4]
GEO={'lead':U(r['geopolitics']['leadRu']),
     'points':[(U(p['titleRu']),U(p['textRu']),p.get('icon','globe')) for p in gp_sel]}

# ---- outlook: lead + 3 points ----
opts=r['outlook']['points']
def op_pick(kw):
    for p in opts:
        if kw in U(p['titleRu']): return p
    return None
ol_sel=[op_pick('High-NA') or opts[0], op_pick('кванты') or op_pick('Кванты'), op_pick('электросет') or op_pick('горлышко')]
ol_sel=[p for p in ol_sel if p][:3]
OL={'lead':U(r['outlook']['leadRu']),
    'points':[(U(p['titleRu']),U(p['textRu']),p.get('icon','chip')) for p in ol_sel]}

# ---- zone nuggets (short one-liners, curated) ----
NUG={
 'asml':'Один EUV-литограф — это ~100 000 деталей от ~5 000 поставщиков в 60 странах.',
 'htc':'Эйндховен — самый изобретательный город планеты: 22,6 патента на 10 000 жителей.',
 'nijmegen':'Одна только Nexperia отгружает отсюда более 110 млрд изделий в год.',
 'amsterdam':'Через узел AMS-IX здесь в 2025 году прошло 35,66 эксабайта данных.',
 'eemshaven':'Google охлаждает ЦОД не питьевой, а переработанной канальной водой (28-км труба).',
 'agriport':'Тепло и CO₂ дата-центров идут в крупнейший тепличный кластер Европы.',
 'delft':'В 2024 году QuTech впервые связал квантовой запутанностью процессоры в двух городах.',
}

SRC=[U(s) for s in r.get('sources',[])][:24]

# ---- emit ----
def pyrepr(x): return repr(x)
out=['# -*- coding: utf-8 -*-','"""Enrichment content (verified via research workflow)."""','']
out.append('HERO_STATS = '+pyrepr(HERO))
out.append('DID_YOU_KNOW = '+pyrepr(DYK))
out.append('TIMELINE = '+pyrepr(TL))
out.append('SUPPLY_CHAIN = '+pyrepr(SC))
out.append('GEOPOLITICS = '+pyrepr(GEO))
out.append('OUTLOOK = '+pyrepr(OL))
out.append('ZONE_NUGGETS = '+pyrepr(NUG))
out.append('SOURCES = '+pyrepr(SRC))
open('deck_extra.py','w',encoding='utf-8').write('\n'.join(out)+'\n')
print('wrote deck_extra.py')
print('HERO:',len(HERO),'DYK:',len(DYK),'TL:',len(TL),'SC:',len(SC),'GEO pts:',len(GEO['points']),'OL pts:',len(OL['points']))
