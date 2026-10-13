#!/usr/bin/env python3
"""Сравнение локальных VLM на эталонном наборе (market-data/gold): все фото объявления → анкета осмотра.

python3 vlm_eval.py <model-key> [--limit N]   — прогон одной модели, результат в market-data/gold/eval_<key>.json
python3 vlm_eval.py report                    — сводная таблица по всем прогнанным моделям
"""
import glob
import json
import os
import re
import subprocess
import sys
import time

LLAMA = os.environ.get('LLAMA_BIN', '/tmp/claude-0/-home-user-test/91795489-9a81-5d0c-ab47-d3353bb9d504/scratchpad/llama/llama-b11583')
MODELS_DIR = os.environ.get('VLM_MODELS', '/tmp/claude-0/-home-user-test/91795489-9a81-5d0c-ab47-d3353bb9d504/scratchpad/models')
GOLD = 'market-data/gold'
# ключ → (репозиторий HF, шаблон файла модели, шаблон mmproj)
MODELS = {
    'qwen25vl-3b': ('ggml-org/Qwen2.5-VL-3B-Instruct-GGUF', r'Q4_K_M\.gguf$', r'mmproj.*\.gguf$'),
    'qwen3vl-2b': ('Qwen/Qwen3-VL-2B-Instruct-GGUF', r'Q4_K_M\.gguf$', r'mmproj.*Q8_0\.gguf$|mmproj.*\.gguf$'),
    'qwen3vl-4b': ('Qwen/Qwen3-VL-4B-Instruct-GGUF', r'Q4_K_M\.gguf$', r'mmproj.*Q8_0\.gguf$|mmproj.*\.gguf$'),
    'qwen3vl-8b': ('Qwen/Qwen3-VL-8B-Instruct-GGUF', r'Q4_K_M\.gguf$', r'mmproj.*Q8_0\.gguf$|mmproj.*\.gguf$'),
    'qwen35-4b': ('unsloth/Qwen3.5-4B-GGUF', r'Q4_K_M\.gguf$', r'mmproj.*F16\.gguf$|mmproj.*\.gguf$'),
    'qwen35-9b': ('unsloth/Qwen3.5-9B-GGUF', r'Q4_K_M\.gguf$', r'mmproj.*F16\.gguf$|mmproj.*\.gguf$'),
    'gemma4-e4b': ('ggml-org/gemma-4-E4B-it-GGUF', r'Q4_K_M\.gguf$|it-q4_0\.gguf$', r'mmproj.*Q8_0\.gguf$|mmproj.*\.gguf$'),
    'gemma4-e2b': ('ggml-org/gemma-4-E2B-it-GGUF', r'Q4_K_M\.gguf$|it-q4_0\.gguf$', r'mmproj.*Q8_0\.gguf$|mmproj.*\.gguf$'),
    'minicpmv46': ('openbmb/MiniCPM-V-4.6-gguf', r'Q4_K_M\.gguf$', r'mmproj.*\.gguf$'),
    # модели 2026 года (апрель–октябрь)
    'qwen36-35b-a3b': ('unsloth/Qwen3.6-35B-A3B-GGUF', r'UD-IQ2_M\.gguf$', r'mmproj-F16\.gguf$|mmproj.*\.gguf$'),
    'gemma4-12b': ('unsloth/gemma-4-12b-it-GGUF', r'it-Q4_K_M\.gguf$', r'mmproj-F16\.gguf$|mmproj.*\.gguf$'),
    'minicpmv47-1b': ('openbmb/MiniCPM-V-4.7-1B-gguf', r'Q4_K_M\.gguf$', r'mmproj-model-f16-4x\.gguf$|mmproj.*\.gguf$'),
    # Bonsai 2 27B: тернарные веса, нужна сборка PrismML llama.cpp (LLAMA_BIN)
    'bonsai2-27b': ('prism-ml/Ternary-Bonsai-2-27B-gguf', r'27B-PQ2_0\.gguf$', r'mmproj-Q8_0\.gguf$'),
    # «решающая» модель: классы за один проход без генерации текста (llama-server /v1/systemone)
    'd1-3b': ('LiquidAI/d1-3B-GGUF', r'd1-3B-Q8_0\.gguf$|Q4_K_M\.gguf$', r'mmproj-d1-3B-Q8_0\.gguf$|mmproj.*\.gguf$'),
}
DECISION = {'d1-3b'}
KNOWN_FILES = {
    'qwen36-35b-a3b': ['Qwen3.6-35B-A3B-UD-IQ2_M.gguf', 'mmproj-F16.gguf'],
    'bonsai2-27b': ['Ternary-Bonsai-2-27B-PQ2_0.gguf', 'Ternary-Bonsai-2-27B-mmproj-Q8_0.gguf'],
}
DECISION_Q = {
    'condition': {'type': 'choice', 'instructions': 'Which class fits the WHOLE apartment interior shown?', 'criteria': {
        'not_shown': 'no photo shows this apartment inside: only building, courtyard, plans or CGI renders',
        'bare_shell': 'no finishing: bare concrete or brick walls, concrete floor, exposed wiring',
        'white_box': 'developer pre-finish: smooth white walls, screed floor, no kitchen, no floor covering',
        'needs_renovation': 'worn Soviet-era finishes: peeling wallpaper, old linoleum or worn parquet, small 1970s tiles, old wooden windows',
        'old_livable': 'renovated long ago (1995-2015), intact but dated: wallpaper, laminate, beige tiles with borders, arches, multi-level ceilings',
        'cosmetic': 'recent budget refresh: new wallpaper or paint, cheap laminate, simple tiles, basic mass-market kitchen',
        'modern': 'quality renovation of the last 7 years: even painted walls, large-format porcelain, wall-hung toilet, built-in kitchen',
        'designer': 'architect project: custom built-ins, premium stone and wood, designer lighting'}},
    'works': {'type': 'choice', 'instructions': 'Which works must a buyer do before reselling this apartment?', 'criteria': {
        'none': 'sell as is', 'refresh': 'paint and small fixes only',
        'cosmetic': 'new floors, walls and doors; bathroom and kitchen stay',
        'major': 'cosmetic plus new bathroom, kitchen, wiring and plumbing',
        'full': 'all finishing from shell or white box'}},
    'renovation_age': {'type': 'choice', 'instructions': 'How old is the renovation?', 'criteria': {
        'new_0_3': '0-3 years', 'recent_3_7': '3-7 years', 'dated_7_15': '7-15 years', 'old_15_plus': 'over 15 years',
        'soviet': 'Soviet-era finishes', 'none': 'no renovation: shell, white box or not shown'}},
}
ORDER = ['bare_shell', 'white_box', 'needs_renovation', 'old_livable', 'cosmetic', 'modern', 'designer']
READY = {'modern', 'designer'}


def fetch(key):
    from huggingface_hub import HfApi, hf_hub_download
    repo, mpat, ppat = MODELS[key]
    try:
        files = HfApi().list_repo_files(repo)
    except Exception:   # лимит запросов HF к списку файлов — берём известные имена
        files = KNOWN_FILES.get(key) or []
    def pick(pat, exclude=None):
        for alt in pat.split('|'):
            c = sorted(f for f in files if re.search(alt, f, re.I) and 'mmproj' not in f.lower()) if exclude else \
                sorted(f for f in files if re.search(alt, f, re.I))
            if c:
                return c[0]
        raise SystemExit(f'{repo}: нет файла по шаблону {pat}; файлы: {files}')
    mf, pf = pick(mpat, exclude=True), pick(ppat)
    d = f'{MODELS_DIR}/{key}'
    return hf_hub_download(repo, mf, local_dir=d), hf_hub_download(repo, pf, local_dir=d), f'{repo}/{mf}'


_CLIP = {}


def interior_photos(paths):
    """CLIP смотрит все фото объявления и оставляет снимки самой квартиры (без фасадов, видов, карт, рендеров)."""
    import torch
    from PIL import Image
    from transformers import CLIPModel, CLIPProcessor
    if not _CLIP:
        _CLIP['m'] = CLIPModel.from_pretrained('openai/clip-vit-base-patch16').eval()
        _CLIP['p'] = CLIPProcessor.from_pretrained('openai/clip-vit-base-patch16')
        kinds = {'in': ['a photo of a room interior in an apartment', 'a photo of a kitchen in an apartment',
                        'a photo of a bathroom or toilet', 'a photo of an empty unfinished room with concrete walls',
                        'a photo of an apartment hallway with doors'],
                 'out': ['a photo of a building exterior', 'a view from a window over a city', 'a photo of a courtyard or street',
                         'a floor plan drawing', 'a map', 'a computer rendering of a luxury building', 'an advertising banner with text',
                         'a photo of a building lobby or elevator hall', 'a staircase in an apartment building entrance']}
        with torch.no_grad():
            t = _CLIP['m'].get_text_features(**_CLIP['p'](text=kinds['in'] + kinds['out'], return_tensors='pt', padding=True))
        _CLIP['t'] = torch.nn.functional.normalize(t, dim=-1)
        _CLIP['n_in'] = len(kinds['in'])
    keep = []
    with torch.no_grad():
        for k in range(0, len(paths), 16):
            ims = [Image.open(p).convert('RGB') for p in paths[k:k + 16]]
            e = torch.nn.functional.normalize(_CLIP['m'].get_image_features(**_CLIP['p'](images=ims, return_tensors='pt')), dim=-1)
            for p, j in zip(paths[k:k + 16], (e @ _CLIP['t'].T).argmax(-1).tolist()):
                if j < _CLIP['n_in']:
                    keep.append(p)
    return keep


class Server:
    """llama-server: модель грузится один раз, методичка (system) кэшируется между квартирами."""
    def __init__(self, model, mmproj, max_tokens, port=8091):
        import urllib.request
        self.url = f'http://127.0.0.1:{port}'
        self.proc = subprocess.Popen(
            [f'{LLAMA}/llama-server', '-m', model, '--mmproj', mmproj, '-c', '12288', '-t', str(os.cpu_count() or 4),
             '--image-max-tokens', str(max_tokens), '--port', str(port), '--no-webui', '-np', '1', '--cache-reuse', '256', '--cache-ram', '0'],
            stdout=subprocess.DEVNULL, stderr=open('market-data/gold/server.log', 'w'))
        for _ in range(1200):
            try:
                urllib.request.urlopen(self.url + '/health', timeout=2)
                return
            except Exception:
                time.sleep(1)
        raise RuntimeError('llama-server не поднялся, см. market-data/gold/server.log')

    def ask(self, prompt, paths, schema):
        import base64, urllib.request
        content = [{'type': 'image_url', 'image_url': {'url': 'data:image/jpeg;base64,' + base64.b64encode(open(p, 'rb').read()).decode()}}
                   for p in paths] + [{'type': 'text', 'text': 'Inspect this apartment and fill in the form.'}]
        body = {'messages': [{'role': 'system', 'content': prompt}, {'role': 'user', 'content': content}],
                'temperature': 0, 'max_tokens': 700, 'repeat_penalty': 1.1, 'cache_prompt': True,
                'response_format': {'type': 'json_schema', 'json_schema': {'schema': schema}},
                'chat_template_kwargs': {'enable_thinking': False}}
        req = urllib.request.Request(self.url + '/v1/chat/completions', data=json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
        r = json.loads(urllib.request.urlopen(req, timeout=3600).read())
        return r['choices'][0]['message'].get('content') or '', r.get('timings', {})

    def decide(self, paths, side=384):
        """Решающая модель: классы с вероятностями за проход на вопрос, без генерации текста."""
        import base64, io, urllib.request
        from PIL import Image
        ims = []
        for p in paths:
            im = Image.open(p).convert('RGB')
            im.thumbnail((side, side))
            b = io.BytesIO()
            im.save(b, 'JPEG', quality=85)
            ims.append('data:image/jpeg;base64,' + base64.b64encode(b.getvalue()).decode())
        body = {'state': 'Listing photos of one apartment for sale in Moscow. Judge the apartment interior only.',
                'images': ims, 'questions': DECISION_Q}
        req = urllib.request.Request(self.url + '/v1/systemone', data=json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
        r = json.loads(urllib.request.urlopen(req, timeout=3600).read())
        a = r['answers']
        ans = {'condition': a['condition']['choice'], 'condition_confidence': round(a['condition'].get('confidence', 0), 2),
               'works': a['works']['choice'], 'renovation_age': a['renovation_age']['choice'],
               'probabilities': {k: v.get('probabilities') for k, v in a.items()}}
        return json.dumps(ans), {'prompt_n': r.get('usage', {}).get('input_tokens'), 'predicted_n': 0}

    def close(self):
        self.proc.terminate()
        self.proc.wait(30)


def run_listing(server, paths, prompt, schema):
    t0 = time.time()
    try:
        text, timings = server.decide(paths) if getattr(server, 'decision', False) else server.ask(prompt, paths, schema)
    except Exception as e:
        return None, time.time() - t0, str(e)[:500], {}, 1
    m = re.search(r'\{.*\}', text, re.S)
    ans = None
    if m:
        try:
            ans = json.loads(m.group(0))
        except Exception:
            ans = None
    return ans, time.time() - t0, text[-1500:], timings, 0


def cmd_run(key, limit, max_tokens):
    model, mmproj, name = fetch(key)
    prompt = open('inspector_prompt.md').read()
    schema = json.load(open('vlm_schema.json'))
    server = Server(model, mmproj, max_tokens)
    server.decision = key in DECISION
    meta = json.load(open(f'{GOLD}/meta.json'))
    gold = {k: v for k, v in json.load(open(f'{GOLD}/gold.json')).items() if not k.startswith('_')}
    out_f = f'{GOLD}/eval_{key}.json'
    res = json.load(open(out_f)) if os.path.exists(out_f) else {}
    for cid in list(gold)[:limit]:
        if cid in res:
            continue
        allp = sorted(glob.glob(f'{GOLD}/{cid}_*.jpg'))
        t0 = time.time()
        import photo_sort
        paths, _out, _dup = photo_sort.sort_photos(allp, 0.93)
        t_clip = time.time() - t0
        if paths:
            ans, sec, tail, speed, rc = run_listing(server, paths, prompt, schema)
        else:   # ни одного фото квартиры — VLM не запускаем
            ans, sec, tail, speed, rc = ({'condition': 'not_shown', 'works': None, 'renovation_age': 'none',
                                          'summary': 'Фото квартиры в объявлении нет.'}, 0.0, '', [], 0)
        res[cid] = dict(answer=ans, seconds=round(sec + t_clip, 1), vlm_seconds=round(sec, 1), photos=len(allp),
                        interior=len(paths), rc=rc, raw=None if ans else tail,
                        timings={k: speed.get(k) for k in ('prompt_n', 'prompt_ms', 'predicted_n', 'predicted_ms', 'cache_n')})
        json.dump(dict(res, _model=name), open(out_f, 'w'), ensure_ascii=False, indent=1)
        g = gold[cid]
        tm = res[cid]['timings']
        print(f'{cid}: {len(allp)} фото, в VLM {len(paths)}, {sec + t_clip:.0f} с '
              f'(вход {tm.get("prompt_n")} ток. за {(tm.get("prompt_ms") or 0) / 1000:.0f} с, кэш {tm.get("cache_n")}, '
              f'ответ {tm.get("predicted_n")} ток. за {(tm.get("predicted_ms") or 0) / 1000:.0f} с) | эталон {g["condition"]}/{g["works"]} | '
              f'модель {(ans or {}).get("condition")}/{(ans or {}).get("works")}', flush=True)
    server.close()


def _unused():
    pass


def score(res, gold):
    n = ok = near = works = age = valid = binary = 0
    secs = []
    for cid, g in gold.items():
        r = res.get(cid)
        if not r:
            continue
        n += 1
        secs.append(r['seconds'])
        a = r.get('answer') or {}
        valid += bool(a)
        c = a.get('condition')
        ok += c == g['condition']
        if g['condition'] == 'not_shown':
            near += c == 'not_shown'
            binary += c == 'not_shown'
        elif c in ORDER:
            near += abs(ORDER.index(c) - ORDER.index(g['condition'])) <= 1
            binary += (c in READY) == (g['condition'] in READY)
        works += a.get('works') == g['works']
        age += a.get('renovation_age') == g['age']
    return dict(n=n, condition=ok, near=near, binary=binary, works=works, age=age, valid=valid,
                sec=round(sum(secs) / len(secs)) if secs else None)


def cmd_report():
    gold = {k: v for k, v in json.load(open(f'{GOLD}/gold.json')).items() if not k.startswith('_')}
    meta = json.load(open(f'{GOLD}/meta.json'))
    from market_condition import SELLER_MAP
    seller = {}
    for cid, g in gold.items():
        s = meta[cid]['seller_repair']
        seller[cid] = {'answer': {'condition': {'no': 'needs_renovation', 'cosmetic': 'cosmetic', 'euro': 'modern',
                                                'design': 'designer', 'without': 'bare_shell', 'preFine': 'white_box'}.get(s)},
                       'seconds': 0}
    rows = [('метка продавца', score(seller, gold))]
    for f in sorted(glob.glob(f'{GOLD}/eval_*.json')):
        rows.append((f.split('eval_')[1][:-5], score(json.load(open(f)), gold)))
    print(f'{"модель":16s} {"n":>3} {"класс":>6} {"±1":>4} {"готов/нет":>9} {"работы":>7} {"возраст":>8} {"JSON":>5} {"с/объявл":>9}')
    for name, s in rows:
        print(f'{name:16s} {s["n"]:>3} {s["condition"]:>6} {s["near"]:>4} {s["binary"]:>9} {s["works"]:>7} {s["age"]:>8} '
              f'{s["valid"]:>5} {str(s["sec"]):>9}')


if __name__ == '__main__':
    if sys.argv[1] == 'report':
        cmd_report()
    else:
        lim = int(sys.argv[sys.argv.index('--limit') + 1]) if '--limit' in sys.argv else 99
        mt = int(sys.argv[sys.argv.index('--max-tokens') + 1]) if '--max-tokens' in sys.argv else 256
        cmd_run(sys.argv[1], lim, mt)
