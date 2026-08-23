(ns app.grafana-series-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.math :as math]
            [app.grafana-series :as gs]))

(def ^:private H 3600000)
(def ^:private base 1760000000000)

(def ^:private buttons [{:id "b1" :label "Чай" :color "#e53935"}
                        {:id "b2" :label "Кофе" :color "#1e88e5"}])

(defn- dp [id bid ts] {:id id :button-id bid :ts ts})

(defn- fixture-dps []
  [(dp "a" "b1" (+ base 1000))
   (dp "c" "b1" (+ base 2000))
   (dp "e" "b1" (+ base H 1000))
   (dp "g" "b2" (+ base (* 2 H) 500))])

(deftest empty-payload-test
  (testing "пустые данные -> нулевой payload без кривых"
    (let [p (gs/series-payload [] buttons)]
      (is (zero? (:points p)))
      (is (= [] (:curves p)))
      (is (= {} (:totals p)))
      (is (= [] (:recent p))))))

(deftest payload-window-totals-test
  (testing "окно, totals, recent"
    (let [p (gs/series-payload (fixture-dps) buttons)]
      (is (= 4 (:points p)))
      (is (= (+ base 1000) (get-in p [:window :t0]))
          "t0 — первый (минимальный) тап")
      (is (= (+ base (* 2 H) 500) (get-in p [:window :t1]))
          "последний тап старше t0+час — окно по нему")
      (is (= {"b1" 3 "b2" 1} (:totals p)))
      (is (= ["g" "e" "c" "a"] (mapv first (:recent p)))
          "recent — свежие сверху")
      (is (= 2 (count (:curves p)))))))

(deftest curves-match-app-math-test
  (testing "p1/p2 совпадают с прямыми вызовами app.math"
    (let [p (gs/series-payload (fixture-dps) buttons)
          c1 (some #(when (= "b1" (:id %)) %) (:curves p))
          ;; то же окно/бины, что считает series-payload: t0 = минимальный тап
          t0 (+ base 1000)
          t1 (+ base (* 2 H) 500)
          bin (math/auto-bin-size (- t1 t0))
          bins (math/range-bins t0 t1 bin)
          ts (sort [(+ base 1000) (+ base 2000) (+ base H 1000)])
          rates (math/tap-rate ts bins)
          accel (math/second-derivative
                 (math/moving-average (mapv :rate rates) 5)
                 (/ bin H))]
      (is (= 2 (count (:curves p))))
      ;; накопленная кривая: последняя точка == полное число тапов кнопки
      (is (= 3 (:v (peek (:cumulative c1)))))
      ;; Производная 1 — сырой биннинг rate из app.math
      (is (= (mapv :rate rates) (mapv :v (:p1 c1))))
      (is (= (count rates) (count (:p1 c1))))
      ;; Производная 2 — центральная разность MA(5), X выровнен с rate
      (is (= (mapv :t rates) (mapv :t (:p2 c1))))
      (is (= accel (mapv :v (:p2 c1)))))))

(deftest decimation-cap-test
  (testing ">400 тапов: кривые прорежены до max-polyline-points, итог сохранён"
    (let [n 1000
          dps (mapv (fn [i] (dp (str "d" i) "b1" (+ base (* i 60000)))) (range n))
          p (gs/series-payload dps buttons)
          c (first (:curves p))]
      (is (<= (count (:cumulative c)) 400))
      (is (= 1 (:v (first (:cumulative c)))) "первая точка сохранена")
      (is (= n (:v (peek (:cumulative c)))) "последняя точка хранит итог")
      (is (<= (count (:p1 c)) 400))
      (is (<= (count (:p2 c)) 400)))))

(deftest fallback-buttons-test
  (testing "конфиг пуст — кнопки выводятся из данных с фолбэк-цветом"
    (let [p (gs/series-payload [(dp "a" "ghost" base)] [])
          btns (:buttons p)]
      (is (= [{:id "ghost" :label "ghost" :color "#e53935"}] btns))
      (is (= 1 (count (:curves p))))
      (is (= "ghost" (:label (first (:curves p))))))))

(deftest inactive-button-skipped-test
  (testing "кнопка без тапов не даёт кривой"
    (let [p (gs/series-payload [(dp "a" "b1" base)] buttons)]
      (is (= ["b1"] (mapv :id (:curves p)))))))
