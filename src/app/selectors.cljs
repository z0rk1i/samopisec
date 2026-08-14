(ns app.selectors
  "Чистые селекторы данных графиков и дневных счётчиков — без re-frame,
   тестируются отдельно."
  (:require [app.math :as math]))

(defn range-window
  "Окно [start end] для диапазона range-k, относительно t0 (мс)."
  [range-k t0]
  (case range-k
    :day   [(- t0 math/day-ms) t0]
    :week  [(- t0 (* 7 math/day-ms)) t0]
    :month [(- t0 (* 30 math/day-ms)) t0]
    :all   [0 t0]))

(defn series
  "Серии для выбора {:range k :button-id id} из datapoints.
   Возвращает {:cumulative [..] :rate [..] :accel [..] :start :end}."
  [chart datapoints t0]
  (let [{:keys [range button-id]} chart
        [start end] (range-window range t0)
        dps (if (= :all button-id)
              datapoints
              (filter #(= button-id (:button-id %)) datapoints))
        ts (mapv :ts dps)]
    (if (empty? ts)
      {:cumulative [] :rate [] :accel [] :start start :end end}
      (assoc (math/series ts start end (math/auto-bin-size (- end start)))
             :start start :end end))))

(defn start-of-day
  "Метка начала текущего дня (локально), мс."
  [now-ms]
  (let [d (js/Date. now-ms)]
    (.setHours d 0 0 0 0)
    (.getTime d)))

(defn today-counts
  "Счётчики нажатий за текущий календарный день: {:total n :by-button {id n}}."
  [datapoints now-ms]
  (let [start (start-of-day now-ms)
        dps (filter #(>= (:ts %) start) datapoints)]
    {:total (count dps)
     :by-button (frequencies (keep :button-id dps))}))