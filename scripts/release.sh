#!/bin/sh
# Сборка релизного Android APK одной командой:
#   NDK-shim (создаёт /tmp/sdk с реальным NDK) -> CLJS release -> Gradle assembleRelease.
set -e
cd "$(dirname "$0")/.."

echo "==> android-sdk-shim"
./scripts/android-sdk-shim.sh

echo "==> env"
. ./scripts/env.sh

echo "==> cljs:release"
npm run cljs:release

echo "==> gradlew assembleRelease"
cd android
./gradlew assembleRelease
cd ..

APK="android/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
  echo "==> OK: $APK ($(du -h "$APK" | cut -f1))"
else
  echo "==> ERROR: APK не найден"
  exit 1
fi