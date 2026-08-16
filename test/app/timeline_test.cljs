(ns app.timeline-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.timeline :as timeline]
            [app.chart-geom :as geom]))

(def series {:start 0 :end 100
             :cumulative [[0 0] [50 5] [100 10]]
             :rate [{:t 0 :rate 0} {:t 50 :rate 5} {:t 100 :rate 10}]
             :accel [0 5 10]})

(deftest points-test
  (testing ":cumulative отдаёт x=scale-x, y=сырое значение"
    (let [pts (timeline/points {:series series :k :cumulative :start 0 :end 100 :W 200})]
      (is (= 3 (count pts)))
      (is (= {:x (geom/scale-x 0 0 100 200 geom/pad) :y 0} (first pts)))
      (is (= {:x (geom/scale-x 100 0 100 200 geom/pad) :y 10} (last pts)))))
  (testing ":rate берёт :t и :rate"
    (let [pts (timeline/points {:series series :k :rate :start 0 :end 100 :W 200})]
      (is (= 3 (count pts)))
      (is (= (geom/scale-x 50 0 100 200 geom/pad) (:x (second pts))))
      (is (= 5 (:y (second pts))))))
  (testing ":accel берёт x из rate[i].t, y из accel[i]"
    (let [pts (timeline/points {:series series :k :accel :start 0 :end 100 :W 200})]
      (is (= 3 (count pts)))
      (is (= {:x (geom/scale-x 0 0 100 200 geom/pad) :y 0} (first pts)))
      (is (= {:x (geom/scale-x 100 0 100 200 geom/pad) :y 10} (last pts)))))
  (testing "пустая серия -> пустые точки"
    (let [pts (timeline/points {:series {:start 0 :end 1 :cumulative [] :rate [] :accel []}
                                :k :cumulative :start 0 :end 1 :W 200})]
      (is (= [] pts))))
  (testing "accel без rate (несбалансировано) -> x=0, не падает"
    (let [pts (timeline/points {:series {:start 0 :end 100 :rate [] :accel [1 2]}
                                :k :accel :start 0 :end 100 :W 200})]
      (is (= 2 (count pts)))
      (is (= {:x (geom/scale-x 0 0 100 200 geom/pad) :y 1} (first pts))))))