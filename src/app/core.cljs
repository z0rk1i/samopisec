(ns app.core
  (:require [react-native :as rn]
            ["expo" :as expo]
            [react-native-safe-area-context :as safe-area]
            [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [app.db]
            [app.events.data]
            [app.events.config]
            [app.events.grafana]
            [app.theme :as theme]
            [app.storage :as storage]
            [app.ui.config :as config-screen]
            [app.ui.grafana :as grafana-screen]
            [app.i18n :as i18n]))

(defui tab-bar []
  (let [t (theme/use-theme)
        screen (use-subscribe [:screen])
        insets (safe-area/useSafeAreaInsets)
        set-opt! #(rf/dispatch [:screen/set %])]
    ($ rn/View {:style {:flex-direction :row :border-top-width 1
                        :border-top-color (:border t)
                        :padding-bottom (+ 8 (.-bottom insets))}}
       (for [[k label] [[:charts (i18n/t :tabs/charts)]
                 [:config (i18n/t :tabs/config)]]]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! k)
                          :accessibility-label label
                          :accessibility-role "tab"
                          :style {:flex 1 :padding-vertical 12 :align-items :center
                                  :background-color (if (= k screen) (:accent-soft t) (:card t))}}
            ($ rn/Text {:style {:font-size 16
                                :color (if (= k screen) (:accent t) (:text-secondary t))
                                :font-weight (if (= k screen) "600" "400")}}
               label))))))

(defui error-banner []
  (let [t (theme/use-theme)
        err (use-subscribe [:storage/error])]
    (when err
      ($ rn/Pressable {:on-press #(rf/dispatch [:storage/error-dismiss])
                       :accessibility-label (i18n/t :storage/error-dismiss)
                       :style {:background-color (:danger t)
                               :padding 12 :margin-horizontal 16 :margin-bottom 8
                               :border-radius 8}}
         ($ rn/Text {:style {:color (:text-on-accent t) :font-size 13}} err)))))

(defui content []
  (let [screen (use-subscribe [:screen])
        insets (safe-area/useSafeAreaInsets)]
    ($ rn/View {:style {:flex 1 :padding-top (.-top insets)}}
       ;; Экраны смонтированы постоянно, переключение — display: Grafana-WebView
       ;; не пересоздаётся и не перезагружается при смене вкладки.
       ($ rn/View {:style {:flex 1 :display (if (= screen :charts) :flex :none)}}
          ($ grafana-screen/screen))
       ($ rn/View {:style {:flex 1 :display (if (= screen :config) :flex :none)}}
          ($ config-screen/screen))
       ($ error-banner)
       ($ tab-bar))))

(defui root []
  ($ safe-area/SafeAreaProvider
     ($ content)))

(defn ^:export init []
  (js/console.log "samopisec storage:" (js/JSON.stringify (clj->js (storage/storage-location))))
  (rf/dispatch-sync [:app/init])
  (rf/dispatch [:storage/load])
  (when (exists? (.-AppState rn))
    (let [app-state (.-AppState rn)]
      (.addEventListener app-state "change"
                         (fn [state]
                           (when (= state "active")
                             (rf/dispatch [:storage/load]))))))
  (expo/registerRootComponent root))
