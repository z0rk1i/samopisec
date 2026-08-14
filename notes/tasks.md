# Задачи

## Фаза 0 — Окружение
- [x] git init + .gitignore
- [x] Obsidian vault (`.obsidian/`, `notes/`)
- [x] CocoaPods 1.17.0 (понадобилась установка гемов nkf, bigdecimal для ruby 4.0), watchman
- [x] Android SDK (platform 36, build-tools 36, emulator, system-image arm64 API 36) + AVD `samopisec` (pixel_7)
- [x] Xcode 26.6, JDK 26 (brew openjdk@26) — env в `scripts/env.sh`

## Фаза 1 — Каркас CLJS
- [x] скаффолд `create-uix-app --expo`
- [x] hello-world на Android-эмуляторе (debug APK, UI рендерится: экраны Кнопки/Графики)
- [x] hello-world на iOS-симуляторе
- [x] dev-workflow: `shadow-cljs watch/release` + Expo (release собирается без ошибок)

## Фаза 2 — Данные и математика
- [x] `math.cljc`: кумулятивная кривая, бинирование, 1-я/2-я производные
- [x] cljs.test (7 тестов / 16 утверждений, все проходят)
- [x] `storage.cljs` + re-frame события (JSONL + конфиг, fx-обработчики)

## Фаза 3 — UI
- [x] экран настройки кнопок (`src/app/ui/config.cljs`)
- [x] экран графиков (`src/app/ui/charts.cljs`, react-native-svg)

## Фаза 4 — Android виджет
- [x] TapWidgetProvider (Kotlin)
- [x] WidgetBridge нативный модуль

## Фаза 5 — iOS виджет
- [x] targets/widget (Swift)
- [x] RecordTapIntent + ExtensionStorage
- [x] App Group `group.com.z0rk1.samopisec` — данные делятся между app и виджетом
- [x] проверка на симуляторе: виджет рендерится (Сегодня: N, счётчики кнопок)

## Фаза 6 — Сборка и документация
- [ ] sideload-сборки (EAS / локально)
- [ ] handbook.md