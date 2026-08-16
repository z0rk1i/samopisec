(ns app.clock-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.clock :as clock]))

(deftest now-ms-override-test
  (testing "set-now! подменяет источник, nil возвращает реальные часы"
    (try
      (clock/set-now! (fn [] 42))
      (is (= 42 (clock/now-ms)))
      (finally
        (clock/set-now! nil)))
    (is (pos? (clock/now-ms)))))

(deftest start-of-day-test
  (testing "начало календарного дня — полночь по локальному времени"
    (let [t (js/Date. 2026 7 16 14 37 0) ;; 16 августа 2026 14:37
          sod (js/Date. (clock/start-of-day (.getTime t)))]
      (is (= 2026 (.getFullYear sod)))
      (is (= 7 (.getMonth sod)))
      (is (= 16 (.getDate sod)))
      (is (= 0 (.getHours sod)))
      (is (= 0 (.getMinutes sod))))))

(deftest day-start-ms-test
  (testing "0 = сегодня, 1 = вчера"
    (let [t (js/Date. 2026 7 16 14 37 0)
          today (js/Date. (clock/day-start-ms (.getTime t) 0))
          yesterday (js/Date. (clock/day-start-ms (.getTime t) 1))]
      (is (= 16 (.getDate today)))
      (is (= 15 (.getDate yesterday)))
      (is (= 0 (.getHours yesterday)))))
  (testing "идемпотентность: start-of-day = day-start-ms 0"
    (let [t (js/Date. 2026 7 16 14 37 0)]
      (is (= (clock/start-of-day (.getTime t))
             (clock/day-start-ms (.getTime t) 0))))))