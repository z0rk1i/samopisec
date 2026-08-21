(ns app.storage
  "Персистентность дата-поинтов и конфига кнопок через expo-file-system.
   Датaпоинты — CSV (id,button_id,ts), конфиг — JSON. iOS: общий App Group
   контейнер (виджет WidgetKit читает только оттуда); Android: document dir
   (= filesDir, оттуда же читает TapWidgetProvider). Миграция: при наличии
   datapoints.jsonl он конвертируется в CSV."
  (:require [clojure.string :as str]
            [app.clock :as clock]
            [app.csv :as csv]
            [app.jsonl :as jsonl]
            [app.contract :as contract]
            [app.compact :as compact]
            [re-frame.core :as rf]
            [react-native :as rn]
            ["expo-file-system" :as fs]))

(def ^:const app-group "group.com.z0rk1.samopisec")

(defn- shared-container
  "Directory App Group контейнера (iOS) либо nil. На Android контейнеров нет —
   appleSharedContainers пуст."
  []
  (get fs/Paths.appleSharedContainers app-group))

(defn- base-dir
  "Общая директория данных.
   iOS: App Group контейнер — виджет читает только оттуда, поэтому молчаливый
   фолбэк на Documents означал бы рассинхрон app/widget и логируется как ошибка.
   Android: document dir (= filesDir, оттуда читает TapWidgetProvider)."
  []
  (if (= (.-OS rn/Platform) "ios")
    (if-let [d (shared-container)]
      d
      (do
        (js/console.error
         (str "samopisec: App Group '" app-group "' не резолвится — entitlement "
              "не попал в сборку. Приложение пишет в Documents, виджет данных не увидит."))
        fs/Paths.document))
    fs/Paths.document))

(defn storage-location
  "Диагностика хранения: платформа, base-dir и резолвится ли App Group.
   Позволяет заметить рассинхрон app/widget (iOS должен писать в App Group)."
  []
  {:platform (.-OS rn/Platform)
   :app-group app-group
   :shared-container (some-> (shared-container) (.-uri))
   :base-dir (.-uri (base-dir))})

(defn- make-file
  [name]
  (new fs/File (base-dir) name))

(defn- dp-file []
  (make-file "datapoints.csv"))

(defn- dp-archive-file []
  (make-file "datapoints-archive.csv"))

(defn- legacy-dp-file []
  (make-file "datapoints.jsonl"))

(defn- legacy-dp-archive-file []
  (make-file "datapoints-archive.jsonl"))

(defn- cfg-file []
  (make-file "config.json"))

(defn- read-csv-file
  "Текст файла, разобранный как CSV, либо [] (файла нет)."
  [^fs/File f]
  (if (.-exists f)
    (-> (.text f) (.then csv/parse-csv))
    (js/Promise.resolve [])))

(defn- migrate-jsonl-to-csv!
  "Одноразовая миграция: если есть datapoints.jsonl а csv ещё нет — конвертирует."
  []
  (let [csv-f (dp-file)
        jsonl-f (legacy-dp-file)
        csv-a (dp-archive-file)
        jsonl-a (legacy-dp-archive-file)]
    (when (and (.-exists jsonl-f) (not (.-exists csv-f)))
      (let [text (.textSync jsonl-f)
            dps (jsonl/parse-jsonl text)]
        (when (seq dps)
          (let [content (csv/serialize-csv dps)
                tmp (make-file "datapoints.csv.tmp")]
            (when (.-exists tmp) (.delete tmp))
            (.create tmp)
            (.write tmp content)
            (.moveSync tmp csv-f #js {:overwrite true})))
        (when-not (seq dps)
          ;; пустой jsonl -> создаём csv с header чтобы виджеты append'или корректно
          (let [tmp (make-file "datapoints.csv.tmp")]
            (when (.-exists tmp) (.delete tmp))
            (.create tmp)
            (.write tmp (str csv/header "\n"))
            (.moveSync tmp csv-f #js {:overwrite true})))
        (try (.delete jsonl-f) (catch :default _ nil))
        (js/console.log "samopisec: миграция datapoints.jsonl -> datapoints.csv")))
    (when (and (.-exists jsonl-a) (not (.-exists csv-a)))
      (let [text (.textSync jsonl-a)
            dps (jsonl/parse-jsonl text)]
        (when (seq dps)
          (let [content (csv/serialize-csv dps)
                tmp (make-file "datapoints-archive.csv.tmp")]
            (when (.-exists tmp) (.delete tmp))
            (.create tmp)
            (.write tmp content)
            (.moveSync tmp csv-a #js {:overwrite true})))
        (try (.delete jsonl-a) (catch :default _ nil))
        (js/console.log "samopisec: миграция datapoints-archive.jsonl -> datapoints-archive.csv")))))

(defonce ^:private write-queue (js/Promise.resolve nil))

(defn report-error!
  "Единая точка репорта ошибок storage: console.warn + событие :storage/error,
   которое UI показывает пользователю (баннер). Не бросает — ошибка хранения
   не должна ронять очередь или UI."
  [where e]
  (js/console.warn (str "samopisec storage " where " failed") e)
  (rf/dispatch [:storage/error (str (when where (str where ": ")) (or (.-message e) (str e)))]))

(defn- enqueue!
  "Ставит асинхронную файловую операцию в очередь — гарантирует порядок записи
   и логирует ошибки, не разрывая цепочку. Ошибки op глотаются (report-error!),
   очередь продолжает работать."
  [op]
  (set! write-queue
        (-> write-queue
            (.then (fn [] (op)))
            (.catch (fn [e] (report-error! "write" e)))))
  write-queue)

(defn- replace-atomic!
  "Атомарно заменяет содержимое f на content: пишет во временный файл в той же
   директории и переименовывает поверх (moveSync overwrite). Читатели никогда не
   видят частично записанный файл. Окно для гонки с виджетом (он дописывает в
   конец напрямую) остаётся, но файл не портится — см. ADR.

   ВНИМАНИЕ: после успешного moveSync НЕЛЬЗЯ удалять tmp — native File при move
   меняет свой uri на файл-назначение, поэтому `(.delete tmp)` (или проверка
   `(.-exists tmp)`) удалит УЖЕ ПЕРЕМЕЩЁННЫЙ файл (config.json/datapoints.jsonl),
   а не временный. Сбойный moveSync оставляет tmp — его подберёт следующий
   вызов через блок `(when (.-exists tmp) (.delete tmp))` в начале."
  [^fs/File f ^string content]
  (let [tmp (make-file (str (.-name f) ".tmp"))]
    (when (.-exists tmp)
      (.delete tmp))
    (.create tmp)
    (.write tmp content)
    (.moveSync tmp f #js {:overwrite true})))

(defn read-datapoints
  "Возвращает Promise<{:dps [datapoint] :main-count n}>.
   dps — отсортированные по времени поинты (основной файл + архив, чтобы
   диапазон «всё» и статистика оставались полными). main-count — число строк
   только основного файла: критерий компакции, не искажённый архивом.
   Читает через write-queue — снимок после всех поставленных записей.
   Невалидные записи отбрасываются. Выполняет миграцию jsonl->csv при первом вызове."
  []
  (enqueue!
   (fn []
     (try (migrate-jsonl-to-csv!) (catch :default e (report-error! "migrate" e)))
     (let [main (dp-file)
           arch (dp-archive-file)]
       (-> (js/Promise.all #js [(read-csv-file main) (read-csv-file arch)])
           (.then (fn [[a b]]
                    {:dps (->> (concat a b)
                               contract/normalize-datapoints
                               (sort-by :ts)
                               vec)
                     :main-count (count a)}))
           (.catch (fn [e]
                     (report-error! "read-datapoints" e)
                     {:dps [] :main-count 0})))))))

(defn append-datapoint!
  "Дописывает строку datapoint в CSV через очередь записи
   (сериализует быстрые тапы, ловит ошибки). Header пишется при создании файла."
  [dp]
  (enqueue!
   (fn []
     (let [f (dp-file)]
       (when-not (.-exists f)
         (.create f)
         (.write f (str csv/header "\n")))
       (.write f (str (csv/serialize-row dp) "\n")
               #js {:append true})))))

(defn delete-last-datapoint!
  "Удаляет ПОСЛЕДНИЙ дата-поинт из CSV (для undo). Читает хвост файла через
   write-queue — это истинно последний поинт, включая тапы с виджета, которых
   нет в in-memory db (db не знает о них до перезагрузки). Удаление по последней
   строке, а не по последнему поинту db — закрывает гонку undo после тапа с
   виджета. Возвращает Promise<dp-or-nil> (nil — файл пуст или последняя строка
   битая; в этом случае файл не трогается)."
  []
  (enqueue!
   (fn []
     (let [f (dp-file)]
       (if (.-exists f)
         (-> (.text f)
             (.then (fn [text]
                      (let [{:keys [lines last]} (csv/split-last text)]
                        (when last
                          (if (seq lines)
                            ;; lines уже содержит header если был, иначе без
                            (replace-atomic! f (str (str/join "\n" lines) "\n"))
                            (.delete f))
                          last)))))
         (js/Promise.resolve nil))))))

(def ^:const compact-threshold
  "Порог числа строк datapoints.csv: при превышении запускается компакция —
   в основном файле остаются только retention-days последних дней, старшие точки
   переносятся в datapoints-archive.csv."
  50000)

(def ^:const retention-days
  "Сколько последних дней сырых поинтов хранится в основном файле."
  90)

(defn compact-datapoints!
  "Компакция datapoints.csv (см. compact-threshold/retention-days).
   Разделение поинтов на свежие/старые — чистый app.compact/split.
   Выполняется через write-queue (единый писатель), запись — атомарной заменой.
   Возвращает Promise, резолвится в число перенесённых в архив точек (0 = не было)."
  []
  (enqueue!
   (fn []
     (let [f (dp-file)]
       (if (.-exists f)
         (-> (.text f)
             (.then (fn [text]
                      (let [dps (csv/parse-csv text)
                            n (count dps)]
                        (if (<= n compact-threshold)
                          0
                          (let [cut (compact/cutoff-ms (clock/now-ms) retention-days)
                                {:keys [kept dropped]} (compact/split dps cut)
                                dropped-count (count dropped)]
                            (when (pos? dropped-count)
                              (let [a (dp-archive-file)
                                    has-header (when (.-exists a)
                                                 (try
                                                   (let [t (.textSync a)]
                                                     (csv/has-header? t))
                                                   (catch :default _ false)))]
                                (when-not (.-exists a)
                                  (.create a)
                                  (.write a (str csv/header "\n")))
                                ;; если архив существует но без header (миграция) — починить
                                (when (and (.-exists a) (not has-header))
                                  (let [old (.textSync a)
                                        fixed (str csv/header "\n" old)]
                                    (replace-atomic! a fixed)))
                                (.write a (csv/serialize-rows dropped) #js {:append true})))
                            (if (seq kept)
                              (replace-atomic! f (csv/serialize-csv kept))
                              (.delete f))
                            dropped-count)))))
             (.catch (fn [e] (report-error! "compact" e) 0)))
         0)))))

(defn read-config
  "Возвращает Promise<config> (map {:buttons [..]}). Битый JSON -> пустой конфиг,
   невалидные кнопки отбрасываются. Читает через write-queue — свежий снимок
   после любых поставленных записей."
  []
  (enqueue!
   (fn []
     (let [f (cfg-file)]
       (if (.-exists f)
         (-> (.text f)
             (.then jsonl/parse-config)
             (.then contract/normalize-config))
         (js/Promise.resolve {:buttons []}))))))

(defn write-config!
  "Сохраняет конфиг кнопок через очередь записи. Атомарная замена (tmp +
   moveSync): краш в середине in-place записи не оставит битый config.json —
   единственную durable-копию кнопок, которую читают и приложение, и виджеты."
  [cfg]
  (enqueue!
   (fn []
     (replace-atomic! (cfg-file) (jsonl/serialize-config cfg)))))

(defn new-id
  []
  (str (random-uuid)))