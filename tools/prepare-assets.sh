#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

APKTOOL_VERSION="3.0.3"
OUT="app/src/main/assets/apktool.dex"

if [ -f "$OUT" ] && [ "$(stat -c%s "$OUT")" -gt 1000000 ]; then
  echo "apktool.dex sudah ada, lewati unduhan"
  exit 0
fi

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  echo "ANDROID_SDK_ROOT tidak diset" >&2
  exit 1
fi

BUILD_TOOLS=$(ls -1d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
PLATFORM=$(ls -1d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -1)
D8="$BUILD_TOOLS/d8"
[ -f "$D8" ] || D8="$BUILD_TOOLS/lib/d8.jar"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
echo "Mengunduh apktool $APKTOOL_VERSION"
curl -fsSL -o "$WORK/apktool.jar" \
  "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VERSION}/apktool_${APKTOOL_VERSION}.jar"

echo "Mengubah apktool.jar menjadi dex"
mkdir -p "$WORK/dex"
"$D8" --lib "$PLATFORM/android.jar" --min-api 26 --output "$WORK/dex" "$WORK/apktool.jar"

mkdir -p app/src/main/assets
mv "$WORK/dex/classes.dex" "$OUT"
echo "Selesai: $OUT ($(stat -c%s "$OUT") byte)"
