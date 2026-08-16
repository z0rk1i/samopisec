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

## ADR-0005 — Виджеты «только кнопки» + фикс нижней навигации
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
1. Expo 57 / RN 0.86 включили edge-to-edge (transparent `navigationBarColor`
   в android styles.xml): нижняя панель кнопок в `core.cljs` рисовалась под
   системной навигацией Android, из-за чего левая кнопка «Графики» была
   недоступна для тапа.
2. Виджеты (Android и iOS) отображали лишнюю информацию: заголовок «Сегодня: N»
   и счётчики по каждой кнопке. Задача — виджет показывает только сами кнопки.

### Решение
1. Установлен `react-native-safe-area-context` (~5.7.0) — нативный модуль,
   требует пересборки native на обеих платформах. Корень обёрнут в
   `SafeAreaProvider`, tab-bar берёт `useSafeAreaInsets` и добавляет
   `:padding-bottom (+ 8 (.-bottom insets))`. Коммит `ef00d0c`.
2. Android-виджет (коммит `6dca4f1`): из `TapWidgetProvider.kt` и layout'ов
   удалены counts/total/заголовок, `buttonView` без параметра counts; подпись
   кнопки укрупнена; пустая подсказка «Откройте приложение…» оставлена.
3. iOS-виджет (коммит `7e57d24`): из `widgets.swift` удалены заголовок,
   `ButtonInfo.count`, `total`, `todayCounts()`; таймлайн обновляется раз в час;
   подсказка оставлена.
4. Релизы пересобраны (Android APK 73M, iOS 57M + `SamopisecWidget.appex`).

### Результат
Android: панель кнопок выше нав-бара, «Графики» открывает charts (uiautomator).
Виджеты показывают только «Чай»/«Кофе» (Android — uiautomator, iOS — OCR
домашнего экрана). Тап по кнопке виджета пишет дата-поинт.

### Зафиксированный баг (отдельно)
На iOS приложение при пустом `appCodeSignEntitlements.appGroups` фолбэчится на
`fs/Paths.document` (свой Documents), а виджет читает App Group контейнер —
возможен рассинхрон данных между app и виджетом. Механика Expo-провайдера
не разобрана (см. память #24).
## ADR-0006 — Android-виджет: статичная сетка 2×3 без перерисовки по тапу
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
На Android количество кнопок в виджете менялось в зависимости от нажатий
(на iOS всё стабильно). Причина: `TapWidgetProvider.onReceive` после каждого
тапа пересобирал RemoteViews целиком (вложенные `addView` для рядов `widget_row`),
а нажатие «Жми» в приложении триггерило `:widget/refresh`. На разных лаунчерах
вложенные RemoteViews могли дублировать кнопки/перерисовываться.

### Решение
1. `widget_layout.xml`: вместо динамического `widget_grid` + рядов `widget_row`
   — фиксированная сетка 2×3 с ячейками `widget_slot_1..6`; каждая кнопка
   кладётся в свою ячейку ровно один раз (по типу).
2. `TapWidgetProvider.kt`: `onReceive` при тапе только пишет дата-поинт и НЕ
   пересобирает виджет (поведение как на iOS — таймлайн не меняется). Удалены
   `widget_row.xml` / `widget_row_full.xml` и константа `COLUMNS`.
3. `db.cljs`: `:data/record` больше не шлёт `:widget/refresh` (нажатие в
   приложении не трогает виджет); обновление виджета осталось только по
   `:config/commit`.

### Результат
Виджет показывает ровно по одной кнопке на каждый тип конфига, вид не меняется
от нажатий. Release APK пересобран.

## ADR-0007 — Фикс графика + пакет улучшений
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
Кумулятивная кривая на экране графиков строилась сверху вниз: `cumulative-panel`
преднормализовывал Y в канвас-координаты, а `chart-card`/`norm-points` нормализовал
повторно — двойная инверсия разворачивала ось (подписи осей показывали пиксельные
значения вместо количества). Заодно накопились мелкие недочёты.

### Решение (8 коммитов)
1. `edfbfad` — charts: `cumulative-panel` передаёт сырой счёт `:y n`, нормализацию
   делает `chart-card` (как в rate/accel-панелях). Кривая растёт снизу вверх,
   подписи осей — реальные количества.
2. `6da31c6` — config: «Жми» шлёт только `:data/record`, убран лишний
   `:config/commit` (не перезаписывался config.json и не обновлялся виджет).
3. `e30b702` — storage: битые строки JSONL пропускаются, битый config.json →
   пустой конфиг, ошибки чтения ловятся в `:storage/load`.
4. `4318bfc` — app shell: top-отступ из safe-area вместо хардкода 44.
5. `ebe00f8` — iOS виджет: тап не пересчитывает таймлайн (паритет с Android).
6. `616df08` — Android виджет: удалена мёртвая ветка `button == null`, request
   code PendingIntent = индекс слота (нет коллизий hash кода).
7. `1099183` — db: единый `math/day-ms` вместо дубликата.
8. `c010951` — `android-sdk-shim.sh`: качает настоящий NDK r27b (dl.google.com
   доступен) вместо заглушки; сборка не ломается после ребута.

### Результат
Кумулятивный график корректен; CLJS release и Kotlin compile проходят без ошибок.

## ADR-0008 — Тесты, контракт данных и рефакторинг кодовой базы
**Дата:** 2026-08-14
**Статус:** accepted

### Контекст
После фикса графика в кодовой базе остались не покрытые тестами модули и дубли:
графическая нормализация, селекторы, парсинг JSONL, а схема `config.json` /
`datapoints.jsonl` существовала только неявно в трёх реализациях (CLJS, Kotlin, Swift).
Дополнительно: конфиг не ограничивал число кнопок (виджет молча резал на 6), не было
экспорта данных, lint'а, тёмной темы, а тесты запускались вручную одной строкой.

### Решение (17 коммитов, A—E)
1. **A — тесты/контракт:** `:test`-сборка shadow-cljs (node-test) + `npm run test`
   (с `source env.sh`, JDK17); извлечены чистые модули `app.jsonl`, `app.contract`
   (предикаты контракта — единый формат для CLJS+Kotlin+Swift), `app.selectors`,
   `app.chart-geom` (с регрессионным тестом на двойную нормализацию Y). Итого 21 тест / 75 утверждений.
2. **B — UX:** лимит 6 кнопок в конфиге (счётчик N/6, отключение добавления);
   экспорт конфига+данных через share-sheet; подтверждение удаления кнопки;
   диапазон «сегодня» = календарный день (был скользящие 24ч).
3. **C — полировка:** a11y-метки (кнопки виджета, палитра цветов, действия);
   `clj-kondo` + `npm run lint` (исправлены unused require, интроспекция `rn/Share`);
   единая палитра `app.theme` (light/dark по `useColorScheme`) вместо хардкода цветов.
4. **D — DX:** `scripts/release.sh` (shim → cljs:release → assembleRelease);
   удалены scratch-сборки `panel-repro`/`series-repro`; README актуализирован
   («сегодня», npm test/lint, release.sh, новые модули).
5. **E — надёжность:** очередь записи в `storage.cljs` (сериализация быстрых
   тапов, `console.warn` на ошибки записи).

### Результат
`npm run lint` — 0 warnings; `npm run test` — 21/75 pass; `cljs:release` собирается.
Артефакты (APK/iOS) НЕ пересобраны — только код и инструментарий.

## ADR-0009 — Android-виджет не загружался на Android 16: `exported="true"` для receiver
**Дата:** 2026-08-15
**Статус:** accepted

### Контекст
На физическом устройстве с Android 16 виджет не появлялся/не грузился на домашнем
экране, хотя на эмуляторе (Android 16, targetSdk 36) он рендерился и проверялся
uiautomator'ом. Receiver `TapWidgetProvider` был объявлен `android:exported="false"`
с самого первого коммита (44a4a44). Виджет-провайдер должен быть exported: host
(лаунчер) — отдельное приложение, которому нужно обнаруживать провайдера и
связывать `appWidgetId`; не-exported receiver на реальных лаунчерах скрывается
из пикера виджетов / не биндится.

### Решение
1. `android/app/src/main/AndroidManifest.xml`: `TapWidgetProvider` → `android:exported="true"`.
2. Release APK пересобран (`./scripts/release.sh`, 73M), merged manifest подтверждает
   `exported="true"`.

### Результат
Виджет доступен для добавления на домашний экран Android 16.

## ADR-0010 — Android-виджет «Не удалось загрузить виджет»: `setContentDescription` через `setString` падает с ActionException
**Дата:** 2026-08-15
**Статус:** accepted

### Контекст
После фикса exported (ADR-0009) виджет стал появляться в пикере, но при добавлении
лаунчер показывал «Не удалось загрузить виджет». Строка a11y
`view.setString(R.id.widget_button_root, "setContentDescription", label)`
(добавлена в 8fa66e4) падает при применении RemoteViews: `ReflectionAction` со
строковым типом вызывает `Class.getMethod("setContentDescription", String.class)`,
а метод объявлен как `View.setContentDescription(CharSequence)` — точного
совпадения типов нет → `ActionException`, host показывает ошибку загрузки. Баг
не всплывал ранее: после 8fa66e4 APK не пересобирали, а проверенный виджет был
без этой строки.

### Решение
Заменить на штатный API `RemoteViews.setContentDescription(viewId, label)`
(использует тип CHAR_SEQUENCE → метод резолвится корректно). Изменение в
`TapWidgetProvider.kt:81`.

### Результат
Верифицировано на эмуляторе Android 16 (SDK 36, AVD samopisec): виджет добавлен,
`buildViews` с 2 кнопками («Чай»/«Кофе») применился без ActionException, обе
кнопки отрисованы (content-desc видны в accessibility-дереве). APK пересобран
(73M).

## ADR-0011 — iOS app/widget: платформозависимый base-dir и блокер App Group на team-less сборках
**Дата:** 2026-08-15
**Статус:** accepted (частично: код-фикс внедрён, build-блокер зафиксирован)

### Контекст
`storage.cljs` писал в `Paths.document`, если в `Paths.appleSharedContainers` не
было ключа App Group (`group.com.z0rk1.samopisec`). На iOS это означало
молчаливый рассинхрон: виджет WidgetKit читает только App Group контейнер, а
приложение падало в свой Documents (память #24, ADR-0005).

### Решение (код)
`base-dir` стал платформозависимым: на iOS приоритет — App Group контейнер, при
его недоступности пишем в Documents с `console.error` (рассинхрон больше не
молчаливый); на Android — `Paths.document` как раньше (его же читает
TapWidgetProvider). Добавлена `storage-location` (диагностика: платформа, base-dir,
резолвится ли App Group) и лог при старте в `core.init`.

### Находка: build-блокер App Group на team-less сборках
При сборке без Apple developer team (`CODE_SIGN_IDENTITY=-`, без
`DEVELOPMENT_TEAM`) xcodebuild 26.5 подписывает app и widget appex с ПУСТЫМИ
entitlements в подписи: шаг CodeSign использует `samopisec.app.xcent`,
сгенерированный из пустого профиля, а не `-Simulated.xcent` (в котором есть
`application-identifier` и app-group). Entitlements попадают только в секцию
`__TEXT,__entitlements` (через `LD_ENTITLEMENTS_SECTION`), но runtime
`containerURL(forSecurityApplicationGroupIdentifier:)` читает подпись → nil →
`appleSharedContainers` пуст → app пишет в Documents, виджет данных не видит.

Проверено (симулятор iPhone 17 Pro, iOS 26.5):
- дефолтная сборка: запускается, `containerURL` → nil;
- переподпись group-only (app + appex): запускается, `containerURL` → nil;
- переподпись с `application-identifier` (FAKETEAMID или голый bundle id):
  SpringBoard отклоняет запуск;
- unsigned: SpringBoard отклоняет запуск;
- переопределение `CODE_SIGN_STYLE=Manual`, `ENTITLEMENTS_ALLOWED=YES`,
  `ENTITLEMENTS_DESTINATION=signature`, `CODE_SIGNING_ALLOWED=NO` —
  подпись всё равно пустая/секция исчезает.

Т.е. на этом Xcode/симуляторе runtime-доступ к App Group возможен только при
подписи, где entitlements приходят из provisioning profile с capability App Group
(нужен team). Ранее (14 авг) виджет работал на симуляторе — вероятно, состояние
Xcode/симулятора изменилось (обновление тулчейна).

### Результат
Код-фикс внедрён и диагностика добавлена. iOS-виджет на team-less сборке на
симуляторе не сможет читать данные, пока в сборке нет provisioning с App Group
capability. Путь: настроить бесплатный Apple personal team (free provisioning
включает App Group для development) и подписывать с `DEVELOPMENT_TEAM`.

## ADR-0012 — Хранилище: единый писатель, атомарная замена, компакция по main-count
**Дата:** 2026-08-16
**Статус:** accepted

### Контекст
Аудит нашёл гонки и краш в storage:
- `compact-datapoints!` переписывал файл вне write-queue → мог пересечься с
  поставленным в очередь тапом и потерять его строку;
- `delete-datapoint!` резолвился в `true` до завершения записи;
- компакция вызывала `.then` на синхронный `.create` (void) → краш при первом
  создании архива;
- `:data/loaded` триггерил компакцию по `(count dps)`, куда входит и архив —
  после первой компакции каждая загрузка зря запускала компакцию;
- потерян обработчик `:config/loaded` (регрессия при правке `:data/loaded`) —
  сохранённые кнопки не восстанавливались.

### Решение
1. **Единый писатель:** `read-datapoints`, `read-config`, `compact-datapoints!`
   идут через `enqueue!` — компакция/чтение больше не обгоняют тапы.
2. **Атомарная замена:** `replace-atomic!` пишет во временный файл и делает
   `moveSync(overwrite)` поверх — читатели никогда не видят частично записанный
   файл. `delete` возвращает `true` только после успешной записи.
3. **Компакция по main-count:** критерий — строки ТОЛЬКО основного файла;
   архив не вызывает ложные компакции.
4. **Архив через append:** `.write a content {:append true}` (без read-modify),
   краш `.create→.then` устранён.
5. `:config/loaded` восстановлен.

### Остаточный риск (принят)
Виджет (Kotlin `appendText`/Swift `FileHandle`) дописывает в файл напрямую,
минуя очередь приложения. Окно «прочитал → переименовал» у `delete`/`compact`
остаётся — нажатие с виджета в этот момент может быть потеряно. Вероятность
крайне низкая (тап человека + редкая операция), файл при этом не портится.
Полное устранение требует общего lock-файла между процессами — избыточно для
однопользовательского приложения.

## ADR-0013 — Фиксы по итогам аудита: хвостовой бин, атомарный config, устаревший фильтр графика
**Дата:** 2026-08-16
**Статус:** accepted

### Контекст
Аудит кодовой базы (CLJS + нативные виджеты) нашёл три бага:
1. `math/range-bins` использовал `quot` — округление вниз. Для дневного окна
   [00:00, сейчас] последний частичный бин (например, 14:00–14:37 при 14:37)
   отбрасывался: тапы в хвосте диапазона пропадали из rate/accel, при этом
   кумулятивная кривая (фильтр [start, end)) их видела — графики расходились.
2. `write-config!` писал `config.json` in-place (`File.write` truncate+rewrite).
   Краш/убийство процесса в середине записи оставлял битый файл → следующий
   `parse-config` возвращал `{:buttons []}` → потеря всех кнопок. Это единственная
   durable-копия конфига, её же читают оба виджета. Датапоинты — append-only
   (безопасны), конфиг — нет.
3. `:config/remove` не сбрасывал `:chart/button-id`, если удаляли кнопку, на
   которую смотрит экран графиков — серии становились пустыми без причины.

### Решение
1. `range-bins` округляет число бинов вверх (`quot (+ range bin-size -1)
   bin-size`): последний частичный бин сохраняется (хвост попадает внутрь
   финального бина, чей `end` может выступать за `end-ms` — по докстрингу
   «бины покрывают [start, end]»). Регресс-тест: 14:37 → 15 бинов, тап в хвосте
   учитывается в rate. Без клипа `end` — не возникает нулевой длительности
   бина и деления на ноль в `tap-rate`.
2. `write-config!` переведён на `replace-atomic!` (tmp + `moveSync overwrite`) —
   тот же паттерн, что у `delete-datapoint!`/компакции. Вынесен чистый
   `jsonl/serialize-config` (пара к `parse-config`) + round-trip тест.
3. Новый чистый предикат `selectors/chart-after-button-remove`: если график смотрел
   на удаляемую кнопку, фильтр сбрасывается на `:all`; `:config/remove` его
   вызывает. Фолбэк внутри `selectors/series` НЕ добавлялся: свежая кнопка с
   нулём нажатий сегодня должна оставаться пустой серией, а не показывать все
   данные — различать «кнопки нет» и «0 нажатий» селектор без списка кнопок
   не может.

### Результат
`npm run lint` 0/0; CLJS-тесты 33/128 pass (добавлены range-bins-tail,
serialize-config round-trip, chart-after-button-remove); Swift 9/9 pass.
Правки не коммичены (по процессу — после ревью).

## ADR-0014 — Рефакторинг по итогам аудита: undo по файлу, app.clock/app.timeline, split db.cljs, cap Kotlin
**Дата:** 2026-08-16
**Статус:** accepted

### Контекст
Продолжение аудита (ADR-0013). Открытые проблемы:
- P5 (undo после тапа с виджета): `:data/undo` брал `(peek (:datapoints db))` —
  последний поинт in-memory. Тап с виджета пишет в файл напрямую, не трогая db;
  если он случился после последнего тапа в приложении и до завершения
  foreground-reload, undo удалял не тот поинт.
- Время (`js/Date.now`, `start-of-day`) размазано по db/selectors/ui — нет одного
  шва для тестов с фиксированным временем.
- Пайплайн серия→точки канваса (scale-x) дублировался в трёх панелях charts.cljs;
  в `accel-panel` была мёртвая ветка (`(if (seq rates) ...)`: accel непуст ⟺ rate
  непуст). `ranges` — `defonce` вместо `def`.
- `db.cljs` смешивал data+config+chart события/подписки.
- Kotlin `WidgetConfig.parseButtons` не ограничивал 6 кнопок (cap жил только в
  `buildViews`), Swift ограничивал — асимметрия контракта.
- `widgetCategory="home_screen"` только в манифесте — вопрос, не мёртвая ли
  KEYGUARD-ветка в `buildViews`.

### Решение
1. **Undo по файлу.** Новый `storage/delete-last-datapoint!` (через write-queue):
   читает хвост `datapoints.jsonl`, берёт ИСТИННО последнюю строку, удаляет её
   атомарно, возвращает поинт. `:data/undo` теперь вызывает fx `:storage/undo`,
   который диспатчит `:data/undone` с реальным id. Чистый хелпер
   `jsonl/split-last` покрыт тестом. Удалён мёртвый `:storage/delete-datapoint` fx
   и `storage/delete-datapoint!`/`line-id` (были только для undo по id). Закрыт
   P5 (кроме остаточного риска: виджет-тап в момент между чтением и записью —
   уже зафиксирован в ADR-0012).
2. **`app.clock`** — единый источник: `now-ms` (подменяемый через `set-now!`),
   `start-of-day`, `day-start-ms`. `js/Date.now` вычищен из db/selectors/ui/
   storage; `selectors/start-of-day`/`day-start-ms` — тонкие алиасы (тесты не
   ломаются).
3. **`app.timeline`** — один `points` (серия→точки канваса {x y}) для трёх
   панелей; `pad`/`H` перенесены в `chart-geom`. Панели в charts.cljs стали
   однострочниками, мёртвая ветка убрана, `ranges` — `def`.
4. **Split `db.cljs`** → `app.db` (default-db, init, :storage/load, экраны,
   ошибки, базовые/стат-подписки) + `app.events.data` + `app.events.config` +
   `app.events.chart`. `core.cljs` их подключает.
5. **Kotlin parity**: `WidgetConfig.MAX_BUTTONS=6` + cap в `parseButtons`;
   `TapWidgetProvider.MAX_BUTTONS` удалён (единый источник). Тест cap на 10.
6. **Lock screen Android 16 — НЕ баг.** По Google FAQ (2025-03): требования к
   lock screen виджетам отсутствуют, «all widgets are compatible»; категория
   `not_keyguard` (API 36) — только opt-out. KEYGUARD-ветка в `buildViews` —
   документированный адаптивный механизм (OPTION_APPWIDGET_HOST_CATEGORY).
   Изменений не требуется; остаётся проверка на реальном устройстве (в tasks).

### Результат
`npm run lint` 0/0; CLJS 38 тестов/161 утверждение pass; Kotlin :app 7/7
(WidgetConfigTest); Swift 9/9. Правки не коммичены.

## ADR-0015 — Багфикс: заголовки графиков пропадали + config.json уничтожался при записи
**Дата:** 2026-08-16
**Статус:** accepted

### Контекст
После установки собранного релиза пользователь сообщил два бага:
1. «Пропали названия графиков и фильтров в окне графиков».
2. «При удалении виджета удаляются все кнопки».

Оба воспроизведены на эмуляторе AVD samopisec (release APK) и устранены.

### Решение

**Баг 1 — затенение `t` в `ui/charts.cljs`.** Компоненты биндили тему в локальную
`t` (`(let [t (theme/use-theme)] ...)`), а i18n подключали `:refer [t tf]`. Внутри
компонентов `(t :charts/...)` вызывал ТЕМУ как функцию → `nil` → пустой текст.
Баг был латентным с самого начала (конфиг-экран использует квалифицированный
`(i18n/t ...)`, поэтому работал). Правка: `charts.cljs` переведён на
`[app.i18n :as i18n]` + квалифицированные `(i18n/t ...)`/`(i18n/tf ...)` во всех
компонентах (единый стиль с `config.cljs`/`stats.cljs`). Только `charts.cljs` —
другие экраны уже квалифицированы.

**Баг 2 — `replace-atomic!` уничтожал файл после `moveSync`.** native `File.move`
обновляет `uri` источника на путь назначения. Следующий за `moveSync` блок
`(when (.-exists tmp) (.delete tmp))` проверял и удалял УЖЕ ПЕРЕМЕЩЁННЫЙ файл
(config.json / datapoints.jsonl), а не временный → каждый коммит конфига
стирал кнопки. Баг был латентным (до ADR-0014 `replace-atomic!` использовался
только в компакции, которая при тестовых объёмах никогда не запускалась) и был
выпущен наружу переводом `write-config!` на атомарную замену (ADR-0013).
Диагноз подтверждён логом: `RA done; tmp.exists=true` после успешного moveSync.
Правка: блок очистки tmp ПОСЛЕ moveSync удалён; пре-очистка в начале (подбирает
tmp от сбойного moveSync) оставлена. Докстринг функции фиксирует правило,
чтобы очистку не вернули.

### Проверка
- Эмулятор (release APK): заголовок «Графики», фильтры диапазона, «Все», тумблеры
  «Скорость/Ускорение», empty-state графиков — отображаются.
- Эмулятор: удаление кнопки → config.json ПЕРЕЖИВАЕТ коммит (`{"buttons":[]}`),
  после рестарта приложения кнопки на месте (ранее файл исчезал → «0/6»).
- `npm run lint` 0/0; CLJS 38/161 pass; release build 0 warnings.

### Открыто
- Проверка на реальном устройстве (Android/iOS) — в tasks.
