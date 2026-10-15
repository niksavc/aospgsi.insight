#!/usr/bin/env bash
# End-to-end check of the "Fix for this Android version" flow on a running emulator (userdebug image,
# so `adb root` works): builds an old-target APK, lets the installed debug app patch it through its
# PatchService (Apktool + bundled aapt2 + Keystore signing), then checks the result on the host and
# installs it. usage: device-test-patch.sh <path-to-AOSP-GSI-Insight-debug.apk>
set -euo pipefail

APP_APK=${1:?path to the AOSP GSI Insight debug APK}
PKG=nks.aospgsi.insight
SDK_ROOT=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
BT=$(ls -d "$SDK_ROOT"/build-tools/* | sort -V | tail -n 1)
JAR=$(ls "$SDK_ROOT"/platforms/android-*/android.jar | sort -V | tail -n 1)
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
WORK=$(mktemp -d)
echo "device API $SDK, build-tools $BT"

echo "== Building an old-target fixture APK (target SDK 19)"
mkdir -p "$WORK/res/values"
cat > "$WORK/res/values/strings.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources><string name="app_name">Legacy</string></resources>
XML
cat > "$WORK/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.legacy">
  <uses-sdk android:minSdkVersion="16" android:targetSdkVersion="19"/>
  <permission android:name="com.example.legacy.PRIVATE" android:protectionLevel="signature"/>
  <permission android:name="org.shared.permission.PUSH" android:protectionLevel="signature"/>
  <application android:label="@string/app_name" android:hasCode="false">
    <provider android:name="com.example.legacy.Files" android:authorities="com.example.legacy.files;shared.authority" android:exported="false"/>
  </application>
</manifest>
XML
"$BT/aapt2" compile --dir "$WORK/res" -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$WORK/AndroidManifest.xml" -o "$WORK/legacy-unsigned.apk" "$WORK/res.zip"
keytool -genkeypair -alias fixture -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Fixture" \
  -keystore "$WORK/fixture.jks" -storepass password -keypass password >/dev/null 2>&1
"$BT/apksigner" sign --ks "$WORK/fixture.jks" --ks-pass pass:password --out "$WORK/legacy.apk" \
  "$WORK/legacy-unsigned.apk"
"$BT/aapt2" dump badging "$WORK/legacy.apk" | grep -E "^package:|^targetSdkVersion|^(min)?[sS]dkVersion"
"$BT/aapt2" dump badging "$WORK/legacy.apk" | grep -q "targetSdkVersion:'19'"

if [ "$SDK" -ge 34 ]; then
  echo "== Android 14+ must refuse the original"
  out=$(adb install "$WORK/legacy.apk" 2>&1 || true)
  echo "$out"
  echo "$out" | grep -q "DEPRECATED_SDK_VERSION"
fi

if [ "$SDK" -lt 34 ]; then
  # Install the original too: the clone must then coexist with it, which fails with
  # CONFLICTING_PROVIDER / DUPLICATE_PERMISSION if authorities or permissions are not renamed.
  echo "== Installing the original fixture"
  adb install "$WORK/legacy.apk"
fi

echo "== Installing AOSP GSI Insight"
adb install -r -g "$APP_APK"
adb root >/dev/null
adb wait-for-device
adb logcat -c

echo "== Staging the fixture in the app's cache"
adb push "$WORK/legacy.apk" /data/local/tmp/legacy.apk >/dev/null
# The app has not run yet, so its cache directory does not exist; create it owned by the app.
OWNER=$(adb shell ls -ld "/data/data/$PKG" | tr -d '\r' | awk '{print $3}')
echo "app owner: $OWNER"
adb shell "mkdir -p /data/data/$PKG/cache && cp /data/local/tmp/legacy.apk /data/data/$PKG/cache/patch-source.apk && chown -R $OWNER:$OWNER /data/data/$PKG/cache && chmod 771 /data/data/$PKG/cache && chmod 644 /data/data/$PKG/cache/patch-source.apk && restorecon -R /data/data/$PKG/cache"
adb shell ls -lZ "/data/data/$PKG/cache" || true

echo "== Starting the patch service"
if [ "$SDK" -ge 26 ]; then START=start-foreground-service; else START=startservice; fi
adb shell am $START -n "$PKG/nks.aospgsi.insight.app.PatchService" \
  --es source "/data/data/$PKG/cache/patch-source.apk" \
  --es package com.example.legacy \
  --ei targetSdk 24 \
  --es newPackage com.example.legacy.aospgsi

result=""
for i in $(seq 1 60); do
  result=$(adb logcat -d -s AospGsiPatch:I | grep -E "result=" || true)
  [ -n "$result" ] && break
  sleep 5
done
echo "--- patch service log"
adb logcat -d -s AospGsiPatch:I | cut -c1-300
if ! echo "$result" | grep -q "result=success"; then
  echo "patching did not succeed: $result"
  echo "--- warnings and errors from the whole device log (app related)"
  adb logcat -d -v time '*:W' | grep -iE "aospgsi|AndroidRuntime|FATAL|ForegroundService|Process: |Exception|apktool|aapt2" | cut -c1-300 | tail -n 60
  echo "--- process deaths (events buffer)"
adb logcat -b events -d | grep -E "am_crash|am_proc_died|am_kill|am_anr|am_proc_bound" | grep -i "$PKG" | tail -n 15 || true
echo "--- app services"
  adb shell dumpsys activity services "$PKG" | head -n 30
  exit 1
fi

echo "== Checking the patched APK"
adb pull "/data/data/$PKG/files/patched/com.example.legacy.apk" "$WORK/patched.apk" >/dev/null
"$BT/apksigner" verify --print-certs "$WORK/patched.apk" | head -n 5
"$BT/apksigner" verify --print-certs "$WORK/patched.apk" | grep -q "certificate DN: CN=AOSP GSI Insight, O=niksavc, C=RU"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -E "^package:|^targetSdkVersion|^(min)?[sS]dkVersion|^application-label"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -q "package: name='com.example.legacy.aospgsi'"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -q "targetSdkVersion:'24'"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -qE "(min)?[sS]dkVersion:'16'"

echo "== Installing the patched APK"
adb install "$WORK/patched.apk"
adb shell pm list packages | grep -q "com.example.legacy.aospgsi"
if [ "$SDK" -lt 34 ]; then
  adb shell pm list packages | grep -qx "package:com.example.legacy"
  echo "original and clone are installed side by side"
fi

echo "== Repair: an APK re-saved by an archiver (everything deflated, signing block gone)"
python3 - "$WORK/legacy.apk" "$WORK/damaged.apk" <<'PY'
import sys, zipfile
src, dst = sys.argv[1:3]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        zout.writestr(info.filename, zin.read(info.filename))
PY
adb shell "rm -rf /data/data/$PKG/files/patched"
adb push "$WORK/damaged.apk" /data/local/tmp/damaged.apk >/dev/null
adb shell "cp /data/local/tmp/damaged.apk /data/data/$PKG/cache/patch-source.apk && chown $OWNER:$OWNER /data/data/$PKG/cache/patch-source.apk && chmod 644 /data/data/$PKG/cache/patch-source.apk && restorecon /data/data/$PKG/cache/patch-source.apk"
# logcat -c does not reliably empty the buffer on every release, so count result lines instead.
before=$(adb logcat -d -s AospGsiPatch:I | grep -c "result=" || true)
adb shell am $START -n "$PKG/nks.aospgsi.insight.app.PatchService" \
  --es source "/data/data/$PKG/cache/patch-source.apk" \
  --es package com.example.legacy \
  --ez repairOnly true
result=""
for i in $(seq 1 24); do
  now=$(adb logcat -d -s AospGsiPatch:I | grep -c "result=" || true)
  if [ "$now" -gt "$before" ]; then
    result=$(adb logcat -d -s AospGsiPatch:I | grep -E "result=" | tail -n 1)
    break
  fi
  sleep 5
done
adb logcat -d -s AospGsiPatch:I | cut -c1-300
echo "$result" | grep -q "result=success" || { echo "repair did not succeed: $result"; exit 1; }
adb pull "/data/data/$PKG/files/patched/com.example.legacy.apk" "$WORK/repaired.apk" >/dev/null
"$BT/apksigner" verify --print-certs "$WORK/repaired.apk" | head -n 5
"$BT/apksigner" verify --print-certs "$WORK/repaired.apk" | grep -q "certificate DN: CN=AOSP GSI Insight, O=niksavc, C=RU"
"$BT/aapt2" dump badging "$WORK/repaired.apk" | grep -q "package: name='com.example.legacy'"
"$BT/aapt2" dump badging "$WORK/repaired.apk" | grep -q "targetSdkVersion:'19'"
if [ "$SDK" -lt 34 ]; then
  # The repaired file has the device key, so the fixture-signed original must go first.
  adb uninstall com.example.legacy >/dev/null
  adb install "$WORK/repaired.apk"
  adb shell pm list packages | grep -qx "package:com.example.legacy"
  echo "repaired APK installs"
else
  out=$(adb install "$WORK/repaired.apk" 2>&1 || true)
  echo "$out"
  echo "$out" | grep -q "DEPRECATED_SDK_VERSION"
fi
echo "device patch test passed"
