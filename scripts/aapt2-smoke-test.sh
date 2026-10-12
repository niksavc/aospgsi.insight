#!/usr/bin/env bash
# Runs a freshly built aapt2 on a connected Android device or emulator: version, compile, link and
# dump a tiny app against the platform android.jar. usage: aapt2-smoke-test.sh <path-to-aapt2>
set -euo pipefail

AAPT2=${1:?path to aapt2}
DEV=/data/local/tmp/aapt2-smoke
JAR=$(ls "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/platforms/android-*/android.jar | sort -V | tail -n 1)
echo "android.jar: $JAR"

WORK=$(mktemp -d)
mkdir -p "$WORK/res/values"
cat > "$WORK/res/values/strings.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources><string name="app_name">Smoke</string></resources>
XML
cat > "$WORK/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.smoke">
  <application android:label="@string/app_name"/>
</manifest>
XML

adb shell "rm -rf $DEV && mkdir -p $DEV/res"
adb push "$AAPT2" "$DEV/aapt2" >/dev/null
adb push "$JAR" "$DEV/android.jar" >/dev/null
adb push "$WORK/res/values/strings.xml" "$DEV/res/strings.xml" >/dev/null
adb push "$WORK/AndroidManifest.xml" "$DEV/AndroidManifest.xml" >/dev/null
adb shell "chmod 755 $DEV/aapt2"

run() { echo "\$ aapt2 $*"; adb shell "cd $DEV && ./aapt2 $*; echo exit=\$?"; }

version=$(run version)
echo "$version"
echo "$version" | grep -q "exit=0"

compile=$(run compile --dir res -o compiled.zip)
echo "$compile"
echo "$compile" | grep -q "exit=0"

link=$(run link -I android.jar --manifest AndroidManifest.xml -o out.apk compiled.zip)
echo "$link"
echo "$link" | grep -q "exit=0"

badging=$(run dump badging out.apk)
echo "$badging"
echo "$badging" | grep -q "package: name='com.example.smoke'"
echo "$badging" | grep -q "application-label:'Smoke'"
echo "aapt2 smoke test passed"
