#!/usr/bin/env bash
# Signs the release APK with the Halo signing key, for the release job in CI.
#
#   GH_TOKEN=... scripts/sign-apk.sh <unsigned.apk> <signed.apk>
#
# Android installs an update only when it is signed with the same key as the
# installed app, and only then keeps the app's data: the device key and its
# membership. So every release needs the same key, and it must not be public.
# It lives in a draft release of this repository, which only people with write
# access can see. The first release finds none: the key is made then and the
# draft created. Deleting that draft means the next APK no longer installs
# over the old one.

set -euo pipefail

UNSIGNED=$1
SIGNED=$2
KEY_TAG=halo-android-signing-key
KEY_FILE=halo-android.p12
ALIAS=halo
# The file is only as private as the draft that holds it, so a password adds nothing.
PASS=halo-android

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
repo=$GITHUB_REPOSITORY

draft=$(gh api --paginate "repos/$repo/releases?per_page=100" \
    --jq ".[] | select(.draft and .tag_name == \"$KEY_TAG\") | .id" | head -n1)
if [[ -n $draft ]]; then
    asset=$(gh api "repos/$repo/releases/$draft/assets" \
        --jq ".[] | select(.name == \"$KEY_FILE\") | .id")
    if [[ -z $asset ]]; then
        echo "The draft release $KEY_TAG has no $KEY_FILE: the signing key is lost." >&2
        exit 1
    fi
    gh api -H 'Accept: application/octet-stream' "repos/$repo/releases/assets/$asset" \
        >"$work/$KEY_FILE"
    echo "Signing with the key from the draft release $KEY_TAG."
else
    keytool -genkeypair -keystore "$work/$KEY_FILE" -storetype PKCS12 \
        -storepass "$PASS" -keypass "$PASS" -alias "$ALIAS" \
        -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Halo" >/dev/null
    gh release create "$KEY_TAG" --repo "$repo" --draft \
        --title "Halo: Android signing key (keep as a draft, do not delete)" \
        --notes "The key every Halo APK is signed with, so that new versions install over old ones and keep their data. Only people with write access to this repository see drafts. Do not publish or delete this draft." \
        "$work/$KEY_FILE"
    echo "Made a new signing key and kept it in the draft release $KEY_TAG."
fi

build_tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
"$build_tools/apksigner" sign --ks "$work/$KEY_FILE" --ks-pass "pass:$PASS" \
    --ks-key-alias "$ALIAS" --out "$SIGNED" "$UNSIGNED"
"$build_tools/apksigner" verify --print-certs "$SIGNED"
