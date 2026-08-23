(ns app.ui.grafana
  "Экран «Графики»: offline-Grafana в WebView. Данные считает приложение
   (app.grafana-series на базе app.math) и инжектит готовый JSON
   window.SAMOPISEC_SERIES; HTML внутри — только рендер. Источник WebView
   мемоизирован по payload: перезагрузка страницы только при смене данных."
  (:require [uix.core :refer [$ defui use-memo]]
            [uix.re-frame :refer [use-subscribe]]
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
        payload (use-memo (fn [] (grafana-series/series-payload dps buttons))
                          [dps buttons])
        injected (str "window.SAMOPISEC_SERIES=" (js/JSON.stringify (clj->js payload)) ";")]
    ($ rn/View {:style {:flex 1 :background-color (:bg t)}}
       ($ WebView {:source #js {:html (str offline/html "<script>" injected "</script>") :baseUrl ""}
                   :style {:flex 1 :backgroundColor (:bg t)}
                   :javaScriptEnabled true
                   :domStorageEnabled true
                   :allowFileAccess true
                   :originWhitelist #js ["*"]}))))
