(ns app.selectors
  "Чистые селекторы дневных счётчиков — без re-frame, тестируются отдельно."
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
