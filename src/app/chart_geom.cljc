(ns app.chart-geom
  "Чистая геометрия графиков: нормализация Y и X в канвас-координаты.
   Нормализация выполняется ровно один раз — на вход нужно подавать СЫРЫЕ
   значения. Повторная нормализация инвертирует ось (см. ADR-0007).")

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