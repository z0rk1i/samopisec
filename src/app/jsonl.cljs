(ns app.jsonl
  "Чистые функции разбора JSONL и config.json без зависимостей от нативного FS.
   Используются storage.cljs и тестами."
  (:require [clojure.string :as str]))

(defn parse-jsonl
  "Разбирает JSONL-текст в вектор объектов, пропуская пустые и битые строки
   (частичная запись после сбоя)."
  [^string text]
  (->> (str/split-lines (or text ""))
       (filter seq)
       (keep (fn [line]
               (try
                 (js->clj (js/JSON.parse line) :keywordize-keys true)
                 (catch :default _ nil))))))

(defn parse-config
  "Разбирает текст config.json в map. Битый JSON -> {:buttons []}."
  [text]
  (try
    (-> text
        (js/JSON.parse)
        (js->clj :keywordize-keys true))
    (catch :default _ {:buttons []})))

(defn serialize-config
  "Сериализует конфиг (map {:buttons [..]}) в текст config.json.
   Чистая пара к parse-config для storage.cljs и тестов."
  [cfg]
  (js/JSON.stringify (clj->js cfg)))

(defn split-last
  "Делит JSONL-текст на {:lines [сырые строки без последней] :last последний
   распарсенный объект или nil}. Пустые строки отбрасываются; битая последняя
   строка -> :last nil. Используется undo: последний поинт файла — истинно
   последний тап (включая нажатия с виджета, которых нет в in-memory db)."
  [text]
  (let [lines (->> (str/split-lines (or text ""))
                   (filter seq)
                   (vec))
        last-line (peek lines)]
    {:lines (if (seq lines) (pop lines) [])
     :last (when last-line
             (try
               (js->clj (js/JSON.parse last-line) :keywordize-keys true)
               (catch :default _ nil)))}))