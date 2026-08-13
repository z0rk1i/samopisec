# Задачи

## Фаза 0 — Окружение
- [x] git init + .gitignore
- [x] Obsidian vault (`.obsidian/`, `notes/`)
- [x] CocoaPods 1.17.0 (понадобилась установка гемов nkf, bigdecimal для ruby 4.0), watchman
- [x] Android SDK (platform 36, build-tools 36, emulator, system-image arm64 API 36) + AVD `samopisec` (pixel_7)
- [x] Xcode 26.6, JDK 26 (brew openjdk@26) — env в `scripts/env.sh`

## Фаза 1 — Каркас CLJS
- [ ] скаффолд `create-uix-app --expo`
- [ ] hello-world на Android-эмуляторе
- [ ] hello-world на iOS-симуляторе
- [ ] dev-workflow: `shadow-cljs watch` + Expo

## Фаза 2 — Данные и математика
- [ ] `math.cljs`: кумулятивная кривая, бинирование, 1-я/2-я производные
- [ ] cljs.test
- [ ] `storage.cljs` + re-frame события

## Фаза 3 — UI
- [ ] экран настройки кнопок
- [ ] экран графиков (react-native-svg)

## Фаза 4 — Android виджет
- [ ] TapWidgetProvider (Kotlin)
- [ ] WidgetBridge нативный модуль

## Фаза 5 — iOS виджет
- [ ] targets/widget (Swift)
- [ ] RecordTapIntent + ExtensionStorage

## Фаза 6 — Сборка и документация
- [ ] sideload-сборки (EAS / локально)
- [ ] handbook.md