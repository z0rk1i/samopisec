(ns app.selectors-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.selectors :as s]))

(deftest range-window-test
  (let [D 86400000
        W (* 7 D)
        M (* 30 D)
        t0 1000000000000]
    (is (= [(- t0 D) t0] (s/range-window :day t0)))
    (is (= [(- t0 W) t0] (s/range-window :week t0)))
    (is (= [(- t0 M) t0] (s/range-window :month t0)))
    (is (= [0 t0] (s/range-window :all t0)))))

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