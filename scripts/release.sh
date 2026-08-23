#!/bin/sh
# Сборка релизного Android APK одной командой (оффлайн, per-button графики, CSV):
#   shim (NDK+CMake) -> offline_html (Grafana) -> CLJS release -> pod install (webview) -> Gradle assembleRelease
# Использование: ./scripts/release.sh  или  ./scripts/release.sh --skip-tests
set -eu
cd "$(dirname "$0")/.."

SKIP_TESTS=0
if [ "${1:-}" = "--skip-tests" ]; then SKIP_TESTS=1; fi

echo "==> android-sdk-shim (NDK 27.1 + CMake 3.22.1)"
./scripts/android-sdk-shim.sh

echo "==> env"
. ./scripts/env.sh

echo "==> offline_html: генерация src/app/offline_html.cljs из assets/grafana-offline/index.html"
if [ -f "assets/grafana-offline/index.html" ]; then
  python3 -c "
import pathlib, json
html = pathlib.Path('assets/grafana-offline/index.html').read_text()
js = json.dumps(html)
cljs = f'(ns app.offline-html)\n\n(def html {js})\n'
pathlib.Path('src/app/offline_html.cljs').write_text(cljs)
print('offline_html.cljs обновлён', len(cljs))
"
else
  echo "WARN: assets/grafana-offline/index.html не найден — пропускаю"
fi

if [ ! -d "node_modules/react-native-webview" ]; then
  echo "==> npm install (webview)"
  npm install --silent
fi

# pod install для webview (iOS), не требует --clean (сохраняет виджеты)
if [ -d "ios" ] && command -v pod >/dev/null 2>&1; then
  echo "==> pod install (webview)"
  npx pod-install --silent || echo "WARN: pod install не удался"
fi

if [ "$SKIP_TESTS" -eq 0 ]; then
  echo "==> lint + test"
  npx clj-kondo --lint src test
  npm run test
else
  echo "==> SKIP lint/test (--skip-tests)"
fi

echo "==> cljs:release"
npm run cljs:release

echo "==> gradlew assembleRelease (arm64-only, R8, сжатые .so, offline Grafana)"
cd android
./gradlew assembleRelease --warning-mode all
cd ..

APK="android/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
  echo "==> OK: $APK ($(du -h "$APK" | cut -f1)) — CSV + Grafana offline + per-button"
  ls -lh "$APK"
else
  echo "==> ERROR: APK не найден"
  exit 1
fi
