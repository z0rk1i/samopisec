(ns app.storage
  "Персистентность дата-поинтов и конфига кнопок через expo-file-system.
   Датaпоинты — JSONL (строка на событие), конфиг — JSON. iOS: общий App Group
   контейнер (виджет WidgetKit читает только оттуда); Android: document dir
   (= filesDir, оттуда же читает TapWidgetProvider)."
  (:require [clojure.string :as str]
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
  (make-file "datapoints.jsonl"))

(defn- dp-archive-file []
  (make-file "datapoints-archive.jsonl"))

(defn- cfg-file []
  (make-file "config.json"))

(defn- read-lines
  "Текст файла, разобранный как JSONL, либо [] (файла нет)."
  [f]
  (if (.-exists f)
    (-> (.text f) (.then jsonl/parse-jsonl))
    (js/Promise.resolve [])))

(defn read-datapoints
  "Возвращает Promise<[datapoint]>, отсортированные по времени.
   Читает основной файл + архив (старые поинты после компакции — чтобы диапазон
   «всё» и статистика оставались полными). Невалидные записи отбрасываются."
  []
  (let [main (dp-file)
        arch (dp-archive-file)]
    (-> (js/Promise.all #js [(read-lines main) (read-lines arch)])
        (.then (fn [[a b]]
                 (->> (concat a b)
                      contract/normalize-datapoints
                      (sort-by :ts)
                      vec))))))

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
   и логирует ошибки, не разрывая цепочку."
  [op]
  (set! write-queue
        (-> write-queue
            (.then (fn [] (op)))
            (.catch (fn [e] (report-error! "write" e)))))
  write-queue)

(defn append-datapoint!
  "Дописывает строку datapoint в JSONL через очередь записи
   (сериализует быстрые тапы, ловит ошибки)."
  [dp]
  (enqueue!
   (fn []
     (let [f (dp-file)]
       (when-not (.-exists f)
         (.create f))
       (.write f (str (js/JSON.stringify (clj->js dp)) "\n")
               #js {:append true})))))

(defn- line-id
  "id дата-поинта из строки JSONL, либо nil (битая строка)."
  [line]
  (try
    (:id (js->clj (js/JSON.parse line) :keywordize-keys true))
    (catch :default _ nil)))

(defn delete-datapoint!
  "Удаляет дата-поинт с данным id из JSONL (для undo). Идёт через write-queue —
   сериализуется с тапами (виджет пишет напрямую, но атомарным append, гонки
   чтение-перезапись тут нет — только между on-demand-операциями приложения).
   Возвращает Promise<boolean> — удалён ли поинт. Удаление по id, а не по позиции:
   последней строкой может быть нажатие с виджета, которое пользователь не делал."
  [id]
  (enqueue!
   (fn []
     (let [f (dp-file)]
       (if (.-exists f)
         (-> (.text f)
             (.then (fn [text]
                      (let [lines (->> (str/split-lines (or text ""))
                                       (filter seq)
                                       (vec))
                            kept (vec (remove #(= id (line-id %)) lines))]
                        (if (= (count lines) (count kept))
                          false
                          (do
                            (if (seq kept)
                              (.write f (str (str/join "\n" kept) "\n"))
                              (.delete f))
                            true))))))
         (js/Promise.resolve false))))))

(def ^:const compact-threshold
  "Порог числа строк datapoints.jsonl: при превышении запускается компакция —
   в основном файле остаются только retention-days последних дней, старшие точки
   переносятся в datapoints-archive.jsonl."
  50000)

(def ^:const retention-days
  "Сколько последних дней сырых поинтов хранится в основном файле."
  90)

(defn compact-datapoints!
  "Компакция datapoints.jsonl (см. compact-threshold/retention-days).
   Разделение поинтов на свежие/старые — чистый app.compact/split.
   Возвращает Promise, резолвится в число перенесённых в архив точек (0 = не было)."
  []
  (let [f (dp-file)]
    (if (.-exists f)
      (-> (.text f)
          (.then (fn [text]
                   (let [lines (->> (str/split-lines (or text ""))
                                    (filter seq)
                                    (vec))
                         n (count lines)]
                     (if (<= n compact-threshold)
                       0
                       (let [dps (jsonl/parse-jsonl (str/join "\n" lines))
                             cut (compact/cutoff-ms (js/Date.now) retention-days)
                             {:keys [kept dropped]} (compact/split dps cut)
                             dropped-count (count dropped)]
                         (when (pos? dropped-count)
                           (let [a (dp-archive-file)
                                 append-arch (fn []
                                               (-> (.text a)
                                                   (.then (fn [arch]
                                                            (.write a (str (or arch "")
                                                                            (str/join "\n" (map #(js/JSON.stringify (clj->js %)) dropped))
                                                                            "\n"))))
                                                   (.catch (fn [e]
                                                             (report-error! "compact archive" e)))))]
(if (.-exists a)
                               (append-arch)
                               (-> (.create a)
                                   (.then append-arch)
                                   (.catch (fn [e]
                                             (report-error! "compact archive create" e)))))))
                         (if (seq kept)
                           (.write f (str (str/join "\n" (map #(js/JSON.stringify (clj->js %)) kept)) "\n"))
                           (.delete f))
                         dropped-count)))))
          (.catch (fn [e] (report-error! "compact" e) 0)))
      0)))

(defn read-config
  "Возвращает Promise<config> (map {:buttons [..]}). Битый JSON -> пустой конфиг,
   невалидные кнопки отбрасываются."
  []
  (let [f (cfg-file)]
    (if (.-exists f)
      (-> (.text f)
          (.then jsonl/parse-config)
          (.then contract/normalize-config))
      (js/Promise.resolve {:buttons []}))))

(defn write-config!
  "Сохраняет конфиг кнопок через очередь записи."
  [cfg]
  (enqueue!
   (fn []
     (let [f (cfg-file)]
       (when-not (.-exists f)
         (.create f))
       (.write f (js/JSON.stringify (clj->js cfg)))))))

(defn new-id
  []
  (str (random-uuid)))