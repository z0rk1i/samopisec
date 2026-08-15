(ns app.storage
  "Персистентность дата-поинтов и конфига кнопок через expo-file-system.
   Датaпоинты — JSONL (строка на событие), конфиг — JSON. iOS: общий App Group
   контейнер (виджет WidgetKit читает только оттуда); Android: document dir
   (= filesDir, оттуда же читает TapWidgetProvider)."
  (:require [app.jsonl :as jsonl]
            [app.contract :as contract]
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

(defn- cfg-file []
  (make-file "config.json"))

(defn read-datapoints
  "Возвращает Promise<[datapoint]>. Поинты отсортированы по времени.
   Невалидные записи отбрасываются."
  []
  (let [f (dp-file)]
    (if (.-exists f)
      (-> (.text f)
          (.then jsonl/parse-jsonl)
          (.then contract/normalize-datapoints))
      (js/Promise.resolve []))))

(defonce ^:private write-queue (js/Promise.resolve nil))

(defn- enqueue!
  "Ставит асинхронную файловую операцию в очередь — гарантирует порядок записи
   и логирует ошибки, не разрывая цепочку."
  [op]
  (set! write-queue
        (-> write-queue
            (.then (fn [] (op)))
            (.catch (fn [e] (js/console.warn "storage write failed" e)))))
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