#!/usr/bin/env bash
# Builds the Rust core for Android and generates its Kotlin bindings.
#
#   scripts/build-android-rust.sh <out-dir> [abi...]
#
# Writes <out-dir>/kotlin (UniFFI bindings) and <out-dir>/jniLibs/<abi>/libhalo_ffi.so.
# ABIs default to arm64-v8a (phones) and x86_64 (emulator). Needs cargo-ndk and
# ANDROID_NDK_HOME. The Gradle build calls this script.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$(mkdir -p "$1" && cd "$1" && pwd)
shift
ABIS=("$@")
if ((${#ABIS[@]} == 0)); then
    ABIS=(arm64-v8a x86_64)
fi
cd "$ROOT"

# The bindings come from the library's metadata, which is the same on every
# target, so a quick host build is enough to generate them.
cargo build --quiet -p halo-ffi
case "$(uname -s)" in
    Darwin) host_lib=target/debug/libhalo_ffi.dylib ;;
    *) host_lib=target/debug/libhalo_ffi.so ;;
esac
rm -rf "$OUT/kotlin"
cargo run --quiet -p halo-ffi --features bindgen --bin uniffi-bindgen -- \
    generate --language kotlin --no-format --out-dir "$OUT/kotlin" "$host_lib"

# cargo-ndk copies every shared library it finds, dependencies included; keep ours only.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
targets=()
for abi in "${ABIS[@]}"; do
    targets+=(-t "$abi")
done
cargo ndk "${targets[@]}" --platform 26 -o "$tmp" build --release -p halo-ffi
rm -rf "$OUT/jniLibs"
for abi in "${ABIS[@]}"; do
    mkdir -p "$OUT/jniLibs/$abi"
    cp "$tmp/$abi/libhalo_ffi.so" "$OUT/jniLibs/$abi/"
done
