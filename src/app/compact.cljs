(ns app.compact
  "Чистые функции компакции datapoints.jsonl: разделение дата-поинтов на
   «свежие» (retention-период, остаются в основном файле) и «старые» (переносятся
   в архив). Без зависимостей от нативного FS — покрыто тестами."
  (:require [app.math :as math]))

(defn cutoff-ms
  "Метка времени (мс) отсечки: точки старше неё — кандидаты в архив.
   retention-days — число дней свежих данных, остающихся в основном файле."
  [now-ms retention-days]
  (- now-ms (* retention-days math/day-ms)))

(defn split
  "Разделяет дата-поинты на {:kept [свежие] :dropped [старые]} относительно
   cutoff (мс). Точки с ts >= cutoff остаются, старшие уходят в архив.
   Порядок внутри групп сохраняется."
  [dps cutoff]
  (reduce (fn [acc dp]
            (if (<= cutoff (:ts dp))
              (update acc :kept conj dp)
              (update acc :dropped conj dp)))
          {:kept [] :dropped []}
          dps))