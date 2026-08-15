(ns app.ui.stats
  "Экран статистики: итоги, серия, лучший день, счётчики по кнопкам, heatmap по часам."
  (:require [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [react-native :as rn]
            [app.theme :as theme]
            [app.i18n :as i18n]))

(defn- hex->rgba
  [hex a]
  (let [h (subs hex 1)
        r (js/parseInt (subs h 0 2) 16)
        g (js/parseInt (subs h 2 4) 16)
        b (js/parseInt (subs h 4 6) 16)]
    (str "rgba(" r "," g "," b "," a ")")))

(defui stat-card [{:keys [title value sub]}]
  (let [t (theme/use-theme)]
    ($ rn/View {:style {:flex 1 :padding 12 :border-width 1 :border-color (:border t)
                        :border-radius 10 :background-color (:card t)
                        :margin-horizontal 4}}
       ($ rn/Text {:style {:font-size 13 :color (:text-secondary t)}} title)
       ($ rn/Text {:style {:font-size 26 :font-weight "700" :color (:text t) :margin-top 4}}
          value)
       (when sub
         ($ rn/Text {:style {:font-size 12 :color (:text-faint t) :margin-top 2}} sub)))))

(defui button-total-row [{:keys [label color count]}]
  (let [t (theme/use-theme)]
    ($ rn/View {:style {:flex-direction :row :align-items :center
                        :padding 8 :border-width 1 :border-color (:border t)
                        :border-radius 8 :margin-bottom 6 :background-color (:card t)}}
       ($ rn/View {:style {:width 14 :height 14 :border-radius 7
                           :background-color color :margin-right 10}})
       ($ rn/Text {:style {:flex 1 :font-size 15 :color (:text t)}} label)
       ($ rn/Text {:style {:font-size 15 :font-weight "600" :color (:text t)}} (str count)))))

(defui screen []
  (let [t (theme/use-theme)
        buttons (use-subscribe [:buttons])
        totals (use-subscribe [:stats/totals])
        streak (use-subscribe [:stats/streak])
        best (use-subscribe [:stats/best-day])
        heatmap (use-subscribe [:stats/heatmap])
        max-hour (apply max 1 heatmap)
        best-label (if best
                     (let [[y m d] (:date best)]
                       (str d "." m "." (mod y 100) " — " (:count best)))
                     "—")]
    ($ rn/View {:style {:flex 1 :padding 16 :background-color (:bg t)}}
       ($ rn/Text {:style {:font-size 24 :font-weight "700" :color (:text t)
                           :margin-bottom 16}}
        (i18n/t :tabs/stats)))
       ($ rn/View {:style {:flex-direction :row :margin-bottom 12}}
          ($ stat-card {:title (i18n/t :stats/total) :value (str (:total totals))})
          ($ stat-card {:title (i18n/t :stats/streak) :value (if (zero? streak) "—" (str streak))
                        :sub (if (zero? streak) (i18n/t :stats/streak-zero) (i18n/tf :stats/streak-sub streak))})
          ($ stat-card {:title (i18n/t :stats/best-day) :value best-label}))
       ($ rn/Text {:style {:font-size 16 :font-weight "600" :color (:text t)
                            :margin-top 8 :margin-bottom 8}}
          (i18n/t :stats/by-buttons))
       ($ rn/View {:style {:flex-direction :row :align-items :center
                           :padding-vertical 6 :border-bottom-width 1
                           :border-bottom-color (:border t)}}
          ($ rn/Text {:style {:flex 1 :font-size 13 :color (:text-secondary t)}}
             (i18n/t :stats/button-col))
          ($ rn/Text {:style {:font-size 13 :color (:text-secondary t)}}
             (i18n/t :stats/count-col)))
       (if (empty? buttons)
         ($ rn/Text {:style {:font-size 15 :color (:text-faint t)}}
            (i18n/t :stats/by-buttons-empty))
         (for [b buttons]
           ($ button-total-row {:key (:id b) :label (:label b) :color (:color b)
                                :count (get (:by-button totals) (:id b) 0)})))
       ($ rn/Text {:style {:font-size 16 :font-weight "600" :color (:text t)
                            :margin-top 16 :margin-bottom 8}}
          (i18n/t :stats/by-hour))
       ($ rn/View {:style {:flex-direction :row :flex-wrap :wrap}}
          (for [h (range 24)]
(let [cnt (nth heatmap h)
                  intensity (/ cnt (max 1 max-hour))
                  txt-color (if (> intensity 0.4) (:text-on-accent t) (:text t))
                  dim-color (if (> intensity 0.4) (:text-on-accent t) (:text-faint t))]
              ($ rn/View {:key h
                          :style {:width "11.5%" :aspect-ratio 1
                                  :margin "1.3%"
                                  :border-radius 4 :align-items :center
                                  :justify-content :center
                                  :background-color (if (zero? cnt)
                                                      (:card t)
                                                      (hex->rgba (:accent t)
                                                                 (max 0.15 (* 0.9 intensity))))}}
                 ($ rn/Text {:style {:font-size 9 :color dim-color}}
                    (str h))
                 ($ rn/Text {:style {:font-size 13 :font-weight "700" :color txt-color}}
                    (if (zero? cnt) "" (str cnt)))))))))