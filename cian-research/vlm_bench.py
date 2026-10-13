"""Замер скорости и качества локальной VLM на одном объявлении (CPU)."""
import json
import os
import sqlite3
import sys
import time

import torch
from PIL import Image
from transformers import AutoProcessor

MODEL = sys.argv[1]
DB = 'market-data/akademicheskiy.sqlite'
torch.set_num_threads(os.cpu_count() or 4)

PROMPT = """You are a strict renovation inspector. These images are photos of ONE apartment listed for sale.
Look at what is actually visible (floors, walls, ceilings, kitchen, bathroom, windows, furniture, wear and damage).
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


def main():
    con = sqlite3.connect(DB)
    cid = int(sys.argv[2]) if len(sys.argv) > 2 else con.execute(
        'select cian_id from photo where path is not null group by cian_id having count(*) >= 5 limit 1').fetchone()[0]
    paths = [r[0] for r in con.execute('select path from photo where cian_id=? and path is not null order by idx', (cid,))][1:4]
    ims = [Image.open(p).convert('RGB') for p in paths]
    t0 = time.time()
    if 'Qwen2.5' in MODEL:
        from transformers import Qwen2_5_VLForConditionalGeneration as M
    else:
        from transformers import Qwen2VLForConditionalGeneration as M
    dtype = torch.bfloat16 if '3B' in MODEL else torch.float32
    model = M.from_pretrained(MODEL, torch_dtype=dtype).eval()
    proc = AutoProcessor.from_pretrained(MODEL, min_pixels=128 * 28 * 28, max_pixels=320 * 28 * 28)
    print(f'загрузка {time.time() - t0:.0f} c', flush=True)
    msgs = [{'role': 'user', 'content': [{'type': 'image'} for _ in ims] + [{'type': 'text', 'text': PROMPT}]}]
    text = proc.apply_chat_template(msgs, add_generation_prompt=True)
    inp = proc(text=[text], images=ims, return_tensors='pt')
    t1 = time.time()
    with torch.no_grad():
        out = model.generate(**inp, max_new_tokens=260, do_sample=False)
    ans = proc.batch_decode(out[:, inp['input_ids'].shape[1]:], skip_special_tokens=True)[0]
    n_new = out.shape[1] - inp['input_ids'].shape[1]
    print(f'объявление {cid}: {len(ims)} фото, вход {inp["input_ids"].shape[1]} токенов, ответ {n_new} токенов, '
          f'{time.time() - t1:.0f} c', flush=True)
    print(ans)
    json.dump(dict(model=MODEL, cid=cid, paths=paths, answer=ans, seconds=time.time() - t1),
              open(f'vlm_bench_{MODEL.split("/")[-1]}.json', 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
