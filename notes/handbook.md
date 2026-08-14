# Handbook — Samopisec

## Структура проекта
- `src/app/core.cljs` — точка входа, таб-бар, корневой компонент
- `src/app/db.cljs` — re-frame: db, события, подписки, селекторы серий
- `src/app/math.cljc` — чистая математика (бины, кумулятивная кривая, производные)
- `src/app/storage.cljs` — персистентность: `datapoints.jsonl` + `config.json` (expo-file-system)
- `src/app/ui/config.cljs` — экран настройки кнопок
- `src/app/ui/charts.cljs` — экран графиков (react-native-svg)
- `src/app/widget.cljs` — мост к нативному виджету (`WidgetBridge`, no-op на iOS)
- `android/.../TapWidgetProvider.kt` — Android виджет (AppWidgetProvider)
- `android/.../WidgetBridgeModule.kt` + `WidgetBridgePackage.kt` — нативный модуль
- `test/app/math_test.cljc` — тесты математики
- `test/panel_repro.cljs` / `series_repro.cljs` — node-репро для серий и панелей
- `app.json`, `android/`, `ios/` — нативные каталоги Expo (коммитим!)

## Команды
- `yarn dev` — Expo + `shadow-cljs watch app` в режиме слежения
- `npm run cljs:release` — production-сборка CLJS
- `clojure -M -e "(require 'app.math-test)(clojure.test/run-tests 'app.math-test)"` — тесты
- Перед любыми clojure/npm командами: `source scripts/env.sh`
- iOS: `npx expo start` (Metro на 8081) + `shadow-cljs watch app` (порт 9630) уже запущены из предыдущих сессий; запускать `xcodebuild` напрямую (`--no-bundler`), а НЕ `expo run:ios` (конфликтует с бегущим Metro):
  `xcodebuild -workspace ios/samopisec.xcworkspace -scheme samopisec -configuration Debug -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath /tmp/ios_dd build`
  затем `xcrun simctl install booted /tmp/ios_dd/Build/Products/Debug-iphonesimulator/samopisec.app` + `xcrun simctl launch booted com.z0rk1.samopisec`
- tmux требует явный сокет: `tmux -L samopisec`
- Скриншот симулятора → OCR для проверки UI: `xcrun simctl io booted screenshot /tmp/ios_shot.png` + `swift /tmp/ocr.swift` (Vision VNRecognizeTextRequest)

## Нюансы
- Экран блокировки iOS: интерактивные кнопки в виджетах заблокированы платформой
  (см. [[decisions]] ADR-0001).
- Нативные каталоги `android/` и `ios/` коммитим — они содержат код виджетов.
  НЕ запускать `expo prebuild --clean` после их правки.
- Java: для shadow-cljs использовать JDK 26 из brew (`export JAVA_HOME=$(/usr/libexec/java_home -v 26)` — вручную, путь brew openjdk@26).
- RN 0.86.2 prebuilt React-Core: если симуляторная сборка падает с `framework 'React' not found`
  и в `ios/Pods/React-Core-prebuilt/React.xcframework/ios-arm64_x86_64-simulator/React.framework/`
  нет бинарника `React` — скачать debug-tarball и распаковать:
  `curl -sL -o /tmp/rncore-debug.tar.gz https://repo1.maven.org/maven2/com/facebook/react/react-native-artifacts/0.86.2/react-native-artifacts-0.86.2-reactnative-core-debug.tar.gz`
  `tar -xzf /tmp/rncore-debug.tar.gz -C ios/Pods/React-Core-prebuilt/`