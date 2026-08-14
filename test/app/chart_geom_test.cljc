(ns app.chart-geom-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.chart-geom :as g]))

(def H 150.0)
(def pad 20.0)

(deftest norm-points-test
  (testing "max raw y -> top, 0-baseline floor (miny не ниже 0)"
    (let [{:keys [pts maxy miny]} (g/norm-points [{:x 0 :y 1} {:x 1 :y 10} {:x 2 :y 5}] H pad)]
      (is (= 10.0 maxy))
      (is (= 0.0 miny))                ; 0-baseline: miny прижат к нулю
      (is (= pad (get-in pts [1 :y]))) ; max (10) на верху
      (is (= 75.0 (get-in pts [2 :y]))) ; среднее (5) по центру
      (is (= 119.0 (get-in pts [0 :y]))) ; min (1) у низа, но выше baseline 0 (130)
      (is (< (get-in pts [1 :y]) (get-in pts [2 :y]) (get-in pts [0 :y])))))
  (testing "flat positive data maps to the top (0-baseline)"
    (let [{:keys [pts]} (g/norm-points [{:x 0 :y 5} {:x 1 :y 5}] H pad)]
      (is (= pad (get-in pts [0 :y])))
      (is (= pad (get-in pts [1 :y]))))))

(deftest double-normalization-regression-test
  (testing "re-normalizing already-normalized points inverts the axis (ADR-0007 bug)"
    (let [raw [{:x 0 :y 1} {:x 1 :y 10}]
          once (g/norm-points raw H pad)
          twice (g/norm-points (:pts once) H pad)]
      ;; single pass: raw min (y=1) sits at the bottom
      (is (> (get-in once [:pts 0 :y]) (get-in once [:pts 1 :y])))
      ;; double pass: raw min now sits ABOVE raw max — the axis is inverted
      (is (< (get-in twice [:pts 0 :y]) (get-in twice [:pts 1 :y]))))))

(deftest scale-x-test
  (let [W 300.0]
    (is (= pad (g/scale-x 1000 1000 2000 W pad)))
    (is (= (- W pad) (g/scale-x 2000 1000 2000 W pad)))
    (is (= (+ pad 130.0) (g/scale-x 1500 1000 2000 W pad)))
    (testing "zero-width window"
      (is (= pad (g/scale-x 1000 1000 1000 W pad))))))