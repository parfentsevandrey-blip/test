#!/bin/bash
# Локальная модель осмотра: llama.cpp (CPU) + Gemma 4 12B QAT. Повторный запуск ничего не качает.
# Каталог — $VLM_HOME (по умолчанию ~/.cache/cian-vlm); пути печатаются для market_vlm.py.
set -euo pipefail
VLM_HOME=${VLM_HOME:-$HOME/.cache/cian-vlm}
TAG=b11583
mkdir -p "$VLM_HOME/models"
if [ ! -x "$VLM_HOME/llama-$TAG/llama-server" ]; then
  curl -sSL -o "$VLM_HOME/llama.tgz" "https://github.com/ggml-org/llama.cpp/releases/download/$TAG/llama-$TAG-bin-ubuntu-x64.tar.gz"
  tar xzf "$VLM_HOME/llama.tgz" -C "$VLM_HOME" && rm "$VLM_HOME/llama.tgz"
fi
python3 - "$VLM_HOME/models" <<'EOF'
import sys
from huggingface_hub import hf_hub_download
repo = 'google/gemma-4-12B-it-qat-q4_0-gguf'
for f in ('gemma-4-12b-it-qat-q4_0.gguf', 'mmproj-gemma-4-12b-it-qat-q4_0.gguf'):
    hf_hub_download(repo, f, local_dir=sys.argv[1])
EOF
python3 -c "from photo_sort import _load; _load()"   # SigLIP 2 для отбора фото — в кэш HF
echo "VLM готова: $VLM_HOME"
