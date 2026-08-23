(ns app.ui.grafana
  "Экран «Графики»: offline-Grafana в WebView. Данные считает приложение
   (app.grafana-series на базе app.math). Страница инжектится с кэшированным
   payload (:grafana/payload — переживает переключение вкладок), а обновления
   доставляются живому WebView через postMessage без пересборки html."
  (:require [uix.core :refer [$ defui use-memo use-effect use-ref]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]
            [app.theme :as theme]
            [app.grafana-series :as grafana-series]
            [app.offline-html :as offline]
            ["react-native-webview" :as rnv]))

(def ^:private WebView (.-WebView rnv))

(defui screen []
  (let [t (theme/use-theme)
        dps (use-subscribe [:datapoints])
        buttons (use-subscribe [:buttons])
        cached (use-subscribe [:grafana/payload])
        payload (use-memo (fn [] (grafana-series/series-payload dps buttons))
                          [dps buttons])
        wv (use-ref nil)]
    (use-effect (fn []
                  (rf/dispatch [:grafana/payload-set payload])
                  (when-some [w @wv]
                    (.postMessage w (js/JSON.stringify (clj->js payload)))))
                [payload])
    ($ rn/View {:style {:flex 1 :background-color (:bg t)}}
       ($ WebView {:ref #(reset! wv %)
                   :source #js {:html (str offline/html
                                           "<script>window.SAMOPISEC_SERIES="
                                           (js/JSON.stringify (clj->js (or cached (grafana-series/series-payload [] []))))
                                           ";</script>")
                                :baseUrl ""}
                   :style {:flex 1 :backgroundColor (:bg t)}
                   :javaScriptEnabled true
                   :domStorageEnabled true
                   :allowFileAccess true
                   :originWhitelist #js ["*"]}))))
