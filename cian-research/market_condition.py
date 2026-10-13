"""Итоговая оценка состояния квартиры.

Признаки по объявлению:
- CLIP: усреднённый эмбеддинг интерьерных фото и zero-shot вероятности классов (кэш в таблице clip_feat);
- VLM-осмотр (market_vlm.py): состояние, возраст ремонта, объём работ из анкеты (one-hot).
Классификатор (логистическая регрессия) учится на ремонте, указанном продавцами; точность — кросс-валидация.
Если анкеты VLM нет, используется модель только на CLIP.
"""
import json
import os

CLASSES = ['без отделки', 'white box', 'требует ремонта', 'косметический', 'современный', 'дизайнерский']
PROMPTS = {
    'без отделки': ['an unfinished apartment with bare grey concrete walls and concrete floor',
                    'a raw empty flat in a new building without any finishing'],
    'white box': ['an empty apartment with smooth white plastered walls, screed floor and no furniture',
                  'a pre-finished white box apartment ready for final decoration'],
    'требует ремонта': ['an old shabby apartment that needs renovation, worn wallpaper and old soviet furniture',
                        'a dilapidated flat with damaged walls, old linoleum and outdated bathroom'],
    'косметический': ['an ordinary lived-in apartment with simple old-fashioned renovation and wallpaper',
                      'a modest apartment with cheap basic renovation, laminate and plain furniture'],
    'современный': ['a modern freshly renovated apartment with new finishing, white walls and laminate floor',
                    'a clean contemporary apartment with new kitchen and modern bathroom tiles'],
    'дизайнерский': ['a luxury apartment with an expensive designer interior and premium finishing',
                     'a high-end stylish apartment with designer lighting, marble and custom furniture'],
}
INTERIOR = ['a photo of a room interior in an apartment', 'a photo of a kitchen in an apartment',
            'a photo of a bathroom', 'a photo of an empty room in an apartment']
OTHER = ['a floor plan drawing of an apartment', 'a photo of a residential building exterior',
         'a view from the window of a city', 'a photo of a building entrance or courtyard', 'a map']
SELLER_MAP = {'no': 'требует ремонта', 'cosmetic': 'косметический', 'euro': 'современный', 'design': 'дизайнерский',
              'without': 'без отделки', 'preFine': 'white box', 'fine': 'современный', 'fineWithFurniture': 'современный'}
MODEL = os.environ.get('CLIP_MODEL', 'openai/clip-vit-base-patch16')
READY = {'современный', 'дизайнерский'}
VLM_COND = CLASSES
VLM_AGE = ['новый', '3–10 лет', 'старше 10 лет', 'советский', 'нет ремонта']
VLM_WORKS = ['не нужны', 'косметика', 'капитальный ремонт', 'ремонт с нуля']


def seller_label(row):
    return SELLER_MAP.get(row['repair'] or '') or SELLER_MAP.get(row['decoration'] or '')


def clip_features(con):
    """Считает CLIP-признаки для объявлений, у которых их ещё нет и есть фото на диске."""
    import torch
    from PIL import Image
    from transformers import CLIPModel, CLIPProcessor
    con.execute('create table if not exists clip_feat(cian_id integer primary key, emb text, zs text, n_interior integer, model text)')
    have = {r[0] for r in con.execute('select cian_id from clip_feat')}
    photos = {}
    for p in con.execute('select cian_id, path from photo where path is not null order by cian_id, idx'):
        if p[0] not in have and os.path.exists(p[1]):
            photos.setdefault(p[0], []).append(p[1])
    if not photos:
        return
    torch.set_num_threads(os.cpu_count() or 4)
    model, proc = CLIPModel.from_pretrained(MODEL).eval(), CLIPProcessor.from_pretrained(MODEL)
    norm = lambda t: torch.nn.functional.normalize(t, dim=-1)
    with torch.no_grad():
        tf = lambda texts: norm(model.get_text_features(**proc(text=texts, return_tensors='pt', padding=True)))
        cls_t = norm(torch.stack([tf(PROMPTS[c]).mean(0) for c in CLASSES]))
        kind_t = tf(INTERIOR + OTHER)
        scale = model.logit_scale.exp().item()
        for n, (cid, paths) in enumerate(photos.items(), 1):
            ims = []
            for pth in paths:
                try:
                    ims.append(Image.open(pth).convert('RGB'))
                except Exception:
                    pass
            if not ims:
                continue
            e = norm(model.get_image_features(**proc(images=ims, return_tensors='pt')))
            inner = e[(e @ kind_t.T).argmax(-1) < len(INTERIOR)]
            if len(inner) == 0:
                con.execute('insert or replace into clip_feat values(?,?,?,?,?)', (cid, None, None, 0, MODEL))
                continue
            emb = norm(inner.mean(0))
            zs = (scale * inner @ cls_t.T).softmax(-1).mean(0)
            con.execute('insert or replace into clip_feat values(?,?,?,?,?)',
                        (cid, json.dumps([round(float(x), 5) for x in emb]), json.dumps([float(x) for x in zs]), len(inner), MODEL))
            if n % 50 == 0:
                con.commit()
                print(f'  CLIP-признаки: {n}/{len(photos)}', flush=True)
    con.commit()


def cmd_condition(a):
    import numpy as np
    from collections import Counter
    from sklearn.linear_model import LogisticRegression
    from sklearn.model_selection import StratifiedKFold, cross_val_predict
    from market import db_open
    con = db_open(a.db)
    clip_features(con)
    rows = {r['cian_id']: r for r in con.execute('select * from listing')}
    feat = {r[0]: (np.array(json.loads(r[1])), np.array(json.loads(r[2])), r[3])
            for r in con.execute('select cian_id, emb, zs, n_interior from clip_feat where emb is not null')}
    insp = {}
    if con.execute("select name from sqlite_master where name='inspect'").fetchone():
        insp = {r[0]: r[1:] for r in con.execute('select cian_id, condition, age, works from inspect where data is not null')}

    def vlm_vec(cid):
        c, ag, w = insp.get(cid, (None, None, None))
        return np.array([c == x for x in VLM_COND] + [ag == x for x in VLM_AGE] + [w == x for x in VLM_WORKS] + [cid in insp], float)

    ids = list(feat)
    X_clip = np.stack([np.hstack([feat[i][0], feat[i][1]]) for i in ids])
    X_all = np.hstack([X_clip, np.stack([vlm_vec(i) for i in ids]) * 3.0])   # вес анкеты VLM
    y = [seller_label(rows[i]) for i in ids]
    counts = Counter(v for v in y if v)
    lab = [k for k, v in enumerate(y) if v and counts[v] >= 5]
    yl = [y[k] for k in lab]
    cv = StratifiedKFold(5, shuffle=True, random_state=0)
    report = {}

    def evaluate(name, X, subset):
        sub = [k for k in lab if k in subset]
        if len(sub) < 30:
            return None
        ys = [y[k] for k in sub]
        pred = cross_val_predict(LogisticRegression(max_iter=3000, C=2.0, class_weight='balanced'), X[sub], ys, cv=cv)
        acc = float(np.mean([p == t for p, t in zip(pred, ys)]))
        bacc = float(np.mean([(p in READY) == (t in READY) for p, t in zip(pred, ys)]))
        report[name] = dict(n=len(sub), acc=round(acc, 3), binary=round(bacc, 3))
        print(f'  {name}: {100 * acc:.0f}% по классам, {100 * bacc:.0f}% «под ремонт / с ремонтом» (n={len(sub)})', flush=True)
        return acc

    inspected = {k for k, i in enumerate(ids) if i in insp}
    print('кросс-валидация на разметке продавцов:')
    evaluate('CLIP, все объявления', X_clip, set(range(len(ids))))
    a_clip = evaluate('CLIP, объявления с осмотром', X_clip, inspected)
    a_all = evaluate('CLIP + осмотр VLM', X_all, inspected)
    vlm_direct = [y[k] == insp[ids[k]][0] for k in lab if k in inspected and insp[ids[k]][0]]
    if vlm_direct:
        report['VLM напрямую'] = dict(n=len(vlm_direct), acc=round(float(np.mean(vlm_direct)), 3))
        print(f'  VLM напрямую (без обучения): {100 * np.mean(vlm_direct):.0f}% (n={len(vlm_direct)})')
    use_vlm = a_all is not None and a_clip is not None and a_all >= a_clip
    clf_clip = LogisticRegression(max_iter=3000, C=2.0, class_weight='balanced').fit(X_clip[lab], yl)
    clf_all = LogisticRegression(max_iter=3000, C=2.0, class_weight='balanced').fit(X_all[lab], yl) if use_vlm else None
    con.execute('delete from cond')
    for k, cid in enumerate(ids):
        if clf_all is not None and cid in insp:
            pr, cls, mdl = clf_all.predict_proba(X_all[k:k + 1])[0], clf_all.classes_, f'clip+vlm acc={a_all:.2f}'
        else:
            pr, cls, mdl = clf_clip.predict_proba(X_clip[k:k + 1])[0], clf_clip.classes_, \
                f'clip acc={report.get("CLIP, все объявления", {}).get("acc", 0):.2f}'
        probs = {c: float(p) for c, p in zip(cls, pr)}
        # классы, которых нет в разметке продавцов вторички (без отделки, white box), берём из осмотра VLM
        vc = insp.get(cid, (None,))[0]
        if vc in ('без отделки', 'white box'):
            best, conf = vc, 0.6
        else:
            best = max(probs, key=probs.get)
            conf = probs[best]
        con.execute('insert into cond values(?,?,?,?,?,?)', (cid, best, conf, json.dumps(probs, ensure_ascii=False),
                                                          feat[cid][2], mdl))
    con.execute('create table if not exists meta(key text primary key, value text)')
    con.execute('insert or replace into meta values(?,?)', ('condition_report', json.dumps(report, ensure_ascii=False)))
    con.commit()
    print('итоговая модель:', 'CLIP + осмотр VLM' if use_vlm else 'CLIP', '| распределение:',
          dict(Counter(r[0] for r in con.execute('select label from cond'))))


def add_parser(sub):
    c = sub.add_parser('condition', help='итоговая оценка состояния (CLIP + осмотр VLM)')
    c.set_defaults(fn=cmd_condition)
