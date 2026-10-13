"""Визуальный осмотр квартиры локальной VLM (Gemma 4 12B QAT, llama.cpp на CPU) — без расхода токенов Claude.

1. SigLIP 2 смотрит ВСЕ фото объявления: отбрасывает фасады, дворы, планировки, виды из окна и почти одинаковые кадры.
2. Оставшиеся фото квартиры (до 32) уходят в модель вместе с методичкой inspector_prompt.md;
   ответ ограничен схемой vlm_schema.json (8 классов состояния, возраст ремонта, объём работ, описание по зонам).
3. Результат — в таблице inspect; повторно осматриваются только объявления, осмотренные другой моделью.
Модель выбрана сравнением на эталоне (VLM_BENCH.md); установка — vlm_setup.sh.
"""
import json
import os
import time

VLM_HOME = os.environ.get('VLM_HOME', os.path.expanduser('~/.cache/cian-vlm'))
MODEL_NAME = 'google/gemma-4-12B-it-qat-q4_0-gguf'
MODEL = f'{VLM_HOME}/models/gemma-4-12b-it-qat-q4_0.gguf'
MMPROJ = f'{VLM_HOME}/models/mmproj-gemma-4-12b-it-qat-q4_0.gguf'
MAX_PHOTOS = 32
COND_RU = {'not_shown': 'не показана', 'bare_shell': 'без отделки', 'white_box': 'white box',
           'needs_renovation': 'требует ремонта', 'old_livable': 'старый ремонт', 'cosmetic': 'косметический',
           'modern': 'современный', 'designer': 'дизайнерский'}
AGE_RU = {'new_0_3': '0–3 года', 'recent_3_7': '3–7 лет', 'dated_7_15': '7–15 лет', 'old_15_plus': 'старше 15 лет',
          'soviet': 'советский', 'none': 'нет ремонта'}
WORKS_RU = {'none': 'не нужны', 'refresh': 'освежить', 'cosmetic': 'косметика', 'major': 'капитальный ремонт',
            'full': 'ремонт с нуля'}


def listing_photos(con, cid):
    return [r[0] for r in con.execute('select path from photo where cian_id=? and path is not null order by idx', (cid,))
            if os.path.exists(r[0])]


def cmd_inspect(a):
    import photo_sort
    from market import db_open
    from vlm_eval import Server, run_listing
    os.environ.setdefault('LLAMA_BIN', f'{VLM_HOME}/llama-b11583')
    con = db_open(a.db)
    con.execute('create table if not exists inspect(cian_id integer primary key, model text, photos text, raw text, '
                'data text, condition text, age text, works text, seconds real, date text)')
    where = 'gone_seen is null' if not a.all else '1'
    ids = [r[0] for r in con.execute(
        f'select cian_id from listing where {where} and cian_id not in (select cian_id from inspect where model=?) '
        f'order by first_seen desc', (MODEL_NAME,))]
    ids = [i for i in ids if listing_photos(con, i)][:a.limit]     # без фото на диске — после `photos`
    print(f'к осмотру: {len(ids)} объявлений (~{len(ids) * 2:.0f} мин)', flush=True)
    if not ids:
        return
    prompt = open('inspector_prompt.md').read()
    schema = json.load(open('vlm_schema.json'))
    import vlm_eval
    vlm_eval.LLAMA = os.environ['LLAMA_BIN']
    server = Server(MODEL, MMPROJ, 96, log='market-data/vlm-server.log', ctx=16384)
    t_all, deadline = time.time(), time.time() + a.max_minutes * 60
    try:
        for n, cid in enumerate(ids, 1):
            allp = listing_photos(con, cid)
            keep, _out, _dup = photo_sort.sort_photos(allp, 0.93)
            keep = keep[:MAX_PHOTOS]
            t0 = time.time()
            if keep:
                ans, _sec, raw, _t, _rc = run_listing(server, keep, prompt, schema)
            else:
                ans, raw = {'condition': 'not_shown', 'renovation_age': 'none', 'works': None,
                            'summary': 'No photo shows the apartment itself.'}, ''
            if ans is None:
                print(f'  {cid}: нет ответа модели — повторим в следующий раз', flush=True)
                continue
            con.execute('insert or replace into inspect values(?,?,?,?,?,?,?,?,?,date("now"))',
                        (cid, MODEL_NAME, json.dumps(keep), raw or None, json.dumps(ans, ensure_ascii=False),
                         COND_RU.get(ans.get('condition')), AGE_RU.get(ans.get('renovation_age')),
                         WORKS_RU.get(ans.get('works')), time.time() - t0))
            con.commit()
            el = time.time() - t_all
            if n % 5 == 0 or n == len(ids):
                print(f'  {n}/{len(ids)}, {el / n:.0f} с/объявление, осталось ~{el / n * (len(ids) - n) / 60:.0f} мин', flush=True)
            if time.time() > deadline:
                print(f'  стоп по времени ({a.max_minutes} мин): осмотрено {n}, остальные — при следующем запуске', flush=True)
                break
    finally:
        server.close()


def add_parser(sub):
    p = sub.add_parser('inspect', help='визуальный осмотр всех фото локальной VLM')
    p.add_argument('--limit', type=int, default=10000)
    p.add_argument('--max-minutes', type=float, default=1e9, help='остановиться после N минут (продолжение — следующим запуском)')
    p.add_argument('--all', action='store_true', help='включая ушедшие объявления')
    p.set_defaults(fn=cmd_inspect)
