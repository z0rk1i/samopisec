(ns panel-repro
  (:require [app.math :as math]))

(def W 350.0)
(def H 140.0)
(def pad 18.0)

(defn scale-x [t start end]
  (let [span (max 1.0 (- end start))]
    (+ pad (* (- W (* 2 pad)) (/ (- t start) span)))))

(defn panel [label points color]
  (if (empty? points)
    (str label ": нет данных")
    (let [ys (map :y points)
          maxy (apply max 0.0 ys)
          miny (apply min 0.0 ys)
          span (max 1.0 (- maxy miny))
          norm (fn [i]
                 [(:x (nth points i))
                  (- H pad (* (- H (* 2 pad))
                              (/ (- (:y (nth points i)) miny) span)))])]
      {:label label :maxy maxy :miny miny
       :first-norm (norm 0)
       :npoints (count points)})))

(defn run []
  (let [ts [1786693915485 1786694725336 1786694911760 1786695662128 1786695728931 1786696000000]
        end 1786696400000
        start (- end (* 24 3600000))
        bin (math/auto-bin-size (- end start))
        s (math/series ts start end bin)
        cum (:cumulative s)
        rates (:rate s)
        accel (:accel s)]
    (println "cum type:" (type cum) "count:" (count cum))
    (println "cum head:" (pr-str (first cum)))
    (println "cumulative-panel pts first:" (pr-str (first (map (fn [[t n]] {:x (scale-x t start end) :y n}) cum))))
    (println "cum ys:" (pr-str (take 3 (map second cum))))
    (println "cum max:" (try (apply max 0.0 (map second cum)) (catch :default e (str "ERR " e))))
    (println "rates type:" (type rates))
    (println "rate first:" (pr-str (first rates)))
    (println "rate-panel pts first:" (pr-str (first (map (fn [{:keys [t rate]}] {:x (scale-x t start end) :y rate}) rates))))
    (println "accel:" (pr-str (take 3 accel)))
    (println "--- panel call ---")
    (println (pr-str (panel "Накопленные нажатия" (map (fn [[t n]] {:x (scale-x t start end) :y n}) cum) "#1976d2")))
    (println "OK")))
