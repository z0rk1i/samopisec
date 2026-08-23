(ns app.chart-geom
  "Децимация полилиний для канвасов (используется app.grafana-series).
   Нормализация/масштабирование жили здесь для нативных карточек графиков —
   удалены вместе с ними (ADR-0023/0025).")

(def max-polyline-points
  "Ограничение числа точек полилинии — децимация на уровне подготовки данных,
  чтобы канвас не рисовал O(n) из 50k+ точек."
  400)

(defn decimate
  "Прореживает точки до ≤ max-points равномерным шагом, сохраняя первую и
   последнюю. Не более max-points — SVG/canvas из 50k+ точек тормозит."
  [points max-points]
  (let [n (count points)]
    (cond
      (or (zero? n) (<= max-points 0)) []
      (<= n max-points) points
      (= max-points 1) [(first points)]
      :else
      (let [step (/ (dec n) (dec max-points))]
        (mapv (fn [i] (nth points (int (* i step))))
              (range max-points))))))
