(ns app.storage-core
  "Чистые переходы файлов хранения (ADR-0024): план дренажа spill-файла,
   решение undo по тексту файла, объединённое чтение main+archive.
   Никакого expo-fs — storage.cljs тонкий FS-адаптер, а логика тестируется
   в node (storage_core_test)."
  (:require [clojure.string :as str]
            [app.csv :as csv]
            [app.contract :as contract]))

(defn drain-plan
  "Текст spill-файла -> {:append-text string :row-count n} или nil, когда
   дренажовать нечего (файл пуст/только header/все строки битые). Битая
   последняя строка отбрасывается парсером; возможный дубль после краша между
   append и delete гасится дедупликацией при чтении."
  [spill-text]
  (when (string? spill-text)
    (let [rows (csv/parse-csv spill-text)]
      (when (seq rows)
        {:append-text (csv/serialize-rows rows)
         :row-count (count rows)}))))

(defn merged-read
  "Распарсенные основной и архивный наборы -> {:dps :main-count}: конкатенация
   с дедупликацией по :id и сортировкой по времени. main-count — число строк
   основного файла: критерий компакции, архив его не искажает."
  [main-dps archive-dps]
  {:dps (->> (concat main-dps archive-dps)
             contract/dedupe-by-id
             contract/normalize-datapoints
             (sort-by :ts)
             vec)
   :main-count (count main-dps)})

(defn undo-plan
  "Текст основного или архивного CSV-файла -> решение снятия последнего поинта:
   {:type :rewrite :content string :removed dp} — атомарная замена содержимого;
   {:type :delete-file :removed dp}            — файл исчерпан, удалить;
   :fallback-archive                            — data-строк нет либо последняя
                                                строка битая: для main значит
                                                «попробовать архив», для архива
                                                — «поинтов больше нет»."
  [text]
  (let [{:keys [lines last]} (csv/split-last text)]
    (if last
      (if (seq lines)
        {:type :rewrite
         :content (str (str/join "\n" lines) "\n")
         :removed last}
        {:type :delete-file :removed last})
      :fallback-archive)))
