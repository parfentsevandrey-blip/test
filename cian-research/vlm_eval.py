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
}
ORDER = ['bare_shell', 'white_box', 'needs_renovation', 'old_livable', 'cosmetic', 'modern', 'designer']
READY = {'modern', 'designer'}


def fetch(key):
    from huggingface_hub import HfApi, hf_hub_download
    repo, mpat, ppat = MODELS[key]
    files = HfApi().list_repo_files(repo)
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


def run_listing(model, mmproj, paths, prompt, max_tokens):
    cmd = [f'{LLAMA}/llama-mtmd-cli', '-m', model, '--mmproj', mmproj, '-c', '12288', '-n', '700', '--repeat-penalty', '1.1', '--temp', '0',
           '-t', str(os.cpu_count() or 4), '--image-max-tokens', str(max_tokens), '-jf', 'vlm_schema.json',
           '-p', prompt, '--no-warmup']
    for p in paths:
        cmd += ['--image', p]
    t0 = time.time()
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    out = r.stdout
    m = re.search(r'\{.*\}', out, re.S)
    ans = None
    if m:
        try:
            ans = json.loads(m.group(0))
        except Exception:
            ans = None
    speed = re.findall(r'(prompt eval time|eval time)\s*=\s*([\d.]+) ms /\s*(\d+) tokens', r.stderr)
    return ans, time.time() - t0, out[-1500:], speed, r.returncode


def cmd_run(key, limit, max_tokens):
    model, mmproj, name = fetch(key)
    prompt = open('inspector_prompt.md').read()
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
            ans, sec, tail, speed, rc = run_listing(model, mmproj, paths, prompt, max_tokens)
        else:   # ни одного фото квартиры — VLM не запускаем
            ans, sec, tail, speed, rc = ({'condition': 'not_shown', 'works': None, 'renovation_age': 'none',
                                          'summary': 'Фото квартиры в объявлении нет.'}, 0.0, '', [], 0)
        res[cid] = dict(answer=ans, seconds=round(sec + t_clip, 1), vlm_seconds=round(sec, 1), photos=len(allp),
                        interior=len(paths), rc=rc, raw=None if ans else tail)
        json.dump(dict(res, _model=name), open(out_f, 'w'), ensure_ascii=False, indent=1)
        g = gold[cid]
        print(f'{cid}: {len(allp)} фото, в VLM {len(paths)}, {sec + t_clip:.0f} с | эталон {g["condition"]}/{g["works"]} | '
              f'модель {(ans or {}).get("condition")}/{(ans or {}).get("works")}', flush=True)


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
