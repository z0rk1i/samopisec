(ns app.ui.charts
  (:require [clojure.string :as str]
            [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]
            ["react-native-svg" :as svg]))

(defonce W 350.0)
(defonce H 140.0)
(defonce pad 18.0)

(def ranges [{:k :day :label "24ч"}
             {:k :week :label "7д"}
             {:k :month :label "30д"}
             {:k :all :label "всё"}])

(defn- scale-x [t start end]
  (let [span (max 1.0 (- end start))]
    (+ pad (* (- W (* 2 pad)) (/ (- t start) span)))))

(defn- polyline [xs ys]
  (str/join " " (map (fn [x y] (str (.toFixed x 1) "," (.toFixed y 1)))
                     xs ys)))

(defn- panel
  "Одна панель: точки {:x :y} в координатах канвы, цвет, лейбл."
  [label points color]
  (if (empty? points)
    ($ svg/Text {:x 8 :y 16 :fill "#999" :font-size 12} (str label ": нет данных"))
    (let [ys (map :y points)
          maxy (apply max 0.0 ys)
          miny (apply min 0.0 ys)
          span (max 1.0 (- maxy miny))
          norm (fn [i]
                 [(:x (nth points i)) (- H pad (* (- H (* 2 pad))
                                                  (/ (- (:y (nth points i)) miny) span)))])]
      ($ svg/Svg {:width W :height H}
         ($ svg/Line {:x1 pad :y1 (- H pad) :x2 (- W pad) :y2 (- H pad)
                      :stroke "#ddd" :stroke-width 1})
         ($ svg/Line {:x1 pad :y1 pad :x2 pad :y2 (- H pad)
                      :stroke "#ddd" :stroke-width 1})
         ($ svg/Text {:x 8 :y 12 :fill "#666" :font-size 11} label)
         ($ svg/Polyline {:points (apply str (polyline (map #(first (norm %)) (range (count points)))
                                                        (map #(second (norm %)) (range (count points)))))
                          :fill "none" :stroke color :stroke-width 2})))))

(defn- cumulative-panel
  "Кумулятивная кривая: [[t n] ...]."
  [cum start end]
  (let [maxn (apply max 0 (map second cum))
        pts (map (fn [[t n]]
                   {:x (scale-x t start end)
                    :y (- H pad (* (- H (* 2 pad)) (if (zero? maxn) 0 (/ n maxn))))})
                 cum)]
    (panel "Накопленные нажатия" pts "#1976d2")))

(defn- rate-panel
  "Скорость нажатий (1-я производная): [{:t :rate} ...]."
  [rates start end]
  (let [pts (map (fn [{:keys [t rate]}]
                   {:x (scale-x t start end)
                    :y rate})
                 rates)]
    (panel "Скорость (1/ч)" pts "#43a047")))

(defn- accel-panel
  "Ускорение (2-я производная): [число...], равномерно по бин-time."
  [accel rates start end]
  (let [n (count accel)
        xs (if (seq rates)
             (map (fn [i] (scale-x (get-in rates [i :t]) start end)) (range n))
             (map (fn [i] (+ pad (* (/ (inc i) (max 1 n)) (- W (* 2 pad))))) (range n)))
        pts (map (fn [i x] {:x x :y (nth accel i)}) (range n) xs)]
    (panel "Ускорение (Δ/час²)" pts "#8e24aa")))

(defui range-chips []
  (let [chart (use-subscribe [:chart])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :margin-bottom 8}}
       (for [{:keys [k label]} ranges]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! :range k)
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8
                                  :background-color (if (= k (:range chart)) "#1976d2" "#eee")}}
            ($ rn/Text {:style {:color (if (= k (:range chart)) "#fff" "#333")
                                :font-size 14}} label))))))

(defui button-chips []
  (let [chart (use-subscribe [:chart])
        buttons (use-subscribe [:buttons])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :flex-wrap :wrap :margin-bottom 8}}
       ($ rn/Pressable {:on-press #(set-opt! :button-id :all)
                        :style {:padding-horizontal 12 :padding-vertical 6
                                :border-radius 16 :margin-right 8 :margin-bottom 4
                                :background-color (if (= :all (:button-id chart)) "#1976d2" "#eee")}}
          ($ rn/Text {:style {:color (if (= :all (:button-id chart)) "#fff" "#333")}}
             "Все"))
       (for [b buttons]
         ($ rn/Pressable {:key (:id b)
                          :on-press #(set-opt! :button-id (:id b))
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8 :margin-bottom 4
                                  :background-color (if (= (:id b) (:button-id chart)) "#1976d2" "#eee")}}
            ($ rn/Text {:style {:color (if (= (:id b) (:button-id chart)) "#fff" "#333")}}
               (:label b)))))))

(defui toggles []
  (let [chart (use-subscribe [:chart])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :margin-bottom 12}}
       (for [[k label] [[:show-rate "Скорость"] [:show-accel "Ускорение"]]]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! k (not (get chart k)))
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8
                                  :background-color (if (get chart k) "#8e24aa" "#eee")}}
            ($ rn/Text {:style {:color (if (get chart k) "#fff" "#333") :font-size 14}}
               label))))))

(defui screen []
  (let [series (use-subscribe [:chart/series])
        chart (use-subscribe [:chart])]
    ($ rn/View {:style {:flex 1 :padding 16}}
       ($ rn/Text {:style {:font-size 24 :font-weight "700" :margin-bottom 12}}
          "Графики")
       ($ range-chips)
       ($ button-chips)
       ($ toggles)
       ($ rn/ScrollView {:style {:flex 1}}
          ($ cumulative-panel {:cum (:cumulative series)
                               :start (:start series) :end (:end series)})
          (when (:show-rate chart)
            ($ rate-panel {:rates (:rate series)
                           :start (:start series) :end (:end series)}))
          (when (:show-accel chart)
            ($ accel-panel {:accel (:accel series)
                            :rates (:rate series)
                            :start (:start series) :end (:end series)}))))))