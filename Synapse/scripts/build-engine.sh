#!/usr/bin/env bash
# Сборка движка llama.cpp из исходников: статические библиотеки с Metal и Accelerate.
#   ./scripts/build-engine.sh        — собирает Vendor/llama, если там нет нужной версии
#   LLAMA_TAG=b11800 ./scripts/build-engine.sh
#
# Результат (в .gitignore):
#   Vendor/llama/include/   — llama.h, mtmd.h (зрение) и заголовки ggml
#   Vendor/llama/lib/libSynapseEngine.a — все библиотеки движка одним архивом
#   Vendor/llama/architectures.txt      — архитектуры моделей, которые понимает эта версия
#   Vendor/llama/TAG
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="${LLAMA_TAG:-$(cat "$ROOT/ENGINE_VERSION")}"
OUT="$ROOT/Vendor/llama"
SRC="${LLAMA_SRC:-$ROOT/Vendor/llama.cpp-src}"

step() { printf '\n\033[1;35m◆ %s\033[0m\n' "$1"; }

if [[ -f "$OUT/TAG" && "$(cat "$OUT/TAG")" == "$TAG" && -f "$OUT/lib/libSynapseEngine.a" && -f "$OUT/include/mtmd.h" ]]; then
  echo "Движок llama.cpp $TAG уже собран — пропускаю"
  exit 0
fi

command -v cmake >/dev/null || { echo "Нужен CMake: brew install cmake"; exit 1; }

step "Исходники llama.cpp $TAG"
if [[ ! -d "$SRC/.git" ]] || [[ "$(git -C "$SRC" describe --tags --exact-match 2>/dev/null)" != "$TAG" ]]; then
  rm -rf "$SRC"
  git clone --quiet --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git "$SRC"
fi
git -C "$SRC" log -1 --format='  %h %cd %s' --date=short

step "Компиляция (Metal + Accelerate, статически, arm64)"
BUILD="$SRC/build-synapse"
rm -rf "$BUILD"
# Флаги — как в официальном build-xcframework.sh проекта: только библиотека, без утилит,
# сервера, тестов и сетевых зависимостей; шейдеры Metal встраиваются в библиотеку.
cmake -S "$SRC" -B "$BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=14.0 \
  -DBUILD_SHARED_LIBS=OFF \
  -DLLAMA_BUILD_COMMON=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_TOOLS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_BUILD_APP=OFF \
  -DLLAMA_BUILD_UI=OFF \
  -DLLAMA_BUILD_MTMD=ON \
  -DMTMD_VIDEO=OFF \
  -DLLAMA_OPENSSL=OFF \
  -DGGML_METAL=ON \
  -DGGML_METAL_EMBED_LIBRARY=ON \
  -DGGML_BLAS=ON \
  -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_RPC=OFF >"$BUILD.log" 2>&1 || { tail -60 "$BUILD.log"; exit 1; }
if ! cmake --build "$BUILD" --config Release -j "$(sysctl -n hw.logicalcpu)" >"$BUILD-make.log" 2>&1; then
  grep -E 'error' "$BUILD-make.log" | head -40
  tail -40 "$BUILD-make.log"
  exit 1
fi
grep -E 'Built target' "$BUILD-make.log" | sed 's/^.*Built/  Built/'

step "Упаковка"
rm -rf "$OUT"
mkdir -p "$OUT/include" "$OUT/lib"
cp "$SRC/include/llama.h" "$OUT/include/"
cp "$SRC"/ggml/include/*.h "$OUT/include/"
# libmtmd — зрение: проектор изображений (mmproj) для мультимодальных моделей.
cp "$SRC/tools/mtmd/mtmd.h" "$SRC/tools/mtmd/mtmd-helper.h" "$OUT/include/"
LIBS=$(find "$BUILD" -name '*.a' | sort)
echo "$LIBS" | sed "s|$BUILD/|  |"
# Один архив — проще подключать к SwiftPM и нельзя забыть библиотеку.
libtool -static -o "$OUT/lib/libSynapseEngine.a" $LIBS 2>/dev/null
du -h "$OUT/lib/libSynapseEngine.a" | sed 's/^/  /'

# Список архитектур, которые понимает эта сборка — приложение показывает, какие модели запустятся.
awk '/LLM_ARCH_NAMES = \{/,/^\};/' "$SRC/src/llama-arch.cpp" | grep -oE '"[^"]+"' | tr -d '"' | grep -v '(unknown)' > "$OUT/architectures.txt"
echo "  архитектур: $(wc -l < "$OUT/architectures.txt" | tr -d ' ')"
cp "$SRC/LICENSE" "$OUT/LICENSE"
git -C "$SRC" log -1 --format='%cI' > "$OUT/DATE"
echo "$TAG" > "$OUT/TAG"
echo "Готово: $OUT"
