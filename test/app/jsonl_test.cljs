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