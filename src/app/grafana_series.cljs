(ns app.grafana-series
  "Готовые данные для offline-Grafana WebView. Всё считается в CLJS на базе
   app.math (единый источник правды производных, ADR-0023/0025), HTML —
   глупый рендерер полученного JSON (window.SAMOPISEC_SERIES).
   Формы кривых: [{:t мс :v число}], прорежены до ≤ chart-geom/max-polyline-points."
  (:require [app.chart-geom :as geom]
            [app.math :as math]))

(def fallback-colors
  "Цвета для кнопок, которых нет в конфиге (данные по удалённой кнопке)."
  ["#e53935" "#1e88e5" "#43a047" "#fb8c00" "#8e24aa" "#fdd835"])

(defn- decim
 [pts]
  (geom/decimate pts geom/max-polyline-points))

(defn effective-buttons
  "Кнопки для дашборда: из конфига, а если конфиг пуст — выведенные из данных
   (кнопка удалена, тапы остались) с фолбэк-цветами."
  [buttons dps]
  (if (seq buttons)
    buttons
    (->> dps
         (keep :button-id)
         distinct
         (map-indexed (fn [i id]
                        {:id id :label id
                         :color (nth fallback-colors i (peek fallback-colors))}))
         vec)))

(defn- per-hour
  "24 счётчика нажатий по локальным часам суток."
  [dps]
  (reduce (fn [acc dp]
            (update acc (.getHours (js/Date. (:ts dp))) inc))
          (vec (repeat 24 0))
          dps))

(defn- curves-per-button
  "Кривые одной кнопки {:id :label :color :cumulative :p1 :p2}. Окно и бины —
   глобальные (все кнопки выровнены по одной оси X)."
  [{:keys [id label color]} dps dt-hours bins]
  (let [ts (->> dps
                (filter #(= id (:button-id %)))
                (mapv :ts)
                sort
                vec)]
    (when (seq ts)
      (let [rates (math/tap-rate ts bins)
            smoothed (math/moving-average (mapv :rate rates) 5)
            accel (math/second-derivative smoothed dt-hours)]
        {:id id
         :label label
         :color color
         :cumulative (decim (mapv (fn [[t n]] {:t t :v n}) (math/cumulative-counts ts)))
         :p1 (decim (mapv (fn [{:keys [t rate]}] {:t t :v rate}) rates))
         :p2 (decim (mapv (fn [i] {:t (:t (nth rates i)) :v (nth accel i)})
                          (range (count accel))))}))))

(defn series-payload
  "Полный payload дашборда из нормализованных дата-поинтов и кнопок конфига:
   {:points N
    :window {:t0 :t1}
    :buttons [{:id :label :color}]
    :totals {id n}
    :per-hour [24]
    :recent [[id button-id ts] ...] — ≤100 последних, свежие сверху
    :curves [{:id :label :color
              :cumulative [{:t :v}]   — накопленная кривая
              :p1 [...]}               — Производная 1, нажатий/час
              :p2 [...]]}              — Производная 2, Δ нажатий/час²
   Пустые данные -> нулевой payload (панели покажут «нет данных»)."
  [dps buttons]
  (let [dps (vec dps)]
    (if (empty? dps)
      {:points 0
       :window {:t0 0 :t1 0}
       :buttons []
       :totals {}
       :per-hour (vec (repeat 24 0))
       :recent []
       :curves []}
      (let [sorted (vec (sort-by :ts dps))
            t0 (:ts (first sorted))
            t1 (max (:ts (peek sorted)) (+ t0 math/hour-ms))
            bin (math/auto-bin-size (- t1 t0))
            bins (math/range-bins t0 t1 bin)
            btns (effective-buttons buttons dps)]
        {:points (count dps)
         :window {:t0 t0 :t1 t1}
         :buttons (mapv #(select-keys % [:id :label :color]) btns)
         :totals (frequencies (keep :button-id dps))
         :per-hour (per-hour dps)
         :recent (->> sorted reverse (take 100)
                      (mapv (fn [{:keys [id button-id ts]}] [id button-id ts])))
         :curves (keep #(curves-per-button % dps (/ bin math/hour-ms) bins)
                       btns)}))))
