#!/usr/bin/env bash
# Builds aapt2 for Android from AOSP sources with the Android NDK.
#
# AOSP builds aapt2 with Soong, which needs a full platform checkout. This script instead uses the
# CMake port lzhiyong/android-sdk-tools (Apache-2.0), pinned to a commit, and fetches the AOSP
# sources at a pinned platform-tools tag.
#
# usage: scripts/build-aapt2.sh <abi> <android-api-level> <output-dir>
#   abi: arm64-v8a | armeabi-v7a | x86_64 | x86
# env:   ANDROID_NDK_HOME (NDK root), WORK_DIR (scratch dir, default ./build/aapt2)
set -euo pipefail

ABI=${1:?abi}
API=${2:?api level}
OUT=$(realpath -m "${3:?output dir}")

AOSP_TAG=${AOSP_TAG:-platform-tools-35.0.2}
BUILDER_REPO=${BUILDER_REPO:-https://github.com/lzhiyong/android-sdk-tools}
BUILDER_SHA=${BUILDER_SHA:-50713285d4de73dd36735928217523817ad16988}
NDK=${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point to the NDK}
WORK=$(realpath -m "${WORK_DIR:-build/aapt2}")

mkdir -p "$WORK" "$OUT"
cd "$WORK"

if [ ! -d builder/.git ]; then
  git init -q builder
  git -C builder remote add origin "$BUILDER_REPO"
  git -C builder fetch -q --depth 1 origin "$BUILDER_SHA"
  git -C builder checkout -q FETCH_HEAD
fi
cd builder
test "$(git rev-parse HEAD)" = "$BUILDER_SHA"

echo "== Fetching AOSP sources at $AOSP_TAG"
python3 get_source.py --tags "$AOSP_TAG"

# get_source.py does not stop when a clone fails, so check every repository it should have fetched.
missing=$(python3 - <<'PY'
import json, os
print(" ".join(r["path"] for r in json.load(open("repos.json")) if not os.path.isdir(r["path"])))
PY
)
if [ -n "$missing" ]; then
  echo "::error::AOSP repositories missing for tag $AOSP_TAG: $missing"
  exit 1
fi

echo "== Applying build patches where they still fit this tag"
apply() { # <dir> <patch>
  if patch -p1 -d "$1" --forward --dry-run -s <"$2" >/dev/null 2>&1; then
    patch -p1 -d "$1" --forward -s <"$2"
    echo "applied $2"
  else
    echo "::warning::patch $2 does not apply to $AOSP_TAG, skipped"
  fi
}
apply . patches/protobuf_CMakeLists.txt.patch
apply src patches/StringPiece.h.patch

echo "== Building host protoc"
mkdir -p src/protobuf/build
(
  cd src/protobuf/build
  cmake -GNinja -Dprotobuf_BUILD_TESTS=OFF ..
  ninja protoc
)
PROTOC=$(find "$PWD/src/protobuf/build" -maxdepth 1 -name 'protoc-*' -type f -perm -u+x | head -n 1)
test -x "$PROTOC"
"$PROTOC" --version

echo "== Building aapt2 for $ABI (API $API)"
# 16 KiB-aligned segments: needed to execute on 16 KiB page-size kernels. build.py has no way to pass
# linker flags, so add them to its cmake command line.
sed -i "s|'-DCMAKE_BUILD_TYPE=Release'\]|'-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384']|" build.py
grep -q "max-page-size=16384" build.py

# The fmt version in this tag (10.2) trips over the stricter consteval checks of recent NDK clang
# releases. consteval only enables compile-time format string checks, so turn it off in the header.
FMT_CORE=src/fmtlib/include/fmt/core.h
sed -i -E 's/^(#\s*)define FMT_CONSTEVAL consteval/\1define FMT_CONSTEVAL/; s/^(#\s*)define FMT_HAS_CONSTEVAL/\1define FMT_HAS_CONSTEVAL_OFF/' "$FMT_CORE"
grep -n "FMT_CONSTEVAL\|FMT_HAS_CONSTEVAL" "$FMT_CORE" | head -n 8 | sed 's/^/fmt: /' || true

# libbase calls fdsan (Android 10, API 29) unconditionally. For older API levels, supply no-op stand-ins:
# fd ownership is then simply not tracked, which is how those Android versions behave anyway.
if [ "$API" -lt 29 ]; then
  cat > src/libbase/include/android-base/fdsan_compat.h <<'HDR'
#pragma once
#include <stdint.h>
#include <unistd.h>
static inline uint64_t android_fdsan_create_owner_tag(int, uint64_t) { return 0; }
static inline void android_fdsan_exchange_owner_tag(int, uint64_t, uint64_t) {}
static inline int android_fdsan_close_with_tag(int fd, uint64_t) { return close(fd); }
HDR
  sed -i '0,/^#pragma once/s//#pragma once\n#include <android-base\/fdsan_compat.h>/' src/libbase/include/android-base/unique_fd.h
  grep -n "fdsan_compat" src/libbase/include/android-base/unique_fd.h | sed 's/^/compat: /'
fi

if [ "$API" -lt 30 ]; then
  # liblog's own sources are compiled in, so the "introduced in API 30" markers on its newer
  # functions do not apply: the implementations ship inside the binary.
  find src/logging -path '*liblog/include/android/log.h' -exec sed -i 's/__INTRODUCED_IN(30)//g' {} +
fi

python3 build.py --ndk="$NDK" --abi="$ABI" --api="$API" --build="build/$ABI" \
  --protoc="$PROTOC" --target=aapt2 --job="$(nproc)"

BIN=$(find "build/$ABI" -type f -name aapt2 -perm -u+x | head -n 1)
test -n "$BIN"
STRIP=$(find "$NDK/toolchains/llvm/prebuilt" -name llvm-strip | head -n 1)
"$STRIP" -o "$OUT/libaapt2.so" "$BIN"
chmod 755 "$OUT/libaapt2.so"

READELF=$(find "$NDK/toolchains/llvm/prebuilt" -name llvm-readelf | head -n 1)
{
  echo "aosp_tag=$AOSP_TAG"
  echo "builder=$BUILDER_REPO@$BUILDER_SHA"
  echo "abi=$ABI"
  echo "api=$API"
  echo "ndk=$(grep -m1 Pkg.Revision "$NDK/source.properties" | cut -d= -f2 | tr -d ' ')"
  echo "sha256=$(sha256sum "$OUT/libaapt2.so" | cut -d' ' -f1)"
  echo "size=$(stat -c %s "$OUT/libaapt2.so")"
  echo "needed=$("$READELF" -d "$OUT/libaapt2.so" | grep NEEDED | sed 's/.*\[\(.*\)\]/\1/' | tr '\n' ' ')"
  echo "load_align=$("$READELF" -lW "$OUT/libaapt2.so" | awk '$1=="LOAD"{print $NF}' | sort -u | tr '\n' ' ')"
} | tee "$OUT/BUILDINFO.txt"
