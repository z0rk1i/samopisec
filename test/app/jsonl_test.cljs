(ns app.jsonl-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.jsonl :as jsonl]))

(deftest parse-jsonl-test
  (testing "valid lines parse to maps with keyword keys"
    (is (= [{:id "a" :button-id "b" :ts 100}
            {:id "c" :button-id "d" :ts 200}]
           (jsonl/parse-jsonl "{\"id\":\"a\",\"button-id\":\"b\",\"ts\":100}\n{\"id\":\"c\",\"button-id\":\"d\",\"ts\":200}\n"))))
  (testing "empty and nil text"
    (is (= [] (jsonl/parse-jsonl "")))
    (is (= [] (jsonl/parse-jsonl nil))))
  (testing "corrupt lines are skipped, valid ones kept"
    (is (= [{:id "ok" :ts 1} {:id "bad"}]
           (jsonl/parse-jsonl "{\"id\":\"ok\",\"ts\":1}\nNOT JSON\n{\"broken\":\n\n{\"id\":\"bad\"}\n"))))
  (testing "blank lines are skipped"
    (is (= [{:id "x"}]
           (jsonl/parse-jsonl "\n\n{\"id\":\"x\"}\n\n")))))

(deftest parse-config-test
  (testing "valid config"
    (is (= {:buttons [{:id "a" :label "Жми" :color "#1976d2"}]}
           (jsonl/parse-config "{\"buttons\":[{\"id\":\"a\",\"label\":\"Жми\",\"color\":\"#1976d2\"}]}"))))
  (testing "broken json falls back to empty config"
    (is (= {:buttons []} (jsonl/parse-config "not json{"))))
  (testing "empty string falls back to empty config"
    (is (= {:buttons []} (jsonl/parse-config "")))))

(deftest serialize-config-test
  (testing "round-trip: serialize-config -> parse-config сохраняет конфиг"
    (let [cfg {:buttons [{:id "a" :label "Жми" :color "#1976d2"}]}]
      (is (= cfg (jsonl/parse-config (jsonl/serialize-config cfg))))))
  (testing "serializes to valid JSON text"
    (is (= "{\"buttons\":[]}"
           (jsonl/serialize-config {:buttons []})))))

(deftest split-last-test
  (testing "последний объект отделяется, остальные строки остаются как есть"
    (let [{:keys [lines last]} (jsonl/split-last
                                "{\"id\":\"a\",\"ts\":1}\n{\"id\":\"b\",\"ts\":2}\n")]
      (is (= ["{\"id\":\"a\",\"ts\":1}"] lines))
      (is (= {:id "b" :ts 2} last))))
  (testing "одна строка -> lines пуст, last это она"
    (let [{:keys [lines last]} (jsonl/split-last "{\"id\":\"a\"}\n")]
      (is (= [] lines))
      (is (= {:id "a"} last))))
  (testing "пустой/битый текст -> last nil, lines пуст"
    (let [r (jsonl/split-last "")]
      (is (= [] (:lines r)))
      (is (nil? (:last r)))))
  (testing "битая последняя строка -> last nil, lines без неё"
    (let [{:keys [lines last]} (jsonl/split-last "{\"id\":\"a\"}\nNOT JSON\n")]
      (is (= ["{\"id\":\"a\"}"] lines))
      (is (nil? last))))
  (testing "пустые строки отбрасываются"
    (let [{:keys [lines last]} (jsonl/split-last "\n\n{\"id\":\"a\"}\n\n{\"id\":\"b\"}\n")]
      (is (= ["{\"id\":\"a\"}"] lines))
      (is (= {:id "b"} last)))))