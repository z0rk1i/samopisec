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