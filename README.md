# Samopisec

Трекер нажатий с виджетами на домашнем экране. Настраиваемые кнопки на home screen
записывают data-point'ы (кнопка + время), приложение показывает графики
кумулятивного счётчика и его производных.

Стек: **ClojureScript** (shadow-cljs) + **UIx** + **re-frame** на **React Native** (Expo),
виджеты — нативный код (**Kotlin** для Android, **Swift/WidgetKit** для iOS).

## Возможности

- Экран **Кнопки**: создание/удаление настраиваемых кнопок (название, цвет),
  счётчики нажатий «сегодня» per-кнопка.
- Экран **Графики**: накопленные нажатия, скорость и ускорение (react-native-svg),
  периоды «сегодня / 7д / 30д / всё», фильтр по кнопкам.
- **Android-виджет** (AppWidget): кнопки на домашнем экране, тап пишет data-point,
  приложение и виджет читают общий `datapoints.jsonl`.
- **iOS-виджет** (WidgetKit, iOS 17+): кнопки через App Intent, обмен данными через
  App Group `group.com.z0rk1.samopisec`.
- Данные — append-only JSONL (`datapoints.jsonl`) + `config.json` (expo-file-system).

## Требования

- JDK 17+ (для Android-сборок): `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`
- Android SDK: `export ANDROID_HOME=/tmp/sdk` (и `ANDROID_SDK_ROOT`)
- Xcode + CocoaPods для iOS
- Все переменные окружения собирает `source scripts/env.sh`

## Разработка

```shell
source scripts/env.sh
yarn install
yarn dev          # Expo (Metro :8081) + shadow-cljs watch app (:9630)
```

В отдельном терминале запустить приложение:

```shell
# Android (эмулятор в /tmp/avd)
cd android && ./gradlew installDebug

# iOS — симулятор «iPhone 17 Pro», собирать напрямую (не expo run:ios — конфликтует с Metro)
xcodebuild -workspace ios/samopisec.xcworkspace -scheme samopisec -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath /tmp/ios_dd build
xcrun simctl install booted /tmp/ios_dd/Build/Products/Debug-iphonesimulator/samopisec.app
xcrun simctl launch booted com.z0rk1.samopisec
```

## Сборки (sideload)

EAS не используется (делает `prebuild --clean` и стирает виджет-таргеты). Только локально.

**Android release APK:**
```shell
./scripts/release.sh   # NDK-shim → cljs:release → gradlew assembleRelease
# → android/app/build/outputs/apk/release/app-release.apk (~76M, подписан debug-ключом)
```

Пошагово (для отладки):
```shell
source scripts/env.sh
npm run cljs:release
cd android && ./gradlew assembleRelease
```

**iOS Release:**
```shell
source scripts/env.sh
npm run cljs:release
xcodebuild -workspace ios/samopisec.xcworkspace -scheme samopisec -configuration Release \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath /tmp/ios_rel build
# → /tmp/ios_rel/Build/Products/Release-iphonesimulator/samopisec.app (57M)
#   + встроенный SamopisecWidget.appex
```

## Тесты

```shell
npm run test   # shadow-cljs :test (node-test): 21 тестов / 75 утверждений
npm run lint   # clj-kondo: src + test
```

## Структура проекта

- `src/app/core.cljs` — точка входа, таб-бар (Кнопки / Графики), корневой компонент
- `src/app/db.cljs` — re-frame: db, события, подписки
- `src/app/math.cljc` — чистая математика (бины, кумулятивная кривая, производные)
- `src/app/selectors.cljs` — селекторы серий (окна диапазонов, today-счётчики)
- `src/app/chart-geom.cljc` — нормализация/масштабирование точек графика
- `src/app/contract.cljc` — контракт данных (config.json / datapoints.jsonl) — единый
  формат для CLJS + Kotlin + Swift
- `src/app/jsonl.cljs` — чистый парсинг JSONL / config (устойчив к битым строкам)
- `src/app/storage.cljs` — персистентность: `datapoints.jsonl` + `config.json`
- `src/app/theme.cljs` — палитра цветов (light/dark по системной схеме)
- `src/app/ui/config.cljs` — экран настройки кнопок
- `src/app/ui/charts.cljs` — экран графиков
- `src/app/widget.cljs` — мост к нативному виджету
- `android/.../TapWidgetProvider.kt` — Android-виджет (AppWidgetProvider)
- `android/.../WidgetBridgeModule.kt` — нативный модуль (refresh виджета после commit)
- `targets/widget/` — iOS-виджет: `widgets.swift` (timeline + view), `AppIntent.swift`,
  `index.swift` (WidgetBundle)
- `test/app/` — cljs-тесты: math, selectors, chart-geom, jsonl, contract
- `scripts/release.sh` — одна команда для Android release APK
- `notes/` — Obsidian vault проекта (решения, задачи, handbook)

## Виджеты

- Android: `AppWidgetProvider` + `WidgetBridge`, данные — `config.json` + `datapoints.jsonl`
  в `filesDir` (общий с приложением). Размер 2×2, виджет показывает только кнопки.
- iOS: `@bacons/apple-targets`, bundle `com.z0rk1.samopisec.widget`, kind `SamopisecWidget`,
  семьи `systemSmall`/`systemMedium`. App Group `group.com.z0rk1.samopisec`:
  приложение пишет через `fs/Paths.appleSharedContainers`, виджет читает через
  `FileManager.containerURL`. Кнопки — `Button(intent: TapButtonIntent.tap(id))`.
- Нативные каталоги `android/` и `ios/` закоммичены — после правки виджетов НЕ запускать
  `expo prebuild --clean`.

## Примечания

Документация проекта ведётся в Obsidian vault `notes/`:
- `notes/decisions.md` — журнал решений (ADR)
- `notes/tasks.md` — задачи
- `notes/handbook.md` — команды, структура, нюансы
