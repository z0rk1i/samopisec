(ns app.storage
  "Персистентность дата-поинтов и конфига кнопок через expo-file-system.
   Датaпоинты — JSONL (строка на событие), конфиг — JSON. Файлы в document dir приложения;
   в фазах 4/5 базовый путь сменится на общий (filesDir / App Group)."
  (:require [app.jsonl :as jsonl]
            [app.contract :as contract]
            ["expo-file-system" :as fs]))

(def ^:const app-group "group.com.z0rk1.samopisec")

(defn- base-dir
  "Общая директория данных. iOS: App Group контейнер (виджет читает оттуда же).
   Android: document dir (= filesDir, оттуда читает TapWidgetProvider)."
  []
  (or (get fs/Paths.appleSharedContainers app-group)
      fs/Paths.document))

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

(defn append-datapoint!
  "Синхронно дописывает строку datapoint в JSONL."
  [dp]
  (let [f (dp-file)]
    (when-not (.-exists f)
      (.create f))
    (.write f (str (js/JSON.stringify (clj->js dp)) "\n")
            #js {:append true})))

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
  "Синхронно сохраняет конфиг кнопок."
  [cfg]
  (let [f (cfg-file)]
    (when-not (.-exists f)
      (.create f))
    (.write f (js/JSON.stringify (clj->js cfg)))))

(defn new-id
  []
  (str (random-uuid)))