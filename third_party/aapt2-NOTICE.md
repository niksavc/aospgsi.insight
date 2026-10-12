# aapt2 for Android

`libaapt2.so` is built in CI by `scripts/build-aapt2.sh`; no binary is stored in this repository.

- Source: AOSP (`frameworks/base` tools/aapt2 and the libraries it links) at tag
  `platform-tools-35.0.2`, fetched from android.googlesource.com.
- Build recipe: CMake port [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools)
  (Apache-2.0), pinned to a commit, compiled with the Android NDK.
- Modifications: only build-time patches applied by the script: the protobuf CMake include path,
  fmt's `consteval` switched off, and — because the binary targets Android 7.0 (API 24) — small
  stand-ins for C library functions that older Android lacks (fdsan, `reallocarray`, system
  property callbacks) and the API markers on liblog's own newer functions removed. aapt2's own
  sources are not edited.
- Runs on Android 7.0 (API 24) or newer.

## Licenses of what is linked into the binary

| Component | License |
| --- | --- |
| AOSP aapt2, libandroidfw, libbase, libutils, libcutils, libziparchive, liblog, libincfs | Apache-2.0 |
| protobuf | BSD-3-Clause |
| BoringSSL | OpenSSL / ISC |
| expat | MIT |
| libpng | libpng license |
| PCRE2 | BSD-3-Clause |
| fmt | MIT |
| zlib (system library) | zlib |

Each component's full license text is in its AOSP repository at the pinned tag.
