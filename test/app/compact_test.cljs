(ns app.compact-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.compact :as c]))

(deftest cutoff-ms-test
  (testing "cutoff is now minus retention days"
    (let [now 1000000000000]
      (is (= (- now (* 90 86400000)) (c/cutoff-ms now 90)))
      (is (= now (c/cutoff-ms now 0))))))

(deftest split-test
  (testing "points newer than cutoff stay, older go to archive"
    (let [cut 500
          dps [{:id "a" :ts 100} {:id "b" :ts 500} {:id "c" :ts 900}]
          {:keys [kept dropped]} (c/split dps cut)]
      (is (= ["b" "c"] (mapv :id kept)))
      (is (= ["a"] (mapv :id dropped)))))
  (testing "boundary ts == cutoff stays in main file"
    (let [{:keys [kept dropped]} (c/split [{:id "x" :ts 500}] 500)]
      (is (= ["x"] (mapv :id kept)))
      (is (empty? dropped))))
  (testing "order preserved within groups"
    (let [{:keys [kept dropped]}
          (c/split [{:id "a" :ts 300} {:id "b" :ts 600} {:id "c" :ts 200} {:id "d" :ts 700}] 500)]
      (is (= ["b" "d"] (mapv :id kept)))
      (is (= ["a" "c"] (mapv :id dropped)))))
  (testing "empty input"
    (is (= {:kept [] :dropped []} (c/split [] 500)))))