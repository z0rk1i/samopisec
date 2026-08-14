(ns app.math-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [app.math :as m]))

(def H m/hour-ms)
(def D m/day-ms)

(deftest auto-bin-size-test
  (is (= H (m/auto-bin-size (* 2 D))))
  (is (= (* 6 H) (m/auto-bin-size (* 7 D))))
  (is (= D (m/auto-bin-size (* 30 D)))))

(deftest range-bins-test
  (is (= [{:start 0 :end 100} {:start 100 :end 200}]
         (m/range-bins 0 200 100)))
  (is (= [{:start 0 :end 50}] (m/range-bins 0 10 50))))

(deftest cumulative-counts-test
  (is (= [] (m/cumulative-counts [])))
  (is (= [[100 1] [200 2] [300 3]]
         (m/cumulative-counts [300 100 200])))
  (is (= [[100 1] [100 2] [150 3]]
         (m/cumulative-counts [100 100 150]))))

(deftest tap-rate-test
  (let [bins [{:start 0 :end 1000} {:start 1000 :end 2000}]]
    (is (= [{:t 0 :count 3 :rate 10800.0} {:t 1000 :count 1 :rate 3600.0}]
           (m/tap-rate [100 200 300 1500] bins)))))

(deftest moving-average-test
  (is (= [1.0 2.0 3.0] (m/moving-average [1 2 3] 1)))
  (is (= [1.5 2.0 2.5] (m/moving-average [1 2 3] 3))))

(deftest second-derivative-test
  (is (= [1.0 1.0 -1.0] (m/second-derivative [1 2 3]))))

(deftest series-test
  (let [ts [0 100 1000 1100 2000]
        s (m/series ts 0 3000 1000)]
    (is (= [[0 1] [100 2] [1000 3] [1100 4] [2000 5]]
           (:cumulative s)))
    (is (= 3 (count (:rate s))))
    (is (= 3 (count (:accel s))))
    (is (= 3000.0 (first (:accel s))))))