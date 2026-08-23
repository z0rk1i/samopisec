(ns app.selectors-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.selectors :as s]))

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
