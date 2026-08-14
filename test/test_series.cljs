(ns test-series
  (:require [app.math :as math]))

(defn run []
  (let [ts [1786693915485 1786694725336 1786694911760 1786695662128 1786695728931 1786695760217]
        start (- 1786695760217 (* 24 3600000))
        end 1786695760217
        bin (math/auto-bin-size (- end start))
        s (math/series ts start end bin)]
    (println "cumulative type:" (type (:cumulative s)))
    (println "rate type:" (type (:rate s)))
    (println "accel type:" (type (:accel s)))
    (println "cumulative sample:" (take 3 (:cumulative s)))
    (println "rate sample:" (take 2 (:rate s)))
    (println "accel sample:" (take 3 (:accel s)))
    (println "empty? cumulative:" (empty? (:cumulative s)))
    (println "empty? rate:" (empty? (:rate s)))
    (println "empty? accel:" (empty? (:accel s)))))
