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

## Нюансы
- Экран блокировки iOS: интерактивные кнопки в виджетах заблокированы платформой
  (см. [[decisions]] ADR-0001).
- Нативные каталоги `android/` и `ios/` коммитим — они содержат код виджетов.
  НЕ запускать `expo prebuild --clean` после их правки.
- Java: для shadow-cljs использовать JDK 26 из brew (`export JAVA_HOME=$(/usr/libexec/java_home -v 26)` — вручную, путь brew openjdk@26).