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