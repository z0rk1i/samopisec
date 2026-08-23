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

(deftest valid-color-test
  (is (c/valid-color? "#1976d2"))
  (is (c/valid-color? "#ABCDEF"))
  (is (not (c/valid-color? "#fff")))
  (is (not (c/valid-color? "#12345")))
  (is (not (c/valid-color? "#1234567")))
  (is (not (c/valid-color? "red")))
  (is (not (c/valid-color? nil)))
  (is (not (c/valid-color? 123))))

(deftest normalize-config-test
  (testing "drops invalid buttons, keeps valid in order"
    (is (= {:buttons [{:id "a" :label "x" :color "#ffffff"}]}
           (c/normalize-config {:buttons [{:id "a" :label "x" :color "#ffffff"}
                                          {:id "" :label "y" :color "#000000"}
                                          {:id "c" :label "z" :color "#fff"}
                                          "junk"]})))))

(deftest normalize-datapoints-test
  (is (= [{:id "a" :button-id "x" :ts 1}]
         (c/normalize-datapoints [{:id "a" :button-id "x" :ts 1}
                                  {:id "bad" :button-id "x" :ts -5}
                                  {:id "bad2" :ts 2}]))))

(deftest dedupe-by-id-test
  (testing "первый поинт побеждает, порядок сохраняется"
    (is (= [{:id "a" :button-id "x" :ts 1}
            {:id "b" :button-id "y" :ts 2}
            {:id "c" :button-id "x" :ts 3}]
           (c/dedupe-by-id [{:id "a" :button-id "x" :ts 1}
                            {:id "b" :button-id "y" :ts 2}
                            {:id "a" :button-id "x" :ts 1}
                            {:id "c" :button-id "x" :ts 3}
                            {:id "b" :button-id "y" :ts 2}]))))
  (testing "пустой вход"
    (is (= [] (c/dedupe-by-id [])))))