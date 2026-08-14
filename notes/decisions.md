# Журнал решений (ADR)

## ADR-0001 — Стек и архитектура приложения
**Дата:** 2026-08-13
**Статус:** accepted

### Контекст
Нужно приложение с настраиваемыми кнопками на home screen (lock screen вторичен),
которое записывает data-point'ы (кнопка + время) и показывает графики кумулятивного
счётчика с производными. Стек выбран заранее: ClojureScript + React Native.

Ограничения платформ, выявленные при исследовании:
- iOS: интерактивные кнопки на **заблокированном** экране блокировки невозможны
  (Apple блокирует действия в виджетах до аутентификации). Рабочий вариант — интерактивный
  виджет рабочего стола (iOS 17+).
- Android: home screen виджет работает на всех версиях; lock screen виджеты вернулись
  в Android 16 (вторично для нас).
- Виджеты нельзя писать на JS/CLJS — нужен нативный код (Kotlin для Android, Swift для iOS).

### Решение
1. Приложение: Expo + shadow-cljs + UIx + re-frame (`create-uix-app --expo --re-frame`).
2. Графики: `react-native-svg` + полилинии, вся математика в CLJS.
3. Android виджет: нативный Kotlin `AppWidgetProvider`, данные — append-only файл
   `datapoints.jsonl` в `filesDir` (общий для виджета и приложения).
4. iOS виджет: `@bacons/apple-targets` (`create-target widget`) — Swift WidgetKit +
   App Intent, обмен данными через App Group (UserDefaults + `ExtensionStorage`).
5. Дистрибуция: личное приложение, sideload (эмулятор/симулятор, локальные сборки).

## ADR-0002 — iOS: сборка через xcodebuild напрямую
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
Phase 1 — первый запуск приложения на iOS-симуляторе. `expo run:ios` конфликтует
с уже запущенным Metro (`expo start` на 8081) и shadow-cljs watch (9630) из dev-сессии.
Дополнительно: prebuilt React-Core 0.86.2 в поде оказался без бинарника симуляторного
слайса (`ld: framework 'React' not found`) — download pod'а не подтянул debug-tarball.

### Решение
1. Не использовать `expo run:ios`, если Metro уже бежит — собирать `xcodebuild` напрямую
   с `-derivedDataPath /tmp/ios_dd` и ставить через `simctl install`/`launch`.
2. Починить prebuilt React-Core: скачать debug-tarball с Maven и распаковать его
   в `ios/Pods/React-Core-prebuilt/` (см. handbook).
3. Проверка UI без возможности смотреть скриншоты — OCR через Vision
   (`VNRecognizeTextRequest`), скриншот `simctl io booted screenshot`.

### Результат
Приложение собралось и запустилось на «iPhone 17 Pro» (iOS 26.5), UI рендерится
(экраны Кнопки/Графики), bundle отдаёт Metro (837 модулей).

## ADR-0003 — iOS виджет: WidgetKit + App Group
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
Phase 5 — интерактивный виджет рабочего стола для iOS (iOS 17+). Android-виджет уже
работает через нативный `AppWidgetProvider` + файлы в `filesDir`. Для iOS тот же паттерн
невозможен: виджет-расширение живёт в отдельном sandbox, общий доступ — только через
App Group. Заодно выяснилось, что официальный модуль `expo-widgets` (SDK 57) мог бы
упростить реализацию, но ADR-0001 зафиксировал `@bacons/apple-targets` + Swift.

### Решение
1. `targets/widget/` (Swift WidgetKit): `TimelineProvider` читает `config.json` +
   `datapoints.jsonl` из App Group контейнера, `systemSmall`/`systemMedium`.
2. Кнопки виджета — `Button(intent:)` с `TapButtonIntent` (AppIntent): дописывает
   datapoint в shared JSONL и перезагружает таймлайны.
3. App Group `group.com.z0rk1.samopisec`: app пишет через
   `fs/Paths.appleSharedContainers` (storage.cljs, fallback на Android — `Paths.document`),
   виджет читает через `FileManager.containerURL`.
4. Reload виджета после изменений в app — `ExtensionStorage.reloadWidget()` из
   `@bacons/apple-targets` (подключён через autolinking pod'а).
5. Xcode 26 SDK: конструктор `@Parameter` в AppIntent теперь требует
   `IntentParameter<String>` вместо простой строки — фабрика
   `TapButtonIntent.tap(buttonId:)`, собирающая параметр через `wrappedValue`.

### Результат
Виджет отображается на home screen симулятора: OCR показывает «Сегодня: 5»,
счётчики Чай/Кофе/Вода из shared-данных. `chronod` логирует reload
`extensionBundleIdentifier=com.z0rk1.samopisec.widget`.

## ADR-0004 — UI-полировка графиков + фиксы загрузки
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
После Phase 0–6 запрошена полировка UI/графиков. При редизайне вскрылись два реальных
бага: (1) панели графиков были `defn` с позиционными аргументами, но вызывались через
`($ panel {:map ...})` — uix трактует такую функцию как компонент с props-map,
внутри `(map second props)` падало `[object Object] is not ISeqable`; (2) `:storage/load`
был зарегистрирован `reg-fx`, но диспатчился `rf/dispatch` как событие — re-frame ругался
и данные не загружались при старте.

### Решение
1. Панели `cumulative-panel`/`rate-panel`/`accel-panel` — `defui`, destructure props-map.
2. `:storage/load` → `reg-event-fx`.
3. Графики: респонсивные карточки (`useWindowDimensions`), сетка 0/25/50/75/100%,
   подписи max/min, метки времени по X, заливка `Polygon` под кумулятивной кривой,
   фон `#f5f5f7`, компактные числа.
4. Экран кнопок: подписка `:today/counts` — бейджи сегодняшних нажатий per-button
   и «сегодня: N» в шапке.

### Результат
Коммит `db8bdb9`. Все три панели рендерятся с данными на симуляторе, счётчики
«сегодня: 1» на экране кнопок (данные реально загрузились после фикса).