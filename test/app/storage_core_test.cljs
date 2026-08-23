(ns app.storage-core-test
  "Чистые переходы файлов хранения (ADR-0024) без expo-fs."
  (:require [clojure.test :refer [deftest is testing]]
            [app.storage-core :as core]))

(def header-line "id,button_id,ts")

(deftest drain-plan-test
  (testing "валидные строки -> план append"
    (let [plan (core/drain-plan (str header-line "\na,x,100\nb,y,200\n"))]
      (is (= 2 (:row-count plan)))
      (is (= "a,x,100\nb,y,200\n" (:append-text plan)))))
  (testing "пустой файл / только header / мусор -> дренажовать нечего"
    (is (nil? (core/drain-plan "")))
    (is (nil? (core/drain-plan (str header-line "\n"))))
    (is (nil? (core/drain-plan "мусор без запятых\n"))))
  (testing "битые строки отбрасываются, валидные остаются"
    (let [plan (core/drain-plan (str "a,x,100\nбитая-строка\nc,y,300\n"))]
      (is (= 2 (:row-count plan))))
    (let [plan (core/drain-plan (str "a,x,100\n torn-line-without-newline"))]
      (is (= 1 (:row-count plan))))))

(deftest merged-read-test
  (testing "main+archive объединяются с дедупликацией и сортировкой"
    (let [res (core/merged-read
               [{:id "m2" :button-id "x" :ts 200} {:id "m1" :button-id "x" :ts 100}]
               [{:id "a1" :button-id "y" :ts 50} {:id "dup" :button-id "z" :ts 999}])
          res' (core/merged-read
                [{:id "m2" :button-id "x" :ts 200} {:id "m1" :button-id "x" :ts 100}]
                [{:id "a1" :button-id "y" :ts 50}])]
      (is (= ["a1" "m1" "m2" "dup"] (mapv :id (:dps res))))
      (is (= 2 (:main-count res)) "архив не искажает критерий компакции")
      (is (= ["a1" "m1" "m2"] (mapv :id (:dps res')))))))

(deftest undo-plan-test
  (testing "обычный файл: атомарная замена без последней строки"
    (let [plan (core/undo-plan (str header-line "\na,x,100\nb,y,200\n"))]
      (is (= :rewrite (:type plan)))
      (is (= {:id "b" :button-id "y" :ts 200} (:removed plan)))
      (is (= (str header-line "\na,x,100\n") (:content plan)))))
  (testing "одна data-строка без header: файл удаляется"
    (let [plan (core/undo-plan "a,x,100\n")]
      (is (= :delete-file (:type plan)))
      (is (= {:id "a" :button-id "x" :ts 100} (:removed plan)))))
  (testing "header-only или пустой текст -> попробовать архив"
    (is (= :fallback-archive (core/undo-plan (str header-line "\n"))))
    (is (= :fallback-archive (core/undo-plan ""))))
  (testing "битая последняя строка -> архив (файл не трогаем на этом шаге)"
    (is (= :fallback-archive (core/undo-plan (str header-line "\nсломанная\n"))))
    (is (= :fallback-archive
           (core/undo-plan (str header-line "\na,x,100\nбитая-хвостовая\n"))))))
