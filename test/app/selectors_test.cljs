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

(deftest merge-datapoints-test
  (let [local [{:id "opt" :button-id "x" :ts 300}]
        loaded [{:id "a" :button-id "x" :ts 100}
                {:id "b" :button-id "y" :ts 200}
                {:id "opt" :button-id "x" :ts 300}]]
    (testing "оптимистичный тап не вымывается загруженным снимком"
      (is (= ["a" "b" "opt"] (mapv :id (s/merge-datapoints local loaded)))))
    (testing "дедупликация по :id, сортировка по времени"
      (is (= ["a" "b" "opt"]
             (mapv :id (s/merge-datapoints loaded loaded)))))
    (testing "пустой вход"
      (is (= [] (s/merge-datapoints [] [])))
      (is (= ["a"] (mapv :id (s/merge-datapoints [] [{:id "a" :button-id "x" :ts 1}])))))))

(deftest resolve-loaded-buttons-test
  (testing "грязный конфиг — правки не откатываются"
    (is (= [{:id "local"}]
           (s/resolve-loaded-buttons true
                                     [{:id "local"}]
                                     [{:id "stale"}]))))
  (testing "чистый — применяется файл"
    (is (= [{:id "file"}]
           (s/resolve-loaded-buttons false [{:id "old"}] [{:id "file"}]))))
  (testing "файл вернул nil (нет config.json) — остаётся текущее"
    (is (= [{:id "cur"}] (s/resolve-loaded-buttons false [{:id "cur"}] nil)))))
