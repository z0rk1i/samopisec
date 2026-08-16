(ns app.selectors
  "Чистые селекторы данных графиков и дневных счётчиков — без re-frame,
   тестируются отдельно."
  (:require [app.clock :as clock]
            [app.math :as math]
            [app.chart-geom :as geom]))

(defn- decimate-series
  "Децимация серий до ≤ max-polyline-points. rate/accel выровнены по индексу
   (accel[i] соответствует rate[i]), поэтому прореживаются ОДИНАКОВЫМ индексным
   паттерном — иначе кривые разъедутся по оси X."
  [{:keys [cumulative rate accel] :as series}]
  (let [idx (geom/decimate (vec (range (count rate))) geom/max-polyline-points)
        pick (fn [xs] (mapv (fn [i] (nth xs i)) idx))]
    (assoc series
           :cumulative (geom/decimate cumulative geom/max-polyline-points)
           :rate (pick rate)
           :accel (pick accel))))

(defn start-of-day
  "Метка начала текущего дня (локально), мс. Пара к clock/start-of-day."
  [now-ms]
  (clock/start-of-day now-ms))

(defn range-window
  "Окно [start end] для диапазона range-k, относительно t0 (мс)."
  [range-k t0]
  (case range-k
    :day   [(start-of-day t0) t0]
    :week  [(- t0 (* 7 math/day-ms)) t0]
    :month [(- t0 (* 30 math/day-ms)) t0]
    :all   [0 t0]))

(defn chart-after-button-remove
  "Chart после удаления кнопки removed-id: если график смотрел на неё, фильтр
   сбрасывается на :all (иначе серии были бы пустыми без причины), иначе chart
   без изменений. Чистая функция — используется :config/remove и тестами."
  [chart removed-id]
  (if (= removed-id (:button-id chart))
    (assoc chart :button-id :all)
    chart))

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
      (-> (math/series ts start end (math/auto-bin-size (- end start)))
          decimate-series
          (assoc :start start :end end)))))

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