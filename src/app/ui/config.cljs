(ns app.ui.config
  (:require [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]))

(defonce colors
  ["#e53935" "#fb8c00" "#fdd835" "#43a047"
   "#1e88e5" "#8e24aa" "#5e35b1" "#757575"])

(defui add-button-form []
  (let [[*label set-label!] (uix.core/use-state "")
        [*color set-color!] (uix.core/use-state (first colors))]
    ($ rn/View {:style {:margin-bottom 16}}
       ($ rn/Text {:style {:font-size 16 :font-weight "600" :margin-bottom 8}}
          "Новая кнопка")
       ($ rn/TextInput {:value *label
                        :on-change-text set-label!
                        :placeholder "Название (например «Чай»)"
                        :style {:border-width 1 :border-color "#ccc"
                                :border-radius 8 :padding 10
                                :font-size 16}})
       ($ rn/View {:style {:flex-direction :row :margin-top 8 :flex-wrap :wrap}}
          (for [c colors]
            ($ rn/Pressable {:key c
                             :on-press #(set-color! c)
                             :style {:width 32 :height 32 :border-radius 16
                                     :background-color c :margin-right 8
                                     :border-width (if (= c *color) 3 0)
                                     :border-color "#000"}})))
       ($ rn/Pressable {:on-press (fn []
                                    (when (seq *label)
                                      (rf/dispatch [:config/add *label *color])
                                      (rf/dispatch [:config/commit])
                                      (set-label! "")
                                      (set-color! (first colors))))
                        :style {:background-color "#1976d2" :padding 12
                                :border-radius 8 :margin-top 12
                                :align-items :center}}
          ($ rn/Text {:style {:color "#fff" :font-size 16 :font-weight "600"}}
             "Добавить")))))

(defui button-row [{:keys [id label color count]}]
  (let [record! #(do (rf/dispatch [:data/record id]) (rf/dispatch [:config/commit]))
        remove! #(do (rf/dispatch [:config/remove id]) (rf/dispatch [:config/commit]))]
    ($ rn/View {:style {:flex-direction :row :align-items :center
                        :padding 10 :border-width 1 :border-color "#e0e0e0"
                        :border-radius 8 :margin-bottom 8}}
       ($ rn/View {:style {:width 16 :height 16 :border-radius 8
                           :background-color color :margin-right 10}})
       ($ rn/Text {:style {:flex 1 :font-size 16}} label)
       ($ rn/Text {:style {:font-size 14 :color "#888" :margin-right 12}}
          (if (zero? count) "0" (str count)))
       ($ rn/Pressable {:on-press record!
                        :style {:padding 8 :background-color "#43a047"
                                :border-radius 6 :margin-right 8}}
          ($ rn/Text {:style {:color "#fff"}} "Жми"))
       ($ rn/Pressable {:on-press remove!
                        :style {:padding 8 :background-color "#eee"
                                :border-radius 6}}
          ($ rn/Text {:style {:color "#c62828"}} "✕")))))

(defui screen []
  (let [buttons (use-subscribe [:buttons])
        counts (use-subscribe [:today/counts])]
    ($ rn/View {:style {:flex 1 :padding 16}}
       ($ rn/View {:style {:flex-direction :row :align-items :baseline
                           :margin-bottom 16}}
          ($ rn/Text {:style {:font-size 24 :font-weight "700" :margin-right 8}}
             "Кнопки")
          ($ rn/Text {:style {:font-size 14 :color "#888"}}
             (str "сегодня: " (:total counts))))
       ($ add-button-form)
       ($ rn/Text {:style {:font-size 14 :color "#888" :margin-bottom 8}}
          "Нажатия с виджета появятся здесь после синхронизации.")
       (if (empty? buttons)
         ($ rn/Text {:style {:font-size 16 :color "#aaa"}}
            "Пока нет кнопок — добавьте первую выше.")
         (for [b buttons]
           ($ button-row {:key (:id b) :id (:id b)
                          :label (:label b) :color (:color b)
                          :count (get (:by-button counts) (:id b) 0)}))))))
