#!/usr/bin/env bash
# Waits for the gap between passes (last log line "next pass in"), then appends extra competitors to the queue.
cd "$(dirname "$0")"
until tail -1 workflow.log | grep -q "next pass in"; do sleep 10; done
python3 - <<'PY'
import json
st=json.load(open('state.json')); extra=json.load(open('extra_competitors.json'))
q=st['searches'].setdefault('конкуренты из рейтингов', [])
have={x['href'] for v in st['searches'].values() for x in v}
for n,i,d in extra:
    h=f'/сервисы/каталог-новостроек/объект/{i}'
    if h not in have: q.append({'href':h,'card':f'{d} km from {n}'})
json.dump(st,open('state.json','w'),ensure_ascii=False,indent=1)
print('added', len(q), 'at', __import__('datetime').datetime.utcnow().isoformat(timespec='seconds'))
PY
