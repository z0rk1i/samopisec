(ns app.selectors
  "Чистые селекторы дневных счётчиков и merge-правила загрузки — без re-frame,
   тестируются отдельно."
  (:require [app.clock :as clock]
            [app.contract :as contract]))

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

(defn merge-datapoints
  "Объединяет локальные (в т.ч. оптимистичные, ещё не записанные в файл) и
   загруженные из файла дата-поинты: дедупликация по :id (первый побеждает),
   сортировка по времени. Закрывает гонку «тап между постановкой чтения в
   очередь записи и резолвом этого чтения» — тап больше не пропадает из UI."
  [local loaded]
  (->> (concat local loaded)
       contract/dedupe-by-id
       (sort-by :ts)
       vec))

(defn resolve-loaded-buttons
  "Загруженный из файла конфиг применяется только при отсутствии несохранённых
   правок (:config/dirty) — иначе отложенное чтение откатывало бы правку."
  [dirty current loaded]
  (if dirty current (or loaded current)))
