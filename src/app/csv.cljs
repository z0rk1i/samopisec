(ns app.csv
  "Чистые функции CSV дата-поинтов без зависимостей от FS.
   Формат: header `id,button_id,ts` (ts = epoch ms int), строки без кавычек
   (id/uuid и button-id не содержат запятых/кавычек). Для парсинга
   пропускается header, битые строки (не 3 колонки или не число ts) дропаются."
  (:require [clojure.string :as str]
            [app.contract :as contract]))

(def header "id,button_id,ts")

(defn serialize-row
  "Сериализует один datapoint {:id :button-id :ts} в CSV-строку."
  [{:keys [id button-id ts]}]
  (str id "," button-id "," (long ts)))

(defn serialize-csv
  "Сериализует seq datapoints в CSV-текст с header."
  [dps]
  (str header "\n"
       (str/join "\n" (map serialize-row dps))
       (when (seq dps) "\n")))

(defn serialize-rows
  "Сериализует seq datapoints в CSV-строки без header (для append архива)."
  [dps]
  (str (str/join "\n" (map serialize-row dps)) "\n"))

(defn- parse-line
  "Парсит одну CSV-строку (без header) -> datapoint или nil."
  [line]
  (let [parts (str/split line #"," 3)]
    (when (= 3 (count parts))
      (let [[id button-id ts-str] parts
            ts (js/parseInt ts-str 10)]
        (when (and (seq id) (seq button-id) (not (js/isNaN ts)))
          (let [dp {:id id :button-id button-id :ts ts}]
            (when (contract/datapoint? dp) dp)))))))

(defn parse-csv
  "Разбирает CSV-текст в вектор datapoints, пропуская header, пустые и битые строки."
  [^string text]
  (let [lines (->> (str/split-lines (or text ""))
                   (filter seq)
                   vec)]
    (if (empty? lines)
      []
      (let [has-header (= header (first lines))
            data-lines (if has-header (rest lines) lines)]
        (->> data-lines
             (keep parse-line)
             vec)))))

(defn split-last
  "Делит CSV-текст на {:lines [сырые строки без последней data-строки] :last datapoint-or-nil}.
   Header не считается data-строкой и не попадает в :last. Пустые строки отбрасываются.
   Битая последняя строка -> :last nil, :lines без неё. Используется undo: последний
   поинт файла — истинно последний тап."
  [text]
  (let [raw (->> (str/split-lines (or text ""))
                 (filter seq)
                 vec)]
    (if (empty? raw)
      {:lines [] :last nil}
      (let [has-header (= header (first raw))
            header-line (when has-header (first raw))
            data-lines (if has-header (vec (rest raw)) raw)]
        (if (empty? data-lines)
          {:lines (if header-line [header-line] []) :last nil}
          (let [last-line (peek data-lines)
                rest-lines (pop data-lines)
                parsed (parse-line last-line)]
            {:lines (cond-> []
                      header-line (conj header-line)
                      (seq rest-lines) (into rest-lines))
             :last parsed}))))))

(defn has-header?
  "true если текст начинается с CSV header."
  [text]
  (let [first-line (first (->> (str/split-lines (or text ""))
                               (filter seq)))]
    (= header first-line)))
