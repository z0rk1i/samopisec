(ns app.offline-html-test
  "Живая копия для WebView (строка в app.offline-html) обязана совпадать с
   исходником assets/grafana-offline/index.html: правка HTML без перегенерации
   offline_html.cljs молча не попадает в приложение (перегенерация:
   node -e '...JSON.stringify...' из notes/handbook)."
  (:require [clojure.test :refer [deftest is]]
            [app.offline-html :as offline]))

(deftest offline-html-parity-test
  (let [src (.readFileSync (js/require "fs")
                           "assets/grafana-offline/index.html"
                           "utf8")]
    (is (= src offline/html)
        "offline_html.cljs устарел — перегенерируй его из assets/grafana-offline/index.html")))
