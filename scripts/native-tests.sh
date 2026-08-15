#!/usr/bin/env bash
# Нативные unit-тесты виджета: Swift (парсинг конфига) + Kotlin (JUnit, :app).
# Только :app:testDebugUnitTest — в :expo нет androidx.test в оффлайн-репозиториях.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== Swift: WidgetConfig =="
swiftc targets/widget/WidgetConfig.swift targets/widget/WidgetConfigTests.swift -o /tmp/samopisec-wc-test
/tmp/samopisec-wc-test

echo "== Kotlin: :app:testDebugUnitTest =="
. ./scripts/env.sh
(cd android && ./gradlew :app:testDebugUnitTest --console=plain | tail -3)