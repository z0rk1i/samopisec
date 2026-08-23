(ns app.chart-geom-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.chart-geom :as g]))

(deftest decimate-test
  (testing "small input is untouched"
    (is (= [{:x 0 :y 0} {:x 1 :y 1}]
           (g/decimate [{:x 0 :y 0} {:x 1 :y 1}] 400)))
    (is (= [] (g/decimate [] 400))))
  (testing "large input is reduced to max-points, first and last preserved"
    (let [pts (mapv (fn [i] {:x i :y i}) (range 1000))
          out (g/decimate pts 100)]
      (is (= 100 (count out)))
      (is (= {:x 0 :y 0} (first out)))
      (is (= {:x 999 :y 999} (last out)))))
  (testing "max-points == 1 returns just the first point"
    (is (= [{:x 0 :y 0}] (g/decimate [{:x 0 :y 0} {:x 5 :y 5}] 1)))))
