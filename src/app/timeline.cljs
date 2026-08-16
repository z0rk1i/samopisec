(ns app.timeline
  "Чистый конвейер серия → точки канваса для панелей графиков.
   selectors/series уже строит сырые серии (bins + cumulative + decimation);
   здесь — единственный способ превратить подсерию в точки {x y} для ширины W.
   Убирает дублирование scale-x из трёх панелей charts.cljs."
  (:require [app.chart-geom :as geom]))

(defn points
  "Точки канваса {x y} для подсерии k из series (:cumulative | :rate | :accel).
   x = scale-x(t, start, end, W, pad); y — СЫРОЕ значение (нормализует
   norm-points в chart-card). Для :accel x берётся из rate[i].t — rate и accel
   выровнены по индексу (см. selectors/decimate-series)."
  [{:keys [series k start end W]}]
  (let [{:keys [cumulative rate accel]} series
        scale #(geom/scale-x % start end W geom/pad)]
    (case k
      :cumulative (mapv (fn [[t n]] {:x (scale t) :y n}) cumulative)
      :rate       (mapv (fn [{:keys [t rate]}] {:x (scale t) :y rate}) rate)
      :accel      (mapv (fn [i a] {:x (scale (get-in rate [i :t])) :y a})
                        (range (count accel)) accel))))