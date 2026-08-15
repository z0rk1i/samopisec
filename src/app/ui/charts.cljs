(ns app.ui.charts
  (:require [clojure.string :as str]
            [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]
            [app.chart-geom :as geom]
            [app.theme :as theme]
            [app.i18n :refer [t tf]]
            ["react-native-svg" :as svg]))

(defonce pad 20.0)
(defonce H 150.0)
(defonce card-radius 12.0)

(def ^:private max-polyline-points
  "Ограничение числа точек полилинии SVG — децимация в chart-card."
  400)

(def ranges [{:k :day :label (t :charts/range-day)}
             {:k :week :label (t :charts/range-week)}
             {:k :month :label (t :charts/range-month)}
             {:k :all :label (t :charts/range-all)}])

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

(defui chart-card
  "Карточка графика: сетка, подписи осей, полилиния и опциональная заливка.
   props: {:title :points {:x :y в канвас-координатах} :color :fill? :start :end}"
  [{:keys [title points color fill? start end]}]
  (let [t (theme/use-theme)
        {:keys [width]} (rn/useWindowDimensions)
        W (max 200.0 (- width 32.0))
        pts-raw (geom/decimate points max-polyline-points)
        n (geom/norm-points pts-raw H pad)
        pts (:pts n)
        maxy (:maxy n)
        miny (:miny n)
        line (polyline-pts pts)
        area (when (and fill? (seq pts))
               (str line " " (.toFixed (- W pad) 1) "," (.toFixed (- H pad) 1)
                    " " (.toFixed pad 1) "," (.toFixed (- H pad) 1)))]
    ($ rn/View {:style {:background-color (:card t) :border-radius card-radius
                        :padding 4 :margin-bottom 12
                        :shadow-color "#000" :shadow-opacity 0.06
                        :shadow-radius 4 :shadow-offset #js {:width 0 :height 2}
                        :elevation 2}}
       ($ rn/Text {:style {:font-size 13 :font-weight "600" :color (:text t)
                           :margin-horizontal 8 :margin-top 6}}
          title)
       ($ svg/Svg {:width W :height H}
          ;; горизонтальные линии сетки (0/25/50/75/100%)
          (for [f [0 0.25 0.5 0.75 1]]
            ($ svg/Line {:key f
                         :x1 pad :y1 (+ pad (* f (- H (* 2 pad))))
                         :x2 (- W pad) :y2 (+ pad (* f (- H (* 2 pad))))
                         :stroke (if (or (zero? f) (= f 1.0)) (:grid-line t) (:grid-line-soft t))
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
          ($ svg/Text {:x 4 :y 13 :fill (:chart-label t) :font-size 10}
             (fmt-count maxy))
          ;; подпись минимума
          ($ svg/Text {:x 4 :y (- H 4) :fill (:chart-label t) :font-size 10}
             (fmt-count miny))
          ;; подписи времени по X
          (when (and start end)
            ($ svg/Text {:x pad :y (- H 4) :fill (:chart-time t) :font-size 10}
               (fmt-time start))
            ($ svg/Text {:x (- W pad) :y (- H 4) :fill (:chart-time t) :font-size 10
                         :text-anchor "end"}
               (fmt-time end)))))))

(defui cumulative-panel
  "Кумулятивная кривая: [[t n] ...]. Y передаётся в сыром виде (количество),
   нормализацию делает chart-card."
  [{:keys [cum start end W]}]
  (let [t (theme/use-theme)
        pts (map (fn [[t n]]
                   {:x (geom/scale-x t start end W pad)
                    :y n})
                 cum)]
    (if (empty? pts)
      ($ rn/Text {:style {:color (:chart-label t) :font-size 13 :margin-bottom 12}}
         (t :charts/cumulative-empty))
      ($ chart-card {:title (t :charts/cumulative-title) :points pts
                     :color (:accent t) :fill? true
                     :start start :end end}))))

(defui rate-panel
  "Скорость нажатий (1-я производная): [{:t :rate} ...]."
  [{:keys [rates start end W]}]
  (let [t (theme/use-theme)
        pts (map (fn [{:keys [t rate]}]
                   {:x (geom/scale-x t start end W pad)
                    :y rate})
                 rates)]
    (if (empty? pts)
      ($ rn/Text {:style {:color (:chart-label t) :font-size 13 :margin-bottom 12}}
         (t :charts/rate-empty))
      ($ chart-card {:title (t :charts/rate-title) :points pts
                     :color (:success t)
                     :start start :end end}))))

(defui accel-panel
  "Ускорение (2-я производная): [число...], равномерно по бин-time."
  [{:keys [accel rates start end W]}]
  (let [t (theme/use-theme)
        n (count accel)
        xs (if (seq rates)
             (map (fn [i] (geom/scale-x (get-in rates [i :t]) start end W pad)) (range n))
             (map (fn [i] (+ pad (* (/ (inc i) (max 1 n)) (- W (* 2 pad))))) (range n)))
        pts (map (fn [i x] {:x x :y (nth accel i)}) (range n) xs)]
    (if (empty? pts)
      ($ rn/Text {:style {:color (:chart-label t) :font-size 13 :margin-bottom 12}}
         (t :charts/accel-empty))
      ($ chart-card {:title (t :charts/accel-title) :points pts
                     :color (:purple t)
                     :start start :end end}))))

(defui range-chips []
  (let [t (theme/use-theme)
        chart (use-subscribe [:chart])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :margin-bottom 8}}
       (for [{:keys [k label]} ranges]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! :range k)
                          :accessibility-label (tf :charts/range-accessibility label)
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8
                                  :background-color (if (= k (:range chart)) (:accent t) (:accent-soft t))}}
            ($ rn/Text {:style {:color (if (= k (:range chart)) (:text-on-accent t) (:text t))
                                :font-size 14}} label))))))

(defui button-chips []
  (let [t (theme/use-theme)
        chart (use-subscribe [:chart])
        buttons (use-subscribe [:buttons])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :flex-wrap :wrap :margin-bottom 8}}
($ rn/Pressable {:on-press #(set-opt! :button-id :all)
                        :accessibility-label (t :charts/filter-all-accessibility)
                        :style {:padding-horizontal 12 :padding-vertical 6
                                :border-radius 16 :margin-right 8 :margin-bottom 4
                                :background-color (if (= :all (:button-id chart)) (:accent t) (:accent-soft t))}}
           ($ rn/Text {:style {:color (if (= :all (:button-id chart)) (:text-on-accent t) (:text t))}}
              (t :charts/filter-all)))
       (for [b buttons]
         ($ rn/Pressable {:key (:id b)
                          :on-press #(set-opt! :button-id (:id b))
                          :accessibility-label (tf :charts/filter-accessibility (:label b))
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8 :margin-bottom 4
                                  :background-color (if (= (:id b) (:button-id chart)) (:accent t) (:accent-soft t))}}
            ($ rn/Text {:style {:color (if (= (:id b) (:button-id chart)) (:text-on-accent t) (:text t))}}
               (:label b)))))))

(defui toggles []
  (let [t (theme/use-theme)
        chart (use-subscribe [:chart])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :margin-bottom 12}}
       (for [[k label] [[:show-rate (t :charts/show-rate)] [:show-accel (t :charts/show-accel)]]]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! k (not (get chart k)))
                          :accessibility-label label
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8
                                  :background-color (if (get chart k) (:purple t) (:accent-soft t))}}
            ($ rn/Text {:style {:color (if (get chart k) (:text-on-accent t) (:text t)) :font-size 14}}
               label))))))

(defui screen []
  (let [t (theme/use-theme)
        {:keys [width]} (rn/useWindowDimensions)
        W (max 200.0 (- width 32.0))
        series (use-subscribe [:chart/series])
        chart (use-subscribe [:chart])]
    ($ rn/View {:style {:flex 1 :padding 16 :background-color (:bg t)}}
       ($ rn/Text {:style {:font-size 24 :font-weight "700" :color (:text t) :margin-bottom 12}}
          (t :charts/title))
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