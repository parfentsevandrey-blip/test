"""Оценка состояния квартиры по фото: CLIP (zero-shot) + классификатор, обученный на ремонте, указанном продавцом."""
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
PHOTO_TYPES = {
    'interior': ['a photo of a room interior in an apartment', 'a photo of a kitchen in an apartment',
                 'a photo of a bathroom', 'a photo of an empty room in an apartment'],
    'plan': ['a floor plan drawing of an apartment'],
    'outside': ['a photo of a residential building exterior', 'a view from the window of a city',
                'a photo of a building entrance or courtyard', 'a map'],
}
# ремонт, указанный продавцом (карточка/выдача ЦИАН) → наш класс
SELLER_MAP = {'no': 'требует ремонта', 'cosmetic': 'косметический', 'euro': 'современный', 'design': 'дизайнерский',
              'without': 'без отделки', 'preFine': 'white box', 'fine': 'современный',
              'fineWithFurniture': 'современный'}
MODEL = 'openai/clip-vit-base-patch16'


def seller_label(row):
    return SELLER_MAP.get(row['repair'] or '') or SELLER_MAP.get(row['decoration'] or '')


def cmd_condition(a):
    import numpy as np
    import torch
    from PIL import Image
    from transformers import CLIPModel, CLIPProcessor
    from market import db_open
    torch.set_num_threads(os.cpu_count() or 4)
    con = db_open(a.db)
    model, proc = CLIPModel.from_pretrained(MODEL).eval(), CLIPProcessor.from_pretrained(MODEL)

    def text_emb(texts):
        with torch.no_grad():
            t = model.get_text_features(**proc(text=texts, return_tensors='pt', padding=True))
        return torch.nn.functional.normalize(t, dim=-1)

    cls_t = torch.stack([text_emb(PROMPTS[c]).mean(0) for c in CLASSES])
    cls_t = torch.nn.functional.normalize(cls_t, dim=-1)
    type_names = list(PHOTO_TYPES)
    type_t = torch.nn.functional.normalize(torch.stack([text_emb(PHOTO_TYPES[k]).mean(0) for k in type_names]), dim=-1)
    scale = model.logit_scale.exp().item()

    rows = {r['cian_id']: r for r in con.execute('select * from listing')}
    photos = {}
    for p in con.execute('select cian_id, idx, path from photo where path is not null order by cian_id, idx'):
        if p['path'] and os.path.exists(p['path']):
            photos.setdefault(p['cian_id'], []).append(p['path'])
    print(f'объявлений с фото: {len(photos)}', flush=True)
    feats, zs, ninterior = {}, {}, {}
    for n, (cid, paths) in enumerate(photos.items(), 1):
        ims = []
        for pth in paths:
            try:
                ims.append(Image.open(pth).convert('RGB'))
            except Exception:
                pass
        if not ims:
            continue
        with torch.no_grad():
            e = torch.nn.functional.normalize(model.get_image_features(**proc(images=ims, return_tensors='pt')), dim=-1)
        kind = (scale * e @ type_t.T).softmax(-1).argmax(-1).tolist()
        inner = e[[i for i, k in enumerate(kind) if type_names[k] == 'interior']]
        if len(inner) == 0:
            continue
        ninterior[cid] = len(inner)
        feats[cid] = torch.nn.functional.normalize(inner.mean(0), dim=-1).numpy()
        zs[cid] = (scale * inner @ cls_t.T).softmax(-1).mean(0).numpy()
        if n % 50 == 0:
            print(f'  {n}/{len(photos)}', flush=True)
    ids = list(feats)
    X = np.stack([feats[i] for i in ids])
    Z = np.stack([zs[i] for i in ids])
    y = [seller_label(rows[i]) for i in ids]
    lab = [i for i, v in enumerate(y) if v]
    zs_acc = np.mean([CLASSES[Z[i].argmax()] == y[i] for i in lab]) if lab else float('nan')
    print(f'zero-shot совпадает с продавцом: {100 * zs_acc:.0f}% (n={len(lab)})', flush=True)
    use_sup, cv_acc = False, float('nan')
    from collections import Counter
    counts = Counter(y[i] for i in lab)
    if len(lab) >= 40 and len(counts) >= 3:
        from sklearn.linear_model import LogisticRegression
        from sklearn.model_selection import StratifiedKFold, cross_val_predict
        keep = [i for i in lab if counts[y[i]] >= 5]
        Xl, yl = np.hstack([X[keep], Z[keep]]), [y[i] for i in keep]
        clf = LogisticRegression(max_iter=2000, C=2.0, class_weight='balanced')
        pred = cross_val_predict(clf, Xl, yl, cv=StratifiedKFold(5, shuffle=True, random_state=0))
        cv_acc = float(np.mean([p == t for p, t in zip(pred, yl)]))
        print(f'классификатор на разметке продавцов, кросс-валидация: {100 * cv_acc:.0f}% (n={len(keep)}, классы {dict(Counter(yl))})', flush=True)
        if cv_acc > zs_acc:
            use_sup = True
            clf.fit(Xl, yl)
    con.execute('delete from cond')
    for k, cid in enumerate(ids):
        if use_sup:
            pr = clf.predict_proba(np.hstack([X[k], Z[k]])[None])[0]
            probs = {c: float(p) for c, p in zip(clf.classes_, pr)}
            mdl = f'clip+logreg cv={cv_acc:.2f}'
        else:
            probs = {c: float(p) for c, p in zip(CLASSES, Z[k])}
            mdl = f'clip zero-shot agree={zs_acc:.2f}'
        best = max(probs, key=probs.get)
        con.execute('insert into cond values(?,?,?,?,?,?)', (cid, best, probs[best], json.dumps(probs, ensure_ascii=False),
                                                          ninterior[cid], mdl))
    con.commit()
    print('оценено объявлений:', len(ids), '| модель:', 'классификатор' if use_sup else 'zero-shot', flush=True)
    dist = Counter(con.execute('select label from cond').fetchall()[i][0] for i in range(len(ids)))
    print('распределение:', dict(dist))


def add_parser(sub):
    c = sub.add_parser('condition', help='оценка состояния по фото')
    c.set_defaults(fn=cmd_condition)
