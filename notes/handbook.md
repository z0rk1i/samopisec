# Handbook — Samopisec

## Структура проекта
- `src/app/core.cljs` — точка входа, таб-бар (Графики/Кнопки), постоянный маунт экранов
- `src/app/db.cljs` — re-frame: db, app-события, подписки (:datapoints, :buttons, :today/counts, :grafana/payload)
- `src/app/math.cljc` — чистая математика (бины, кумулятивная кривая, производные)
- `src/app/grafana_series.cljs` — payload офлайн-дашборда из app.math + decimation (Производная 1/2 по кнопкам)
- `src/app/selectors.cljs` — today-counts + merge-правила загрузки (merge-datapoints, resolve-loaded-buttons)
- `src/app/chart_geom.cljc` — децимация полилиний (decimate, max-polyline-points)
- `src/app/csv.cljs` — чистый парсинг/сериализация CSV `id,button_id,ts`
- `src/app/offline_html.cljs` — инлайн HTML Grafana оффлайн (генерируется release.sh из `assets/grafana-offline/index.html`, паритет стережёт тест)
- `src/app/storage_core.cljs` — чистые переходы файлов хранения (drain-plan/merged-read/undo-plan), node-тесты
- `src/app/storage.cljs` — FS-адаптер expo-file-system + write-queue: datapoints.csv (+spill виджетов, ADR-0024) + config.json
- `src/app/events/data.cljs` / `events/config.cljs` / `events/grafana.cljs` — re-frame события данных/конфига/payload
- `src/app/ui/config.cljs` — экран кнопок
- `src/app/ui/grafana.cljs` — Grafana в WebView: инжект кэша + postMessage-обновления без reload
- `src/app/widget.cljs` — мост к нативному виджету (`WidgetBridge` на Android, `ExtensionStorage.reloadWidget` на iOS)
- `assets/grafana-offline/index.html` — дашборд: Cumulative, Производная 1/2, Totals, Raw (Canvas, per-button)
- `android/.../TapWidgetProvider.kt` — Android виджет (тапы пишут в datapoints-spill.csv, ADR-0024)
- `targets/widget/` — iOS виджет (Swift WidgetKit): `widgets.swift` (spill), `AppIntent.swift`, `WidgetConfig.swift`
- `test/app/*` — 45 тестов: math, storage-core, selectors, grafana-series, chart-geom, csv, jsonl, contract, offline-html (паритет+postMessage)
- `grafana/` — docker-compose Grafana (для dev, не нужен для оффлайн), dashboards, uploader
- `scripts/` — сборка: `env.sh`, `android-sdk-shim.sh` (NDK+CMake), `release.sh` (offline_html + webview + R8), `sync-csv.sh`, `native-tests.sh`
- `app.json`, `android/`, `ios/` — нативные каталоги Expo (коммитим!)

## Команды
- `yarn dev` — Expo + `shadow-cljs watch app` в режиме слежения
- `npm run cljs:release` — production-сборка CLJS
- `npm run test` — `shadow-cljs :test` (45 тестов)
- `npm run lint` — `clj-kondo --lint src test` (0 warnings)
- Перед любыми clojure/npm командами: `source scripts/env.sh` (JDK 17, ANDROID_HOME=/tmp/sdk, NDK 27.1, CMake 3.22.1)
- `scripts/android-sdk-shim.sh` — создаёт `/tmp/sdk` (симлинки + NDK r27b 27.1.12297006 2.4Г + CMake 3.22.1 36М), устойчив к нехватке места (чистит `/tmp/gradle-home`, `/tmp/m2`), пробует `sdkmanager` затем `curl` fallback
- `scripts/env.sh` — `JAVA_HOME` 17, `ANDROID_HOME=/tmp/sdk`, `ANDROID_NDK_HOME`, `NPM_CONFIG_CACHE`, `GRADLE_USER_HOME`, проверка `platform-tools`/`cmake`
- `scripts/release.sh` — одна команда для релиза: `shim → offline_html (python3 json.dumps) → npm install → pod-install (webview) → lint/test → cljs:release → gradle assembleRelease` → `android/app/build/outputs/apk/release/app-release.apk` (~10M, arm64-only + R8 + сжатые .so/бандл, Grafana оффлайн + per-button). Флаг `--skip-tests` для быстрой сборки
- `scripts/sync-csv.sh` — `adb pull` CSV для эмулятора (`/tmp/samopisec.csv` → Grafana dev), для телефона не нужен (оффлайн, данные на устройстве)
- `scripts/native-tests.sh` — Swift `WidgetConfig` + Kotlin `:app:testDebugUnitTest`
- iOS: `npx expo start` (Metro на 8081) + `shadow-cljs watch app` (порт 9630) уже запущены; запускать `xcodebuild` напрямую (`--no-bundler`), а НЕ `expo run:ios`:
  `xcodebuild -workspace ios/samopisec.xcworkspace -scheme samopisec -configuration Debug -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath /tmp/ios_dd build` + `simctl install/launch`
- tmux требует явный сокет: `tmux -L samopisec`
- Скриншот симулятора → OCR: `xcrun simctl io booted screenshot /tmp/ios_shot.png` + `swift /tmp/ocr.swift`
- **Sideload-сборки** — EAS НЕ используем (делает `prebuild --clean` и стирает виджет-таргеты). Только локально:
  - Android release APK: `./scripts/release.sh` → `android/app/build/outputs/apk/release/app-release.apk` (~10M, arm64-only + R8 + сжатые .so/бандл, подписан debug-ключом). x86: `./gradlew assembleRelease -PreactNativeArchitectures=x86_64`
  - iOS Release: `xcodebuild -workspace ios/samopisec.xcworkspace -scheme samopisec -configuration Release -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath /tmp/ios_rel build` → `/tmp/ios_rel/.../samopisec.app` + `SamopisecWidget.appex`
- iOS виджет: после правки `targets/widget/*.swift` пересобрать `npx expo prebuild -p ios` (добавляет target, но стирает `ios/Pods/` — затем `pod install` в `ios/`), дальше `xcodebuild`. Appex: `SamopisecWidget.appex`, bundle `com.z0rk1.samopisec.widget`, kind `SamopisecWidget`.
- Проверка виджета: `xcrun simctl shutdown` → дописать widget в `IconState.plist` → `simctl boot` → скриншот + OCR. Данные в App Group `group.com.z0rk1.samopisec` (`datapoints.csv`).
- **Графики (2026-08-23):** вкладка «Графики» = офлайн-Grafana; серии (Cumulative, Производная 1, Производная 2) считает `app.grafana-series` на базе `app.math`, HTML — рендерер готового JSON. Обновления живой страницы — postMessage, без reload.
- **CSV + spill (ADR-0024):** `datapoints.csv` header `id,button_id,ts`; виджеты пишут тапы только в `datapoints-spill.csv`, приложение дренирует его в основной под очередью записи (write-queue); undo при пустом main снимает архив.

## Нюансы
- Экран блокировки iOS: интерактивные кнопки в виджетах заблокированы (ADR-0001).
- Нативные каталоги `android/` и `ios/` коммитим — НЕ запускать `expo prebuild --clean` после правки.
- Java: JDK 17 (`/opt/homebrew/opt/openjdk@17`), `source scripts/env.sh` перед сборкой.
- RN 0.86.2 prebuilt React-Core: если `framework 'React' not found` — скачать debug-tarball: `curl -sL -o /tmp/rncore-debug.tar.gz https://repo1.maven.org/maven2/com/facebook/react/react-native-artifacts/0.86.2/react-native-artifacts-0.86.2-reactnative-core-debug.tar.gz` + `tar -xzf ... -C ios/Pods/React-Core-prebuilt/`
- **Edge-to-edge:** `react-native-safe-area-context`, `SafeAreaProvider` + `useSafeAreaInsets`.
- **Виджеты «только кнопки»**: показывают только кнопки цветом, `widget_layout_v2` статичные слоты, ` TapWidgetProvider` CSV.
- **Оффлайн:** приложение и Grafana работают без сети — WebView инжектит `window.SAMOPISEC_SERIES` и принимает обновления через postMessage; `usesCleartextTraffic` оставлен для dev Grafana.
- **WebView:** `react-native-webview@13.15.0` (pod `react-native-webview`), `allowFileAccess true`, `mixedContentMode always`, `expo-asset` не нужен (HTML инлайн через `shadow.resource`/`offline_html.cljs`).
- **Диск `/tmp`:** NDK 2.4Г + CMake 36М + Gradle 1.6Г — при `BUILD FAILED [CXX1101]` или `CMake Error` чистить `rm -rf /tmp/gradle-home /tmp/m2 /tmp/sdk/ndk /tmp/sdk/cmake` и `scripts/android-sdk-shim.sh` заново.
