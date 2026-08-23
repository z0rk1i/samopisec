(ns app.selectors
  "Чистые селекторы данных статистики и дневных счётчиков — без re-frame,
   тестируются отдельно."
  (:require [app.clock :as clock]))

(defn start-of-day
  "Метка начала текущего дня (локально), мс. Пара к clock/start-of-day."
  [now-ms]
  (clock/start-of-day now-ms))

(defn today-counts
  "Счётчики нажатий за текущий календарный день: {:total n :by-button {id n}}."
  [datapoints now-ms]
  (let [start (start-of-day now-ms)
        dps (filter #(>= (:ts %) start) datapoints)]
    {:total (count dps)
     :by-button (frequencies (keep :button-id dps))}))

(defn per-button-totals
  "Итоговые счётчики за всё время: {:total n :by-button {id n}}."
  [datapoints]
  {:total (count datapoints)
   :by-button (frequencies (keep :button-id datapoints))})

(defn- day-start-ms
  "Начало календарного дня с отступом days от now-ms (0 = сегодня).
   Пара к clock/day-start-ms."
  [now-ms days]
  (clock/day-start-ms now-ms days))

(defn- taps-on-day?
  "Есть ли хоть одно нажатие в календарный день days назад от now-ms."
  [datapoints now-ms days]
  (let [s (day-start-ms now-ms days)
        e (day-start-ms now-ms (dec days))]
    (boolean (some #(and (<= s (:ts %)) (< (:ts %) e)) datapoints))))

(defn current-streak
  "Серия подряд идущих календарных дней с хотя бы одним нажатием.
   Если сегодня пусто, а вчера было — серия продолжается со вчера."
  [datapoints now-ms]
  (cond
    (taps-on-day? datapoints now-ms 0)
    (loop [n 1] (if (taps-on-day? datapoints now-ms n) (recur (inc n)) n))

    (taps-on-day? datapoints now-ms 1)
    (loop [n 1] (if (taps-on-day? datapoints now-ms (inc n)) (recur (inc n)) n))

    :else 0))

(defn best-day
  "Календарный день с максимальным числом нажатий:
   {:ts начало-дня :count n :date [y m d]} либо nil."
  [datapoints]
  (when (seq datapoints)
    (let [by-day (frequencies
                  (map (fn [{:keys [ts]}]
                         (let [d (js/Date. ts)]
                           (.setHours d 0 0 0 0)
                           (.getTime d)))
                       datapoints))
          [best-ts cnt] (apply max-key val by-day)
          d (js/Date. best-ts)]
      {:ts best-ts :count cnt
       :date [(.getFullYear d) (inc (.getMonth d)) (.getDate d)]})))

(defn per-hour-heatmap
  "Распределение нажатий по часам суток: вектор из 24 чисел (все данные)."
  [datapoints]
  (reduce (fn [acc {:keys [ts]}]
            (update acc (.getHours (js/Date. ts)) inc))
          (vec (repeat 24 0))
          datapoints))