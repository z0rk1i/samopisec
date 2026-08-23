(ns app.offline-html-test
  "Живая копия для WebView (строка в app.offline-html) обязана совпадать с
   исходником assets/grafana-offline/index.html: правка HTML без перегенерации
   offline_html.cljs молча не попадает в приложение (перегенерация — шаг в
   scripts/release.sh). Плюс smoke postMessage-канала: сообщение от натива
   перерисовывает дашборд."
  (:require [clojure.test :refer [deftest is testing]]
            [app.offline-html :as offline]))

(deftest offline-html-parity-test
  (let [src (.readFileSync (js/require "fs")
                           "assets/grafana-offline/index.html"
                           "utf8")]
    (is (= src offline/html)
        "offline_html.cljs устарел — перегенерируй его из assets/grafana-offline/index.html")))

(defn- script-of
  [html]
  (let [open (.indexOf html "<script>")
        close (.lastIndexOf html "</script>")]
    (subs html (+ open 8) close)))

(defn- stub-env
  "Стаб DOM/canvas: счётчик вызовов отрисовки, элементы по id, захваченные
   message-слушатели. Возвращает {:calls :pill :doc-listeners :restore}."
  []
  (let [calls (atom 0)
        bump #(swap! calls inc)
        ctx #js {:clearRect bump :beginPath bump :moveTo bump :lineTo bump
                 :stroke bump :fillRect bump :fillText bump
                 :setLineDash (fn [_])
                 :measureText (fn [_] #js {:width 10})}
        canvas #js {:width 600 :height 220 :style #js {}
                    :getContext (fn [_] ctx)}
        pill #js {:textContent nil}
        els #js {}
        doc-listeners (atom {})
        win-listeners (atom {})
        prev-doc (.-document js/globalThis)
        prev-win (.-window js/globalThis)
        doc #js {:readyState "complete"
                 :getElementById (fn [id]
                                   (when-not (aget els id)
                                     (aset els id
                                           (if (.startsWith id "c-")
                                             canvas
                                             #js {:style #js {} :textContent nil
                                                  :innerHTML "" :appendChild (fn [_])})))
                                   (aget els id))
                 :querySelector (fn [_] #js {:innerHTML "" :appendChild (fn [_])})
                 :createElement (fn [_] #js {:style #js {} :textContent ""
                                             :className "" :appendChild (fn [_])})
                 :addEventListener (fn [type f] (swap! doc-listeners assoc type f))}
        win #js {:addEventListener (fn [type f] (swap! win-listeners assoc type f))}]
    (aset els "total" pill)
    (set! (.-document js/globalThis) doc)
    (set! (.-window js/globalThis) win)
    {:calls calls :pill pill
     :doc-listeners doc-listeners :win-listeners win-listeners
     :restore (fn []
                (set! (.-document js/globalThis) prev-doc)
                (set! (.-window js/globalThis) prev-win))}))

(deftest webview-message-update-test
  (testing "postMessage от натива перерисовывает дашборд без перезагрузки страницы"
    (let [{:keys [calls pill doc-listeners restore]} (stub-env)]
      (js/eval (script-of offline/html))
      ;; init() отрендерил пустые данные; сообщение с payload обновляет страницу
      (let [payload (clj->js
                     {:points 2
                      :window {:t0 1 :t1 100}
                      :buttons [{:id "b1" :label "Чай" :color "#e53935"}]
                      :totals {"b1" 2}
                      :recent [["abc123" "b1" 50]]
                      :curves [{:id "b1" :label "Чай" :color "#e53935"
                                :cumulative [{:t 1 :v 1} {:t 50 :v 2}]
                                :p1 [{:t 1 :v 2} {:t 50 :v 0}]
                                :p2 [{:t 1 :v 0} {:t 50 :v 0}]}]})
            before @calls
            handler (get @doc-listeners "message")]
        (is (fn? handler) "HTML регистрирует document-слушатель сообщений")
        (handler #js {:data (js/JSON.stringify payload)})
        (is (> @calls before) "канвасы перерисованы")
        (is (= "2 points • 1 кнопок" (.-textContent pill))))
      (restore))))

(deftest webview-message-garbage-ignored-test
  (testing "некорректное сообщение не роняет страницу"
    (let [{:keys [calls doc-listeners restore]} (stub-env)]
      (js/eval (script-of offline/html))
      (let [before @calls
            handler (get @doc-listeners "message")]
        (handler #js {:data "не-json"})
        (handler #js {:data "{\"unrelated\":true}"})
        (is (= before @calls) "мусор игнорируется, канвасы не тронуты"))
      (restore))))
