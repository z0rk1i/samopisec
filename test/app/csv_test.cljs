(ns app.csv-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.csv :as csv]))

(deftest header-test
  (is (= "id,button_id,ts" csv/header)))

(deftest serialize-row-test
  (is (= "a,b,100" (csv/serialize-row {:id "a" :button-id "b" :ts 100}))))

(deftest serialize-csv-test
  (testing "with rows includes header and trailing newline"
    (is (= "id,button_id,ts\na,b,100\nc,d,200\n"
           (csv/serialize-csv [{:id "a" :button-id "b" :ts 100}
                               {:id "c" :button-id "d" :ts 200}]))))
  (testing "empty -> header only"
    (is (= "id,button_id,ts\n" (csv/serialize-csv []))))
  (testing "single row"
    (is (= "id,button_id,ts\na,b,100\n"
           (csv/serialize-csv [{:id "a" :button-id "b" :ts 100}])))))

(deftest parse-csv-test
  (testing "valid csv with header"
    (is (= [{:id "a" :button-id "b" :ts 100}
            {:id "c" :button-id "d" :ts 200}]
           (csv/parse-csv "id,button_id,ts\na,b,100\nc,d,200\n"))))
  (testing "without header still parses (tolerates legacy)"
    (is (= [{:id "a" :button-id "b" :ts 100}]
           (csv/parse-csv "a,b,100\n"))))
  (testing "empty and nil"
    (is (= [] (csv/parse-csv "")))
    (is (= [] (csv/parse-csv nil))))
  (testing "corrupt lines skipped"
    (is (= [{:id "a" :button-id "b" :ts 100}]
           (csv/parse-csv "id,button_id,ts\na,b,100\nNOT_CSV\nx,y,not_a_number\n"))))
  (testing "blank lines skipped"
    (is (= [{:id "a" :button-id "b" :ts 100}]
           (csv/parse-csv "id,button_id,ts\n\n\na,b,100\n\n"))))
  (testing "header only -> empty"
    (is (= [] (csv/parse-csv "id,button_id,ts\n")))))

(deftest split-last-test
  (testing "header + 2 rows -> last is second, lines = header + first"
    (let [{:keys [lines last]} (csv/split-last "id,button_id,ts\na,b,100\nc,d,200\n")]
      (is (= ["id,button_id,ts" "a,b,100"] lines))
      (is (= {:id "c" :button-id "d" :ts 200} last))))
  (testing "single data row"
    (let [{:keys [lines last]} (csv/split-last "id,button_id,ts\na,b,100\n")]
      (is (= ["id,button_id,ts"] lines))
      (is (= {:id "a" :button-id "b" :ts 100} last))))
  (testing "header only -> last nil"
    (let [{:keys [lines last]} (csv/split-last "id,button_id,ts\n")]
      (is (= ["id,button_id,ts"] lines))
      (is (nil? last))))
  (testing "empty -> last nil"
    (let [r (csv/split-last "")]
      (is (= [] (:lines r)))
      (is (nil? (:last r)))))
  (testing "corrupt last line -> last nil"
    (let [{:keys [lines last]} (csv/split-last "id,button_id,ts\na,b,100\nBAD\n")]
      (is (= ["id,button_id,ts" "a,b,100"] lines))
      (is (nil? last))))
  (testing "without header"
    (let [{:keys [lines last]} (csv/split-last "a,b,100\nc,d,200\n")]
      (is (= ["a,b,100"] lines))
      (is (= {:id "c" :button-id "d" :ts 200} last)))))

(deftest round-trip-test
  (testing "serialize -> parse retains datapoints"
    (let [dps [{:id "a" :button-id "b" :ts 100}
               {:id "x" :button-id "y" :ts 9999999999999}]]
      (is (= dps (csv/parse-csv (csv/serialize-csv dps)))))))
