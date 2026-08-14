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
- [x] sideload-сборки (локально, без EAS): Android release APK (72M), iOS Release (57M + виджет)
- [x] handbook.md

## UI-полировка (коммит db8bdb9)
- [x] графики: респонсивные карточки, сетка, подписи осей, заливка под кривой
- [x] экран кнопок: счётчик «сегодня» в шапке + per-button сегодняшние нажатия
- [x] фикс: панели графиков → defui с props-map (устранён `[object Object] is not ISeqable`)
- [x] фикс: `:storage/load` reg-fx → reg-event-fx (данные не загружались при старте)

## Сессия 2026-08-14 — виджеты «только кнопки», фикс нав-бара
- [x] нижние кнопки выше системной навигации Android (react-native-safe-area-context, commit ef00d0c)
- [x] кнопка «Графики» на Android снова работает (панель не перекрывалась)
- [x] Android-виджет: только кнопки, без заголовка/счётчиков (commit 6dca4f1)
- [x] iOS-виджет: только кнопки, без заголовка/счётчиков (commit 7e57d24)
- [x] README.md (commit aa21529)
- [x] релизы пересобраны: Android APK 73M, iOS Release 57M + виджет

## Сессия 2026-08-14 (вечер) — статичный Android-виджет
- [x] Android-виджет: фиксированная сетка 2×3, по одной кнопке на каждый тип (widget_layout.xml + TapWidgetProvider)
- [x] Android-виджет не перерисовывается по тапу (onReceive пишет только дата-поинт, как iOS)
- [x] нажатие «Жми» в приложении не трогает виджет (уберён `:widget/refresh` из `:data/record`)
- [x] ADR-0006, release APK пересобран

## Сессия 2026-08-14 (вечер) — фикс графика + улучшения
- [x] кумулятивная кривая строилась сверху вниз — исправлена двойная нормализация Y (edfbfad)
- [x] «Жми» в приложении не перезаписывает конфиг и не трогает виджет (6da31c6)
- [x] storage устойчив к битым строкам/файлам (e30b702)
- [x] top-отступ из safe-area вместо хардкода 44 (4318bfc)
- [x] iOS виджет не пересчитывает таймлайн по тапу (ebe00f8)
- [x] Android виджет: мёртвый код убран, стабильные request-коды (616df08)
- [x] единый math/day-ms (1099183)
- [x] android-sdk-shim качает реальный NDK r27b (c010951)
- [x] ADR-0007

## Следующие шаги
- [ ] тест на физических устройствах (iPhone/Android)
- [ ] lock screen виджет (вторично по ADR-0001)
- [ ] разобраться с iOS app/widget desync (app читает Documents, виджет — app group; см. ADR-0005 / память #24)