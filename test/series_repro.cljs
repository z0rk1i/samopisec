(ns series-repro
  (:require [app.math :as math]))

(defn run []
  (let [ts [1786693915485 1786694725336 1786694911760 1786695662128 1786695728931]
        end 1786695760217
        start (- end (* 24 3600000))
        bin (math/auto-bin-size (- end start))
        s (math/series ts start end bin)]
    (println "cumulative:" (pr-str (:cumulative s)))
    (println "rate:" (pr-str (take 3 (:rate s))))
    (println "accel:" (pr-str (take 3 (:accel s))))
    (println "OK")))
