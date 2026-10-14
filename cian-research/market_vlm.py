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


def photos_complete(con, cid):
    """Скачаны ли все фото объявления (404 не в счёт) — иначе осмотр подождёт."""
    urls = json.loads(con.execute('select photos from listing where cian_id=?', (cid,)).fetchone()[0] or '[]')
    rows = {r[0]: (r[1], r[2]) for r in con.execute('select idx, url, path from photo where cian_id=?', (cid,))}
    return all(i in rows and rows[i][0] == u and (rows[i][1] is None or os.path.exists(rows[i][1]))
               for i, u in enumerate(urls) if u)


def listing_note(con, cid):
    """Факты объявления для модели: год дома и новостройка — ремонт не может быть старше дома."""
    r = con.execute('select build_year, year_lo, year_hi, is_new from listing where cian_id=?', (cid,)).fetchone()
    if not r:
        return ''
    year, lo, hi, new = r
    if year:
        s = f'Building year: {year}.'
    elif lo and (not hi or hi >= 2090):
        s = f'Building year: {lo} or later.'
    elif lo or hi:
        s = f'Building year: between {lo or "?"} and {hi or "?"}.'
    else:
        s = 'Building year: unknown.'
    return s + (' New building (sold by the developer or recently completed).' if new else '')


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

    def pending():
        if a.ids:
            ids = [int(i) for i in a.ids.split(',') if int(i) not in done_ids]
            return ids, len(ids)
        ids = [r[0] for r in con.execute(
            f'select cian_id from listing where {where} and cian_id not in (select cian_id from inspect where model=?) '
            f'order by first_seen desc', (MODEL_NAME,)) if listing_photos(con, r[0])]
        return [i for i in ids if photos_complete(con, i)], len(ids)

    done_ids = set()
    ready, total = pending()
    print(f'к осмотру: {len(ready)} объявлений с полным набором фото (ещё докачиваются: {total - len(ready)}), '
          f'~{len(ready) * 2:.0f} мин', flush=True)
    if not ready and not a.wait:
        return
    prompt = open('inspector_prompt.md').read()
    schema = json.load(open('vlm_schema.json'))
    import vlm_eval
    vlm_eval.LLAMA = os.environ['LLAMA_BIN']
    server = Server(MODEL, MMPROJ, 96, log='market-data/vlm-server.log', ctx=16384)
    t_all, deadline, n, failed = time.time(), time.time() + a.max_minutes * 60, 0, set()
    try:
        while time.time() < deadline and n < a.limit:
            ready, total = pending()
            ready = [i for i in ready if i not in failed]
            if not ready:
                if a.wait and total > len(failed):    # фото ещё докачиваются (`photos` в параллель)
                    time.sleep(60)
                    continue
                break
            cid = ready[0]
            allp = listing_photos(con, cid)
            keep, _out, _dup = photo_sort.sort_photos(allp, 0.93)
            keep = keep[:MAX_PHOTOS]
            t0 = time.time()
            if keep:
                ans, _sec, raw, _t, _rc = run_listing(server, keep, prompt, schema, listing_note(con, cid))
            else:
                ans, raw = {'condition': 'not_shown', 'renovation_age': 'none', 'works': None,
                            'summary': 'На фото нет самой квартиры.'}, ''
            if ans is None:
                failed.add(cid)
                print(f'  {cid}: нет ответа модели — повторим в следующий раз', flush=True)
                continue
            done_ids.add(cid)
            if a.dry:
                old = con.execute('select data from inspect where cian_id=?', (cid,)).fetchone()
                o = json.loads(old[0]) if old and old[0] else {}
                print(f'  {cid} [{listing_note(con, cid)}] было {o.get("condition")}/{o.get("renovation_age")}/{o.get("works")} '
                      f'→ стало {ans.get("condition")}/{ans.get("renovation_age")}/{ans.get("works")} | {ans.get("summary")}', flush=True)
                continue
            con.execute('insert or replace into inspect values(?,?,?,?,?,?,?,?,?,date("now"))',
                        (cid, MODEL_NAME, json.dumps(keep), raw or None, json.dumps(ans, ensure_ascii=False),
                         COND_RU.get(ans.get('condition')), AGE_RU.get(ans.get('renovation_age')),
                         WORKS_RU.get(ans.get('works')), time.time() - t0))
            con.commit()
            n += 1
            if n % 5 == 0:
                left = pending()[1]
                print(f'  осмотрено {n}, {(time.time() - t_all) / n:.0f} с/объявление, осталось {left} '
                      f'(~{left * (time.time() - t_all) / n / 60:.0f} мин)', flush=True)
        print(f'осмотрено за запуск: {n}; осталось: {pending()[1]}', flush=True)
    finally:
        server.close()


def add_parser(sub):
    p = sub.add_parser('inspect', help='визуальный осмотр всех фото локальной VLM')
    p.add_argument('--limit', type=int, default=10000)
    p.add_argument('--max-minutes', type=float, default=1e9, help='остановиться после N минут (продолжение — следующим запуском)')
    p.add_argument('--all', action='store_true', help='включая ушедшие объявления')
    p.add_argument('--ids', help='осмотреть только эти объявления (через запятую), даже если уже осмотрены')
    p.add_argument('--dry', action='store_true', help='не записывать, а показать «было → стало»')
    p.add_argument('--wait', action='store_true', help='ждать объявления, чьи фото ещё докачиваются')
    p.set_defaults(fn=cmd_inspect)
