(ns app.contract
  "Единый контракт данных между приложением и нативными виджетами (Kotlin/Swift).
   config.json:      {:buttons [{:id string :label string :color string}]}
   datapoints.jsonl: строка {:id string :button-id string :ts number (мс)}"
  (:require [clojure.string :as str]))

(def max-buttons 6)

(def ^:private hex-regex
  "Цвет вида #rrggbb (ровно 6 hex-цифр). Палитра виджетов даёт такие; всё, что
   короче/длиннее/с буквами — невалидно (hex->rgba в UI падает на subs)."
  #"^#[0-9a-fA-F]{6}$")

(defn valid-color?
  "Валидный цвет кнопки: строка вида #rrggbb."
  [c]
  (and (string? c) (boolean (re-matches hex-regex c))))

(defn config-button?
  "Валидная кнопка конфига: непустой id, непустой label и цвет вида #rrggbb."
  [b]
  (and (map? b)
       (string? (:id b))
       (not (str/blank? (:id b)))
       (string? (:label b))
       (not (str/blank? (:label b)))
       (valid-color? (:color b))))

(defn datapoint?
  "Валидный дата-поинт: id, button-id и положительный ts (мс)."
  [dp]
  (and (map? dp)
       (string? (:id dp))
       (string? (:button-id dp))
       (number? (:ts dp))
       (pos? (:ts dp))))

(defn normalize-config
  "Оставляет только валидные кнопки."
  [cfg]
  {:buttons (vec (filter config-button? (:buttons cfg)))})

(defn normalize-datapoints
  "Оставляет только валидные дата-поинты."
  [dps]
  (filterv datapoint? dps))