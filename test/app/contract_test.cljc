(ns app.contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.contract :as c]))

(deftest config-button-test
  (testing "valid button"
    (is (c/config-button? {:id "a" :label "Жми" :color "#1976d2"})))
  (testing "invalid buttons"
    (is (not (c/config-button? nil)))
    (is (not (c/config-button? {:id "" :label "x" :color "#fff"})))
    (is (not (c/config-button? {:id "a" :label "" :color "#fff"})))
    (is (not (c/config-button? {:id "a" :label "x" :color "red"})))
    (is (not (c/config-button? {:id "a" :label "x"})))))

(deftest datapoint-test
  (is (c/datapoint? {:id "d" :button-id "b" :ts 100}))
  (is (not (c/datapoint? {:id "d" :button-id "b" :ts -1})))
  (is (not (c/datapoint? {:id "d" :button-id "b"})))
  (is (not (c/datapoint? {:id "d" :button-id "b" :ts "100"}))))

(deftest normalize-config-test
  (testing "drops invalid buttons, keeps valid in order"
    (is (= {:buttons [{:id "a" :label "x" :color "#fff"}]}
           (c/normalize-config {:buttons [{:id "a" :label "x" :color "#fff"}
                                          {:id "" :label "y" :color "#000"}
                                          "junk"]})))))

(deftest normalize-datapoints-test
  (is (= [{:id "a" :button-id "x" :ts 1}]
         (c/normalize-datapoints [{:id "a" :button-id "x" :ts 1}
                                  {:id "bad" :button-id "x" :ts -5}
                                  {:id "bad2" :ts 2}]))))