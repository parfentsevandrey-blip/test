#!/bin/sh
# Собирает «ядро» — программу themesh на Go — для трёх ABI Android и кладёт её в
# app/src/main/jniLibs/<abi>/libthemesh.so. Приложение запускает этот файл как обычную программу
# из ApplicationInfo.nativeLibraryDir (поэтому «библиотека» называется lib*.so: только такие файлы
# Android распаковывает при установке).
#
#   sh tools/build-core.sh            # все три ABI
#   sh tools/build-core.sh x86_64     # только эмулятор на ПК
#   VERSION=1.2.3 sh tools/build-core.sh   # записать версию в программу (как `make dist`)
#
# Нужен Go (версия из go.mod). Результат в git не попадает (*.so в android/.gitignore).
set -eu

here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
out="$here/../app/src/main/jniLibs"

want=${1:-all}
case "$want" in
all | arm64-v8a | x86_64 | armeabi-v7a) ;;
*)
    echo "build-core.sh: неизвестный ABI «$want» (бывают arm64-v8a, x86_64, armeabi-v7a или all)" >&2
    exit 2
    ;;
esac

if ! command -v go >/dev/null 2>&1; then
    echo "build-core.sh: не найден Go (https://go.dev/dl/); он нужен, чтобы собрать программу themesh" >&2
    exit 1
fi
if [ ! -f "$repo/cmd/themesh/main.go" ]; then
    echo "build-core.sh: не нашёл исходники программы ($repo/cmd/themesh); скрипт должен лежать в android/tools" >&2
    exit 1
fi

ldflags="-s -w"
if [ -n "${VERSION:-}" ]; then
    module=$(cd "$repo" && go list -m)
    ldflags="$ldflags -X $module/internal/mesh.Version=$VERSION"
fi

# build <abi> <GOARCH> [VAR=value ...]
build() {
    abi=$1
    goarch=$2
    shift 2
    mkdir -p "$out/$abi"
    printf '  %-12s ' "$abi"
    (cd "$repo" && env CGO_ENABLED=0 GOOS=linux GOARCH="$goarch" "$@" \
        go build -trimpath -ldflags "$ldflags" -o "$out/$abi/libthemesh.so" ./cmd/themesh)
    size=$(wc -c <"$out/$abi/libthemesh.so" | tr -d ' ')
    echo "libthemesh.so ($((size / 1024)) КБ)"
}

echo "Собираю ядро в $out"
if [ "$want" = all ] || [ "$want" = arm64-v8a ]; then build arm64-v8a arm64; fi
if [ "$want" = all ] || [ "$want" = x86_64 ]; then build x86_64 amd64; fi
if [ "$want" = all ] || [ "$want" = armeabi-v7a ]; then build armeabi-v7a arm GOARM=7; fi
echo "Готово."
