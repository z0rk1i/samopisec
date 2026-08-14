(ns app.core
  (:require [react-native :as rn]
            ["expo" :as expo]
            [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [app.db]
            [app.storage :as storage]
            [app.ui.config :as config-screen]
            [app.ui.charts :as charts-screen]))

(defui tab-bar []
  (let [screen (use-subscribe [:screen])
        set-opt! #(rf/dispatch [:screen/set %])]
    ($ rn/View {:style {:flex-direction :row :border-top-width 1
                        :border-top-color "#e0e0e0"}}
       (for [[k label] [[:charts "Графики"] [:config "Кнопки"]]]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! k)
                          :style {:flex 1 :padding-vertical 12 :align-items :center
                                  :background-color (if (= k screen) "#f0f0f0" "#fff")}}
            ($ rn/Text {:style {:font-size 16
                                :color (if (= k screen) "#1976d2" "#666")
                                :font-weight (if (= k screen) "600" "400")}}
               label))))))

(defui root []
  (let [screen (use-subscribe [:screen])]
    ($ rn/View {:style {:flex 1 :padding-top 44}}
       (if (= screen :charts)
         ($ charts-screen/screen)
         ($ config-screen/screen))
       ($ tab-bar))))

(defn ^:export init []
  (rf/dispatch-sync [:app/init])
  (rf/dispatch [:storage/load])
  (when (exists? (.-AppState rn))
    (let [app-state (.-AppState rn)]
      (.addEventListener app-state "change"
                         (fn [state]
                           (when (= state "active")
                             (rf/dispatch [:storage/load]))))))
  (expo/registerRootComponent root))
