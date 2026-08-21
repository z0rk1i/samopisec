(ns app.ui.grafana
  (:require [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [react-native :as rn]
            [app.theme :as theme]
            [app.csv :as csv]
            [app.offline-html :as offline]
            ["react-native-webview" :as rnv]))

(def ^:private WebView (.-WebView rnv))

(defui screen []
  (let [t (theme/use-theme)
        dps (use-subscribe [:datapoints])
        buttons (use-subscribe [:buttons])
        csv (csv/serialize-csv dps)
        injected (str "window.SAMOPISEC_CSV=" (js/JSON.stringify csv) ";"
                      "window.SAMOPISEC_BUTTONS=" (js/JSON.stringify (clj->js buttons)) ";"
                      "window.__SAMOPISEC_CSV=window.SAMOPISEC_CSV;"
                      "window.__SAMOPISEC_BUTTONS=window.SAMOPISEC_BUTTONS;")]
    ($ rn/View {:style {:flex 1 :background-color (:bg t)}}
       ($ WebView {:source #js {:html (str offline/html "<script>" injected "</script>") :baseUrl ""}
                   :style {:flex 1 :backgroundColor (:bg t)}
                   :javaScriptEnabled true
                   :domStorageEnabled true
                   :allowFileAccess true
                   :originWhitelist #js ["*"]
                   :onMessage (fn [_] nil)}))))
