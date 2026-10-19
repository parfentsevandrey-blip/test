#!/usr/bin/env bash
# Ждёт, пока Циан перестанет отвечать капчей на облачный пул (один лёгкий запрос раз в 10 мин),
# после двух чистых ответов подряд запускает demand/collect.py (он продолжает с места остановки).
# Если сбор снова упрётся в капчу -- опять ждёт. usage: run_when_cool.sh OUT_DIR JK_ID...
cd "$(dirname "$0")/.." || exit 1
OUT=$1; shift
PY=.venv/bin/python
probe() {
  $PY -I - <<'EOF'
from curl_cffi import requests
q={"jsonQuery":{"_type":"flatsale","engine_version":{"type":"term","value":2},"geo":{"type":"geo","value":[{"type":"newobject","id":4025502}]},"page":{"type":"term","value":1}}}
try:
    r=requests.post("https://api.cian.ru/search-offers/v2/search-offers-desktop/",json=q,impersonate="chrome",
                    headers={"origin":"https://www.cian.ru","referer":"https://www.cian.ru/"},timeout=60)
    print("OK" if r.text.lstrip().startswith("{") else "BLOCK")
except Exception:
    print("ERR")
EOF
}
for round in $(seq 1 30); do
  ok=0
  while [ $ok -lt 2 ]; do
    res=$(probe); echo "$(date +%H:%M) probe $res"
    if [ "$res" = OK ]; then ok=$((ok+1)); sleep 60; else ok=0; sleep 600; fi
  done
  echo "$(date +%H:%M) cool -> collect (round $round)"
  $PY -I demand/collect.py "$OUT" "$@" && { echo "$(date +%H:%M) COLLECT DONE"; exit 0; }
  echo "$(date +%H:%M) collect stopped on captcha -> cooling again"
done
