#!/usr/bin/env bash
# End-to-end check of the "Fix for this Android version" flow on a running emulator (userdebug image,
# so `adb root` works): builds an old-target APK, lets the installed debug app patch it through its
# PatchService (Apktool + bundled aapt2 + Keystore signing), then checks the result on the host and
# installs it. usage: device-test-patch.sh <path-to-BloxInsight-debug.apk>
set -euo pipefail

APP_APK=${1:?path to the BloxInsight debug APK}
PKG=com.bloxinsight.manager
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
  <application android:label="@string/app_name"/>
</manifest>
XML
"$BT/aapt2" compile --dir "$WORK/res" -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$WORK/AndroidManifest.xml" -o "$WORK/legacy-unsigned.apk" "$WORK/res.zip"
keytool -genkeypair -alias fixture -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Fixture" \
  -keystore "$WORK/fixture.jks" -storepass password -keypass password >/dev/null 2>&1
"$BT/apksigner" sign --ks "$WORK/fixture.jks" --ks-pass pass:password --out "$WORK/legacy.apk" \
  "$WORK/legacy-unsigned.apk"
"$BT/aapt2" dump badging "$WORK/legacy.apk" | grep -E "^package:|^targetSdkVersion|^sdkVersion"
"$BT/aapt2" dump badging "$WORK/legacy.apk" | grep -q "targetSdkVersion:'19'"

if [ "$SDK" -ge 34 ]; then
  echo "== Android 14+ must refuse the original"
  out=$(adb install "$WORK/legacy.apk" 2>&1 || true)
  echo "$out"
  echo "$out" | grep -q "DEPRECATED_SDK_VERSION"
fi

echo "== Installing BloxInsight"
adb install -r -g "$APP_APK"
adb root >/dev/null
adb wait-for-device
adb logcat -c

echo "== Staging the fixture in the app's cache"
adb push "$WORK/legacy.apk" /data/local/tmp/legacy.apk >/dev/null
adb shell "cp /data/local/tmp/legacy.apk /data/data/$PKG/cache/patch-source.apk && chmod 644 /data/data/$PKG/cache/patch-source.apk && restorecon -R /data/data/$PKG/cache"

echo "== Starting the patch service"
if [ "$SDK" -ge 26 ]; then START=start-foreground-service; else START=startservice; fi
adb shell am $START -n "$PKG/com.bloxinsight.app.PatchService" \
  --es source "/data/data/$PKG/cache/patch-source.apk" \
  --es package com.example.legacy \
  --ei targetSdk 24 \
  --es newPackage com.example.legacy.bloxinsight

result=""
for i in $(seq 1 120); do
  result=$(adb logcat -d -s BloxInsightPatch:I BloxInsightPatch:W | grep -E "result=" || true)
  [ -n "$result" ] && break
  sleep 5
done
echo "--- patch service log"
adb logcat -d -s BloxInsightPatch:I BloxInsightPatch:W | cut -c1-300
echo "$result" | grep -q "result=success" || { echo "patching did not succeed: $result"; exit 1; }

echo "== Checking the patched APK"
adb pull "/data/data/$PKG/files/patched/com.example.legacy.apk" "$WORK/patched.apk" >/dev/null
"$BT/apksigner" verify --print-certs "$WORK/patched.apk" | head -n 5
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -E "^package:|^targetSdkVersion|^sdkVersion|^application-label"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -q "package: name='com.example.legacy.bloxinsight'"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -q "targetSdkVersion:'24'"
"$BT/aapt2" dump badging "$WORK/patched.apk" | grep -q "sdkVersion:'16'"

echo "== Installing the patched APK"
adb install "$WORK/patched.apk"
adb shell pm list packages | grep -q "com.example.legacy.bloxinsight"
echo "device patch test passed"
