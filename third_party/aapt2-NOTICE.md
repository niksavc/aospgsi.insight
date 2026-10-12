# aapt2 for Android

`libaapt2.so` is built in CI by `scripts/build-aapt2.sh`; no binary is stored in this repository.

- Source: AOSP (`frameworks/base` tools/aapt2 and the libraries it links) at tag
  `platform-tools-35.0.2`, fetched from android.googlesource.com.
- Build recipe: CMake port [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools)
  (Apache-2.0), pinned to a commit, compiled with the Android NDK.
- Modifications: only build-time patches applied by the script (protobuf CMake include path and
  fmt's `consteval` switched off); the aapt2 sources themselves are not edited.
- Requires Android 11 (API 30) or newer.

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
