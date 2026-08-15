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

## Сессия 2026-08-14 (поздний вечер) — тесты, контракт, рефакторинг (ADR-0008)
- [x] A5: `:test`-сборка shadow-cljs + `npm run test` (node-test, 21 тестов / 75 утверждений)
- [x] A3: `app.jsonl` — чистый парсинг JSONL/config (устойчив к битым строкам) + тесты
- [x] A1: `app.contract` — контракт config.json/datapoints.jsonl (CLJS+Kotlin+Swift) + тесты
- [x] A4: `app.selectors` — селекторы серий/today + тесты; db.subs их используют
- [x] A2: `app.chart-geom` — нормализация точек + регресс-тест двойной нормализации
- [x] B1: лимит 6 кнопок (счётчик N/6, отключение добавления)
- [x] B2: экспорт конфига+данных через share-sheet
- [x] B3: подтверждение удаления кнопки (Alert)
- [x] B4: «сегодня» = календарный день (был скользящие 24ч)
- [x] C1: a11y-метки (кнопки виджета, палитра, действия)
- [x] C2: clj-kondo + `npm run lint` (0 warnings)
- [x] C3: `app.theme` — палитра light/dark (`useColorScheme`)
- [x] D1: `scripts/release.sh`
- [x] D2: удалены scratch-сборки panel-repro/series-repro
- [x] D3: README актуализирован
- [x] E1: очередь записи в storage (сериализация тапов, warn на ошибки)
- [x] ADR-0008

## Сессия 2026-08-15 — Android 16: виджет не грузился (exported + ActionException)
- [x] фикс 1: `TapWidgetProvider` в манифесте `android:exported="true"` (было false — лаунчер не видел провайдера)
- [x] фикс 2: `setString(id, "setContentDescription", ...)` → `setContentDescription(id, ...)` — setString падал ActionException при применении RemoteViews
- [x] release APK пересобран (73M), ADR-0009 и ADR-0010
- [x] верификация на эмуляторе Android 16: виджет добавлен, кнопки «Чай»/«Кофе» отрисованы без ошибок

## Роадмап улучшений (2026-08-15)

### P1 — Надёжность
- [x] iOS app/widget рассинхрон: `storage.cljs` фолбэчится на `Paths.document`,
      виджет читает App Group → разобрать `appleSharedContainers`, гарантировать запись
      в `group.com.z0rk1.samopisec` (память #24, ADR-0005)
      — код-фикс внедрён (ADR-0011): iOS всегда приоритет App Group + диагностика;
      осталось: настроить бесплатный Apple personal team для provisioning с App Group
      capability (на team-less симуляторных сборках runtime не резолвит контейнер)
- [x] тест на физических устройствах: пройден (Android 16, 2026-08-15) — виджет
      добавляется и тапается; iOS — отложено до provisioning (ADR-0011)
- [x] гонка записи `datapoints.jsonl` Android: виджет (Java appendText) vs приложение
      (expo-file-system) → сериализовать доступ — проверено: обе стороны одиночный
      атомарный append (O_APPEND), перемешивания нет

### P2 — Данные
- [x] чтение всего JSONL при старте/графиках → компакция: порог 50k строк, ретенция
      90 дней сырых поинтов, старшие — в datapoints-archive.jsonl (без потери данных)
- [x] мемоизация селекторов `:chart/series`, `:today/counts` — закрыто: на текущем
      масштабе (десятки тыс. точек ≈ 1-2 мс на тап) re-frame уже кеширует по db-identity

### P3 — Функциональность
- [x] undo последнего нажатия (случайные тапы на виджете) — буфер + кнопка в приложении
- [x] редактирование кнопок: label/цвет/порядок (`:config/update` есть, UI нет)
- [x] экран статистики: лучший день, серии, heatmap по часам, итоги per-button
- [x] lock screen виджет Android 16 (вторично по ADR-0001): доступен по умолчанию,
      onAppWidgetOptionsChanged re-render + крупный текст (WIDGET_CATEGORY_KEYGUARD)
- [x] iOS Live Activity / Lock Screen — закрыто (не нужно по решению пользователя)

### P4 — UX
- [x] i18n: словарь `app/i18n` (RU), `t`/`tf`-шаблоны; добавление языка — новая карта
- [x] онбординг / пустые состояния
- [x] эмодзи в label (уже работали), иконка приложения + adaptive + сплэш (тёмная, 2×3 кнопки)

### P5 — Инженерия
- [x] native-тесты контракта: Kotlin JUnit 6/6, Swift 9/9, `scripts/native-tests.sh`
- [x] валидация config.json в нативе: Kotlin/Swift пропускают кнопки без id/label
      (битая кнопка больше не роняет весь виджет в empty)
- [x] CI: `.github/workflows/ci.yml` — CLJS lint+тесты, Swift и Kotlin unit-тесты