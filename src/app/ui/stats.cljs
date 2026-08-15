(ns app.ui.stats
  "Экран статистики: итоги, серия, лучший день, счётчики по кнопкам, heatmap по часам."
  (:require [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [react-native :as rn]
            [app.theme :as theme]))

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
          "Статистика")
       ($ rn/View {:style {:flex-direction :row :margin-bottom 12}}
          ($ stat-card {:title "Всего" :value (str (:total totals))})
          ($ stat-card {:title "Серия" :value (if (zero? streak) "—" (str streak))
                        :sub (if (zero? streak) "нет дней подряд" "дней подряд")})
          ($ stat-card {:title "Лучший день" :value best-label}))
       ($ rn/Text {:style {:font-size 16 :font-weight "600" :color (:text t)
                           :margin-top 8 :margin-bottom 8}}
          "По кнопкам")
       (if (empty? buttons)
         ($ rn/Text {:style {:font-size 15 :color (:text-faint t)}}
            "Нет кнопок — добавьте в разделе «Кнопки».")
         (for [b buttons]
           ($ button-total-row {:key (:id b) :label (:label b) :color (:color b)
                                :count (get (:by-button totals) (:id b) 0)})))
       ($ rn/Text {:style {:font-size 16 :font-weight "600" :color (:text t)
                           :margin-top 16 :margin-bottom 8}}
          "По часам")
       ($ rn/View {:style {:flex-direction :row :flex-wrap :wrap}}
          (for [h (range 24)]
            (let [cnt (nth heatmap h)
                  intensity (/ cnt (max 1 max-hour))]
              ($ rn/View {:key h
                          :style {:width "11.5%" :aspect-ratio 1
                                  :margin "1.3%"
                                  :border-radius 4 :align-items :center
                                  :justify-content :center
                                  :background-color (if (zero? cnt)
                                                      (:card t)
                                                      (hex->rgba (:accent t)
                                                                 (max 0.15 (* 0.9 intensity))))}}
                 ($ rn/Text {:style {:font-size 11 :color (if (> intensity 0.4)
                                                            (:text-on-accent t)
                                                            (:text-secondary t))}}
                    (str h)))))))))