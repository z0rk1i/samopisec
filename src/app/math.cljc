(ns app.math
  "Чистые функции для агрегации дата-поинтов и производных кумулятивной кривой.")

(defonce hour-ms 3600000.0)
(defonce day-ms (* 24 hour-ms))

(defn auto-bin-size
  "Автоматический размер бина по длине диапазона (мс)."
  [range-ms]
  (cond
    (< range-ms (* 3 day-ms)) hour-ms
    (< range-ms (* 14 day-ms)) (* 6 hour-ms)
    :else day-ms))

(defn range-bins
  "Границы бинов [start end) покрывающие [start-ms end-ms].
   Количество бинов округляется вверх: последний частичный бин (хвост диапазона)
   сохраняется, иначе данные последнего неполного интервала терялись бы из rate/accel."
  [start-ms end-ms bin-size-ms]
  (let [n (max 1 (quot (+ (- end-ms start-ms) bin-size-ms -1) bin-size-ms))]
    (mapv (fn [i] {:start (+ start-ms (* i bin-size-ms))
                   :end (+ start-ms (* (inc i) bin-size-ms))})
          (range n))))

(defn cumulative-counts
  "По отсортированным по времени тикам (мс) -> [[t cum-count] ...], монотонно растущая кривая."
  [ts]
  (let [sorted (sort ts)]
    (mapv (fn [i t] [t (inc i)]) (range) sorted)))

(defn tap-rate
  "Количество нажатий и rate (нажатий/час) в каждом бине."
  [ts bins]
  (let [sorted (sort ts)]
    (loop [ts' sorted
           bins' bins
           acc []]
      (if (empty? bins')
        acc
        (let [{:keys [start end]} (first bins')
              dur (- end start)
              cnt (count (take-while #(< % end) ts'))]
          (recur (drop cnt ts')
                 (rest bins')
                 (conj acc {:t start
                            :count cnt
                            :rate (/ (* cnt hour-ms) dur)})))))))

(defn moving-average
  "Скользящее среднее с окном window (нечётное >= 1), края усечены."
  [xs window]
  (let [n (count xs)
        w (max 1 (min window n))
        half (quot w 2)
        csum (vec (reductions + 0.0 xs))]
    (mapv (fn [i]
            (let [lo (max 0 (- i half))
                  hi (min n (+ i half 1))]
              (/ (- (nth csum hi) (nth csum lo)) (- hi lo))))
          (range n))))

(defn second-derivative
  "Ускорение по ряду rate: accel[i] = (rate[i+1] - rate[i-1]) / (2*dt).
   dt — длительность бина в часах (rate в нажатиях/час -> accel в нажатиях/час²).
   На краях — односторонние разности без выдуманных нулей (иначе ложные спайки)."
  [rates dt]
  (let [n (count rates)
        dt (max 1e-9 dt)]
    (mapv (fn [i]
            (cond
              (= n 1) 0.0
              (zero? i) (/ (- (nth rates 1) (nth rates 0)) dt)
              (= i (dec n)) (/ (- (nth rates i) (nth rates (dec i))) dt)
              :else (/ (- (nth rates (inc i)) (nth rates (dec i))) (* 2.0 dt))))
          (range n))))

(defn series
  "Полный набор серий для тиков в [start-ms end-ms) c бином bin-size-ms:
   {:cumulative [[t n]...], :rate [{:t :count :rate}...], :accel [число...]}."
  [ts start-ms end-ms bin-size-ms]
  (let [ts' (sort (filter #(and (<= start-ms %) (< % end-ms)) ts))
        bins (range-bins start-ms end-ms bin-size-ms)
        rates (tap-rate ts' bins)
        smoothed (moving-average (mapv :rate rates) 5)]
    {:cumulative (cumulative-counts ts')
     :rate rates
     :accel (second-derivative smoothed (/ bin-size-ms hour-ms))}))