(ns app.selectors-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.selectors :as s]
            [app.chart-geom :as geom]))

(deftest range-window-test
  (let [D 86400000
        W (* 7 D)
        M (* 30 D)
        t0 1000000000000]
    (testing ":day is a calendar-day window, not rolling 24h"
      (let [[start end] (s/range-window :day t0)]
        (is (= t0 end))
        (is (= (s/start-of-day t0) start))
        (is (< 0 (- end start) D))))
    (is (= [(- t0 W) t0] (s/range-window :week t0)))
    (is (= [(- t0 M) t0] (s/range-window :month t0)))
    (is (= [0 t0] (s/range-window :all t0)))))

(deftest start-of-day-test
  (testing "mid-afternoon maps to local midnight"
    (let [sd (js/Date. (s/start-of-day (.getTime (js/Date. 2026 7 14 15 30 45 123))))]
      (is (= 0 (.getHours sd)))
      (is (= 0 (.getMinutes sd)))
      (is (= 0 (.getSeconds sd)))
      (is (= 2026 (.getFullYear sd)))
      (is (= 7 (.getMonth sd)))
      (is (= 14 (.getDate sd)))))
  (testing "midnight stays midnight"
    (let [d (js/Date. 2026 0 5 0 0 0 0)]
      (is (= (.getTime d) (s/start-of-day (.getTime d)))))))

(deftest day-window-filtering-test
  (testing ":day window includes today but excludes yesterday"
    (let [t0 (.getTime (js/Date. 2026 7 14 18 0 0))
          [start end] (s/range-window :day t0)
          today-ts (.getTime (js/Date. 2026 7 14 12 0 0))
          yest-ts (.getTime (js/Date. 2026 7 13 23 59 59))]
      (is (<= start today-ts end))
      (is (> start yest-ts)))))

(deftest series-test
  (testing "empty datapoints -> empty series with window"
    (let [res (s/series {:range :all :button-id :all} [] 1000)]
      (is (= [] (:cumulative res)))
      (is (= [] (:rate res)))
      (is (= [] (:accel res)))
      (is (= 0 (:start res)))
      (is (= 1000 (:end res)))))
  (testing "filters by button-id"
    (let [dps [{:id "a" :button-id "x" :ts 100}
               {:id "b" :button-id "y" :ts 200}
               {:id "c" :button-id "x" :ts 300}]
          res (s/series {:range :all :button-id "x"} dps 1000)]
      (is (= 2 (count (:cumulative res))))))
  (testing "cumulative counts sorted by ts"
    (let [dps [{:id "a" :button-id "x" :ts 300}
               {:id "b" :button-id "x" :ts 100}
               {:id "c" :button-id "x" :ts 200}]
          res (s/series {:range :all :button-id "x"} dps 1000)]
      (is (= [[100 1] [200 2] [300 3]] (:cumulative res))))))

(deftest today-counts-test
  (testing "counts only today's datapoints"
    (let [now (js/Date.now)
          dps [{:id "old" :button-id "x" :ts 1}
               {:id "now" :button-id "y" :ts now}
               {:id "now2" :button-id "y" :ts now}]
          res (s/today-counts dps now)]
      (is (= 2 (:total res)))
      (is (= {"y" 2} (:by-button res)))))
  (testing "no datapoints"
    (is (= {:total 0 :by-button {}} (s/today-counts [] (js/Date.now))))))
(deftest per-button-totals-test
  (testing "counts all datapoints and groups by button"
    (let [dps [{:id "a" :button-id "x" :ts 1}
               {:id "b" :button-id "y" :ts 2}
               {:id "c" :button-id "x" :ts 3}
               {:id "bad" :ts 4}]]
      (is (= {:total 4 :by-button {"x" 2 "y" 1}} (s/per-button-totals dps)))))
  (testing "empty"
    (is (= {:total 0 :by-button {}} (s/per-button-totals [])))))

(deftest current-streak-test
  (let [D 86400000
        now 1760000000000
        d0 (s/start-of-day now)
        make (fn [ts] {:id "a" :button-id "x" :ts ts})]
    (testing "no taps -> 0"
      (is (zero? (s/current-streak [] now))))
    (testing "one tap today -> 1"
      (is (= 1 (s/current-streak [(make (+ d0 60 60 1000))] now))))
    (testing "taps today and yesterday -> 2"
      (is (= 2 (s/current-streak [(make (+ d0 1000))
                                  (make (- d0 1000))] now))))
    (testing "empty today but yesterday -> 1"
      (is (= 1 (s/current-streak [(make (- d0 1000))] now))))
    (testing "gap breaks the streak"
      (is (zero? (s/current-streak [(make (- d0 (* 2 D) 5000))] now))))))

(deftest best-day-test
  (let [d0 (s/start-of-day 1760000000000)
        make (fn [ts] {:id "a" :button-id "x" :ts ts})]
    (testing "empty -> nil"
      (is (nil? (s/best-day []))))
    (testing "most taps day wins"
      (let [res (s/best-day [(make (+ d0 1000))
                             (make (+ d0 2000))
                             (make (- d0 1000))])]
        (is (= 2 (:count res)))
        (is (= d0 (:ts res)))))))

(deftest per-hour-heatmap-test
  (testing "buckets by hour of day"
    (let [d (js/Date. 1760000000000)
          h0 (.getHours d)
          t0 (.getTime d)
          make (fn [h] (let [dd (js/Date. t0)] (.setHours dd h 30 0 0) {:id "a" :button-id "x" :ts (.getTime dd)}))
          res (s/per-hour-heatmap [(make h0) (make h0) (make (mod (inc h0) 24))])]
      (is (= 24 (count res)))
      (is (= 2 (nth res h0)))
      (is (= 1 (nth res (mod (inc h0) 24)))))))

(deftest series-decimation-test
  (testing "big series decimates to ≤ max-polyline-points, keeps first/last"
    (let [n (* geom/max-polyline-points 10)
          dps (mapv (fn [i] {:id (str "d" i) :button-id "x" :ts (+ 1000 (* i 86400000))})
                    (range n))
          res (s/series {:range :all :button-id "x"} dps (+ 1000 (* n 86400000)))]
      (is (<= (count (:cumulative res)) geom/max-polyline-points))
      (is (= 1 (first (map second (:cumulative res)))) "первая точка — накопление 1")
      (is (= n (second (last (:cumulative res)))) "последняя точка сохраняет итог")
      (is (<= (count (:rate res)) geom/max-polyline-points))
      (is (<= (count (:accel res)) geom/max-polyline-points))))
  (testing "rate/accel stay aligned by index after decimation"
    (let [n (* geom/max-polyline-points 10)
          dps (mapv (fn [i] {:id (str "d" i) :button-id "x" :ts (+ 1000 (* i 86400000))})
                    (range n))
          res (s/series {:range :all :button-id "x"} dps (+ 1000 (* n 86400000)))
          rates (:rate res)
          accel (:accel res)]
      (is (= (count rates) (count accel)) "rate и accel прорежены одинаково"))))
