(ns app.chart-geom
  "Чистая геометрия графиков: нормализация Y и X в канвас-координаты.
   Нормализация выполняется ровно один раз — на вход нужно подавать СЫРЫЕ
   значения. Повторная нормализация инвертирует ось (см. ADR-0007).")

(def max-polyline-points
  "Ограничение числа точек полилинии SVG — децимация на уровне селектора
   (sub кэширует), чтобы chart-card не считал O(n) на каждый рендер."
  400)

(defn norm-points
  "Нормализует сырые точки {:x :y} в канвас-координаты панели высотой H.
   maxy -> верх (y=pad), miny -> низ (y=H-pad). Для положительных данных
   miny прижат к нулевой базовой линии (0-baseline).
   Возвращает {:pts [..] :maxy :miny}."
  [points H pad]
  (let [ys (map :y points)
        maxy (apply max 0.0 ys)
        miny (apply min 0.0 ys)
        span (max 1e-9 (- maxy miny))
        norm (fn [i]
               {:x (:x (nth points i))
                :y (- H pad (* (- H (* 2 pad)) (/ (- (:y (nth points i)) miny) span)))})]
    {:pts (mapv norm (range (count points)))
     :maxy maxy
     :miny miny}))

(defn scale-x
  "X канваса для времени t в [start end] при ширине W."
  [t start end W pad]
  (let [span (max 1.0 (- end start))]
    (+ pad (* (- W (* 2 pad)) (/ (- t start) span)))))

(defn decimate
  "Прореживает точки до ≤ max-points равномерным шагом, сохраняя первую и
   последнюю. Не более max-points — SVG из 50k+ точек тормозит на телефонах."
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