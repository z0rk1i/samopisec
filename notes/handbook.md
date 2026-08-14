# Handbook — Samopisec

## Структура проекта
- `src/app/core.cljs` — точка входа, таб-бар, корневой компонент
- `src/app/db.cljs` — re-frame: db, события, подписки, селекторы серий
- `src/app/math.cljc` — чистая математика (бины, кумулятивная кривая, производные)
- `src/app/storage.cljs` — персистентность: `datapoints.jsonl` + `config.json` (expo-file-system)
- `src/app/ui/config.cljs` — экран настройки кнопок
- `src/app/ui/charts.cljs` — экран графиков (react-native-svg)
- `src/app/widget.cljs` — мост к нативному виджету (`WidgetBridge` на Android, `ExtensionStorage.reloadWidget` на iOS)
- `android/.../TapWidgetProvider.kt` — Android виджет (AppWidgetProvider)
- `android/.../WidgetBridgeModule.kt` + `WidgetBridgePackage.kt` — нативный модуль
- `targets/widget/` — iOS виджет (Swift WidgetKit): `widgets.swift` (timeline + view),
  `AppIntent.swift` (`TapButtonIntent`), `index.swift` (WidgetBundle)
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
- **Sideload-сборки (Phase 6)** — EAS НЕ используем (делает `prebuild --clean` и стирает виджет-таргеты). Только локально:
  - Android release APK: `source scripts/env.sh && clojure -M -m shadow.cljs.devtools.cli release app` → `cd android && ./gradlew assembleRelease` → `android/app/build/outputs/apk/release/app-release.apk` (~72M, подписан debug-ключом — ставится на любые устройства)
  - iOS Release: `source scripts/env.sh && xcodebuild -workspace ios/samopisec.xcworkspace -scheme samopisec -configuration Release -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath /tmp/ios_rel build` (JS bundle из `app/`, Metro не нужен) → `/tmp/ios_rel/Build/Products/Release-iphonesimulator/samopisec.app` (57M) + встроенный `SamopisecWidget.appex`
- iOS виджет: после правки `targets/widget/*.swift` пересобрать `npx expo prebuild -p ios` (добавляет target в pbxproj, но стирает `ios/Pods/` — затем `pod install` в `ios/`), дальше обычный `xcodebuild`. Appex-модуль: `SamopisecWidget.appex`, bundle `com.z0rk1.samopisec.widget`, kind `SamopisecWidget`.
- Проверка виджета на home screen: `xcrun simctl shutdown` → дописать widget-элемент в `data/Library/SpringBoard/IconState.plist` (элемент `elementType=widget`, `bundleIdentifier=com.z0rk1.samopisec.widget`, `widgetIdentifier=SamopisecWidget`, `gridSize=medium`) → `xcrun simctl boot` → скриншот + OCR (искать «Сегодня: N»). Данные виджета лежат в App Group контейнере `group.com.z0rk1.samopisec`.
- **UI-полировка графиков (коммит `db8bdb9`)**: `charts.cljs` — панели теперь `defui`-компоненты, destructure props MAP (НЕ позиционные аргументы): `($ cumulative-panel {:cum … :start … :end … :W …})`. Раньше панели были `defn` с `[cum start end W]` и вызов `($ panel {:map})` падал `[object Object] is not ISeqable`. Карточки респонсивные (`useWindowDimensions`), сетка 0/25/50/75/100%, подписи max/min, метки времени по X, заливка под кривой (Polygon fill-opacity), фон `#f5f5f7`.
- **Фикс `:storage/load` (в `db8bdb9`)**: был зарегистрирован как `reg-fx`, но диспатчился как событие (`rf/dispatch [:storage/load]`) — re-frame ругался «no event handler registered» и данные НЕ загружались при старте. Переведён на `reg-event-fx`; теперь счётчики «сегодня» на экране кнопок реально работают.
- **Подписка `:today/counts`** в `db.cljs` — сегодняшние нажатия по каждой кнопке; используется в `config.cljs` для бейджей.

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