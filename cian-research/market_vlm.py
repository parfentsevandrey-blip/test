"""Визуальный осмотр квартиры локальной VLM (Qwen2.5-VL-3B на CPU) — без расхода токенов Claude.

1. CLIP размечает тип каждого фото (кухня / санузел / комната / планировка / снаружи).
2. В осмотр идут до 4 интерьерных фото: кухня, санузел и комнаты.
3. VLM заполняет анкету осмотра (JSON), ответ кэшируется в таблице inspect — повторно не осматриваем.
"""
import json
import os
import re
import time

VLM = os.environ.get('VLM_MODEL', 'Qwen/Qwen2.5-VL-3B-Instruct')
CLIP = 'openai/clip-vit-base-patch16'
PROMPT = """You are a strict renovation inspector. These images are photos of ONE apartment listed for sale.
Look at what is actually visible (floors, walls, ceilings, kitchen, bathroom, windows, furniture, wear and damage).
Judge the age of the renovation by its style: arches, stucco moldings, glossy tiles and laminate of the 2000s are over 10 years old.
Reply with one JSON object and nothing else:
{"condition": "<bare concrete | white box | needs full renovation | old lived-in | cosmetic | modern renovation | designer premium>",
 "renovation_age": "<new | 3-10 years | over 10 years | soviet era | none>",
 "floor": "<material, state>", "walls": "<material, state>", "ceiling": "<type, state>",
 "kitchen": "<what is seen, age, state or not visible>", "bathroom": "<tiles, plumbing, state or not visible>",
 "windows": "<plastic | wooden | not visible>", "furniture": "<none | partial | full>, <style>",
 "defects": ["<each visible problem>"],
 "works_needed": "<none | cosmetic | major renovation | full renovation from scratch>",
 "summary": "<two sentences in Russian>"}
"""
KINDS = {'kitchen': 'a photo of a kitchen in an apartment', 'bathroom': 'a photo of a bathroom or toilet',
         'room': 'a photo of a living room or bedroom interior', 'empty': 'a photo of an empty unfinished room',
         'plan': 'a floor plan drawing', 'outside': 'a photo of a building exterior, courtyard, street or window view'}
COND_RU = {'bare concrete': 'без отделки', 'white box': 'white box', 'needs full renovation': 'требует ремонта',
           'old lived-in': 'требует ремонта', 'cosmetic': 'косметический', 'modern renovation': 'современный',
           'designer premium': 'дизайнерский'}
AGE_RU = {'new': 'новый', '3-10 years': '3–10 лет', 'over 10 years': 'старше 10 лет', 'soviet era': 'советский', 'none': 'нет ремонта'}
WORKS_RU = {'none': 'не нужны', 'cosmetic': 'косметика', 'major renovation': 'капитальный ремонт',
            'full renovation from scratch': 'ремонт с нуля'}


def parse_answer(text):
    m = re.search(r'\{.*\}', text, re.S)
    if not m:
        return None
    raw = m.group(0)
    for attempt in (raw, re.sub(r',\s*([}\]])', r'\1', raw)):
        try:
            return json.loads(attempt)
        except Exception:
            pass
    return None


def norm_choice(val, mapping):
    v = str(val or '').lower()
    for k in sorted(mapping, key=len, reverse=True):
        if k in v:
            return mapping[k]
    return None


def tag_photos(con, ids):
    """Тип фото по CLIP; сохраняется в photo.kind."""
    import torch
    from PIL import Image
    from transformers import CLIPModel, CLIPProcessor
    cols = {r[1] for r in con.execute('pragma table_info(photo)')}
    if 'kind' not in cols:
        con.execute('alter table photo add column kind text')
    todo = con.execute(f'select cian_id, idx, path from photo where path is not null and kind is null '
                       f'and cian_id in ({",".join(map(str, ids)) or 0})').fetchall()
    if not todo:
        return
    model, proc = CLIPModel.from_pretrained(CLIP).eval(), CLIPProcessor.from_pretrained(CLIP)
    names = list(KINDS)
    with torch.no_grad():
        t = torch.nn.functional.normalize(model.get_text_features(**proc(text=list(KINDS.values()), return_tensors='pt',
                                                                         padding=True)), dim=-1)
        for k in range(0, len(todo), 16):
            batch = [r for r in todo[k:k + 16] if os.path.exists(r[2])]
            ims = [Image.open(r[2]).convert('RGB') for r in batch]
            if not ims:
                continue
            e = torch.nn.functional.normalize(model.get_image_features(**proc(images=ims, return_tensors='pt')), dim=-1)
            for r, j in zip(batch, (e @ t.T).argmax(-1).tolist()):
                con.execute('update photo set kind=? where cian_id=? and idx=?', (names[j], r[0], r[1]))
    con.commit()


def pick_photos(con, cid, n=4):
    rows = con.execute('select idx, path, kind from photo where cian_id=? and path is not null order by idx', (cid,)).fetchall()
    rows = [r for r in rows if os.path.exists(r[1])]
    by = {}
    for r in rows:
        by.setdefault(r[2], []).append(r[1])
    pick = (by.get('kitchen', [])[:1] + by.get('bathroom', [])[:1] + by.get('room', [])[:2] + by.get('empty', [])[:2])
    pick += [p for p in by.get('room', [])[2:] if p not in pick]
    return pick[:n]


def cmd_inspect(a):
    import torch
    from PIL import Image
    from transformers import AutoProcessor, Qwen2_5_VLForConditionalGeneration
    from market import db_open
    torch.set_num_threads(os.cpu_count() or 4)
    con = db_open(a.db)
    con.execute('create table if not exists inspect(cian_id integer primary key, model text, photos text, raw text, '
                'data text, condition text, age text, works text, seconds real, date text)')
    where = 'gone_seen is null' if not a.all else '1'
    ids = [r[0] for r in con.execute(f'select cian_id from listing where {where} and cian_id not in '
                                     f'(select cian_id from inspect) order by first_seen desc')][:a.limit]
    tag_photos(con, ids)
    print(f'к осмотру: {len(ids)} объявлений (~{len(ids) * 50 / 60:.0f} мин)', flush=True)
    if not ids:
        return
    model = Qwen2_5_VLForConditionalGeneration.from_pretrained(VLM, dtype=torch.bfloat16).eval()
    proc = AutoProcessor.from_pretrained(VLM, min_pixels=128 * 28 * 28, max_pixels=320 * 28 * 28)
    t_all = time.time()
    for n, cid in enumerate(ids, 1):
        paths = pick_photos(con, cid)
        if not paths:
            con.execute('insert or replace into inspect(cian_id, model, photos, date) values(?,?,?,date("now"))',
                        (cid, VLM, '[]'))
            continue
        ims = [Image.open(p).convert('RGB') for p in paths]
        msgs = [{'role': 'user', 'content': [{'type': 'image'} for _ in ims] + [{'type': 'text', 'text': PROMPT}]}]
        inp = proc(text=[proc.apply_chat_template(msgs, add_generation_prompt=True)], images=ims, return_tensors='pt')
        t0 = time.time()
        with torch.no_grad():
            out = model.generate(**inp, max_new_tokens=280, do_sample=False)
        raw = proc.batch_decode(out[:, inp['input_ids'].shape[1]:], skip_special_tokens=True)[0]
        d = parse_answer(raw) or {}
        con.execute('insert or replace into inspect values(?,?,?,?,?,?,?,?,?,date("now"))',
                    (cid, VLM, json.dumps(paths), raw, json.dumps(d, ensure_ascii=False),
                     norm_choice(d.get('condition'), COND_RU), norm_choice(d.get('renovation_age'), AGE_RU),
                     norm_choice(d.get('works_needed'), WORKS_RU), time.time() - t0))
        con.commit()
        if n % 5 == 0 or n == len(ids):
            el = time.time() - t_all
            print(f'  {n}/{len(ids)}, {el / n:.0f} с/объявление, осталось ~{el / n * (len(ids) - n) / 60:.0f} мин', flush=True)


def add_parser(sub):
    p = sub.add_parser('inspect', help='визуальный осмотр фото локальной VLM')
    p.add_argument('--limit', type=int, default=10000)
    p.add_argument('--all', action='store_true', help='включая ушедшие объявления')
    p.set_defaults(fn=cmd_inspect)
