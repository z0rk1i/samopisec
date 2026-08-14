(ns app.ui.charts
  (:require [clojure.string :as str]
            [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]
            ["react-native-svg" :as svg]))

(defonce pad 20.0)
(defonce H 150.0)
(defonce card-radius 12.0)

(def ranges [{:k :day :label "24ч"}
             {:k :week :label "7д"}
             {:k :month :label "30д"}
             {:k :all :label "всё"}])

(defn- fmt-time
  "ЧЧ:ММ по метке времени (мс)."
  [t]
  (when t
    (.toLocaleTimeString (js/Date. t) #js {:hour "2-digit" :minute "2-digit"})))

(defn- fmt-count
  "Компактное число: 12.5 -> 12, 1234 -> 1.2k."
  [n]
  (let [abs (js/Math.abs n)]
    (cond
      (>= abs 1000) (str (.toFixed (/ n 1000) 1) "k")
      (= n (js/Math.round n)) (str (js/Math.round n))
      :else (.toFixed n 1))))

(defn- polyline-pts
  "Строка «x1,y1 x2,y2 ...» из точек {:x :y}."
  [pts]
  (str/join " " (map (fn [{:keys [x y]}]
                       (str (.toFixed x 1) "," (.toFixed y 1)))
                     pts)))

(defn- norm-points
  "Нормализует точки {:x :y} (в канвас-координатах) к высоте панели H.
   Возвращает {:pts упорядоченные точки, :maxy :miny}."
  [points]
  (let [ys (map :y points)
        maxy (apply max 0.0 ys)
        miny (apply min 0.0 ys)
        span (max 1e-9 (- maxy miny))
        norm (fn [i]
               {:x (:x (nth points i))
                :y (- H pad (* (- H (* 2 pad)) (/ (- (:y (nth points i)) miny) span)))})]
    {:pts (mapv norm (range (count points)))
     :maxy maxy
     :miny miny}))

(defn- scale-x
  "X канваса для времени t в [start end] при ширине W."
  [t start end W]
  (let [span (max 1.0 (- end start))]
    (+ pad (* (- W (* 2 pad)) (/ (- t start) span)))))

(defui chart-card
  "Карточка графика: сетка, подписи осей, полилиния и опциональная заливка.
   props: {:title :points {:x :y в канвас-координатах} :color :fill? :start :end}"
  [{:keys [title points color fill? start end]}]
  (let [{:keys [width]} (rn/useWindowDimensions)
        W (max 200.0 (- width 32.0))
        n (norm-points points)
        pts (:pts n)
        maxy (:maxy n)
        miny (:miny n)
        line (polyline-pts pts)
        area (when (and fill? (seq pts))
               (str line " " (.toFixed (- W pad) 1) "," (.toFixed (- H pad) 1)
                    " " (.toFixed pad 1) "," (.toFixed (- H pad) 1)))]
    ($ rn/View {:style {:background-color "#fff" :border-radius card-radius
                        :padding 4 :margin-bottom 12
                        :shadow-color "#000" :shadow-opacity 0.06
                        :shadow-radius 4 :shadow-offset #js {:width 0 :height 2}
                        :elevation 2}}
       ($ rn/Text {:style {:font-size 13 :font-weight "600" :color "#555"
                           :margin-horizontal 8 :margin-top 6}}
          title)
       ($ svg/Svg {:width W :height H}
          ;; горизонтальные линии сетки (0/25/50/75/100%)
          (for [f [0 0.25 0.5 0.75 1]]
            ($ svg/Line {:key f
                         :x1 pad :y1 (+ pad (* f (- H (* 2 pad))))
                         :x2 (- W pad) :y2 (+ pad (* f (- H (* 2 pad))))
                         :stroke (if (or (zero? f) (= f 1.0)) "#ddd" "#f0f0f0")
                         :stroke-width 1}))
          ;; заливка под кривой
          (when area
            ($ svg/Polygon {:points area
                            :fill color :fill-opacity 0.08}))
          ;; полилиния
          ($ svg/Polyline {:points line
                           :fill "none" :stroke color :stroke-width 2.5
                           :stroke-linejoin "round" :stroke-linecap "round"})
          ;; подпись максимума
          ($ svg/Text {:x 4 :y 13 :fill "#999" :font-size 10}
             (fmt-count maxy))
          ;; подпись минимума
          ($ svg/Text {:x 4 :y (- H 4) :fill "#999" :font-size 10}
             (fmt-count miny))
          ;; подписи времени по X
          (when (and start end)
            ($ svg/Text {:x pad :y (- H 4) :fill "#bbb" :font-size 10}
               (fmt-time start))
            ($ svg/Text {:x (- W pad) :y (- H 4) :fill "#bbb" :font-size 10
                         :text-anchor "end"}
               (fmt-time end)))))))

(defui cumulative-panel
  "Кумулятивная кривая: [[t n] ...]. Y передаётся в сыром виде (количество),
   нормализацию делает chart-card."
  [{:keys [cum start end W]}]
  (let [pts (map (fn [[t n]]
                   {:x (scale-x t start end W)
                    :y n})
                 cum)]
    (if (empty? pts)
      ($ rn/Text {:style {:color "#999" :font-size 13 :margin-bottom 12}}
         "Накопленные нажатия: нет данных за выбранный период")
      ($ chart-card {:title "Накопленные нажатия" :points pts
                     :color "#1976d2" :fill? true
                     :start start :end end}))))

(defui rate-panel
  "Скорость нажатий (1-я производная): [{:t :rate} ...]."
  [{:keys [rates start end W]}]
  (let [pts (map (fn [{:keys [t rate]}]
                   {:x (scale-x t start end W)
                    :y rate})
                 rates)]
    (if (empty? pts)
      ($ rn/Text {:style {:color "#999" :font-size 13 :margin-bottom 12}}
         "Скорость (1/ч): нет данных")
      ($ chart-card {:title "Скорость (1/ч)" :points pts
                     :color "#43a047"
                     :start start :end end}))))

(defui accel-panel
  "Ускорение (2-я производная): [число...], равномерно по бин-time."
  [{:keys [accel rates start end W]}]
  (let [n (count accel)
        xs (if (seq rates)
             (map (fn [i] (scale-x (get-in rates [i :t]) start end W)) (range n))
             (map (fn [i] (+ pad (* (/ (inc i) (max 1 n)) (- W (* 2 pad))))) (range n)))
        pts (map (fn [i x] {:x x :y (nth accel i)}) (range n) xs)]
    (if (empty? pts)
      ($ rn/Text {:style {:color "#999" :font-size 13 :margin-bottom 12}}
         "Ускорение (Δ/час²): нет данных")
      ($ chart-card {:title "Ускорение (Δ/час²)" :points pts
                     :color "#8e24aa"
                     :start start :end end}))))

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
  (let [{:keys [width]} (rn/useWindowDimensions)
        W (max 200.0 (- width 32.0))
        series (use-subscribe [:chart/series])
        chart (use-subscribe [:chart])]
    ($ rn/View {:style {:flex 1 :padding 16 :background-color "#f5f5f7"}}
       ($ rn/Text {:style {:font-size 24 :font-weight "700" :margin-bottom 12}}
          "Графики")
       ($ range-chips)
       ($ button-chips)
       ($ toggles)
       ($ rn/ScrollView {:style {:flex 1}}
          ($ cumulative-panel {:cum (:cumulative series)
                               :start (:start series) :end (:end series) :W W})
          (when (:show-rate chart)
            ($ rate-panel {:rates (:rate series)
                           :start (:start series) :end (:end series) :W W}))
          (when (:show-accel chart)
            ($ accel-panel {:accel (:accel series)
                            :rates (:rate series)
                            :start (:start series) :end (:end series) :W W}))))))

