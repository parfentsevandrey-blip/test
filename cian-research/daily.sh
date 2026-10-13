#!/bin/bash
# Ежедневное обновление барометра района (запускается по расписанию в облаке или вручную).
set -euo pipefail
cd "$(dirname "$0")"
python3 -c "import scrapling, torch, transformers, sklearn" 2>/dev/null || {
  pip install -q --root-user-action=ignore "scrapling[all]" pillow scikit-learn accelerate
  pip install -q --root-user-action=ignore --index-url https://download.pytorch.org/whl/cpu torch==2.8.0 torchvision==0.23.0
  pip install -q --root-user-action=ignore transformers==4.56.2
  scrapling install
}
python3 -u market.py collect                                   # выдача, события, просмотры (API + вежливый клиент)
if [ "$(date +%u)" = "1" ]; then python3 -u market.py history; fi   # история домов — раз в неделю
python3 -u market.py photos                                    # фото новых и ещё не осмотренных
python3 -u market.py inspect --limit "${INSPECT_LIMIT:-60}"    # осмотр локальной VLM (~55 с на объявление)
python3 -u market.py condition                                 # итоговая оценка состояния
python3 -u market.py build                                     # dashboard/data.json
