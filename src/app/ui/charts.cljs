(ns app.ui.charts
  (:require [clojure.string :as str]
            [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]
            [app.chart-geom :as geom]
            [app.timeline :as timeline]
            [app.theme :as theme]
            [app.i18n :as i18n]
            ["react-native-svg" :as svg]))

(defonce card-radius 12.0)

(def ranges [{:k :day :label (i18n/t :charts/range-day)}
             {:k :week :label (i18n/t :charts/range-week)}
             {:k :month :label (i18n/t :charts/range-month)}
             {:k :all :label (i18n/t :charts/range-all)}])

(defn- fmt-axis
  [t range]
  (when t
    (if (= range :day)
      (.toLocaleTimeString (js/Date. t) #js {:hour "2-digit" :minute "2-digit"})
      (.toLocaleDateString (js/Date. t) #js {:day "2-digit" :month "2-digit"}))))

(defn- fmt-count
  [n]
  (let [abs (js/Math.abs n)]
    (cond
      (>= abs 1000) (str (.toFixed (/ n 1000) 1) "k")
      (= n (js/Math.round n)) (str (js/Math.round n))
      :else (.toFixed n 1))))

(defn- polyline-pts
  [pts]
  (str/join " " (map (fn [{:keys [x y]}]
                       (str (.toFixed x 1) "," (.toFixed y 1)))
                     pts)))

(defui multi-chart-card
  "Карточка графика с N линиями разных цветов (по кнопкам) на одном канвасе.
   props: {:title :lines [{:points [{:x :y}] :color :label}] :start :end :range}"
  [{:keys [title lines start end range]}]
  (let [t (theme/use-theme)
        {:keys [width]} (rn/useWindowDimensions)
        W (max 200.0 (- width 32.0))
        pad geom/pad
        H geom/chart-h
        all-ys (mapcat (fn [{:keys [points]}] (map :y points)) lines)
        maxy (apply max 0.0 (or (seq all-ys) [0.0]))
        miny (apply min 0.0 (or (seq all-ys) [0.0]))
        span (max 1e-9 (- maxy miny))
        norm-y (fn [y] (- H pad (* (- H (* 2 pad)) (/ (- y miny) span))))
        normed-lines (mapv (fn [{:keys [points color label]}]
                             {:color color :label label
                              :pts (mapv (fn [{:keys [x y]}] {:x x :y (norm-y y)}) points)
                              :raw-points points})
                           lines)]
    ($ rn/View {:style {:background-color (:card t) :border-radius card-radius
                        :padding 4 :margin-bottom 12
                        :shadow-color "#000" :shadow-opacity 0.06
                        :shadow-radius 4 :shadow-offset #js {:width 0 :height 2}
                        :elevation 2}}
       ($ rn/Text {:style {:font-size 13 :font-weight "600" :color (:text t)
                           :margin-horizontal 8 :margin-top 6}}
          title)
       ;; легенда
       ($ rn/View {:style {:flex-direction :row :flex-wrap :wrap :margin-horizontal 8 :margin-bottom 4 :gap 8}}
          (for [{:keys [label color]} lines]
            ($ rn/View {:key label :style {:flex-direction :row :align-items :center :margin-right 10}}
               ($ rn/View {:style {:width 10 :height 10 :border-radius 5 :background-color color :margin-right 6}})
               ($ rn/Text {:style {:font-size 11 :color (:text-secondary t)}} label))))
       ($ svg/Svg {:width W :height H}
          (for [f [0 0.25 0.5 0.75 1]]
            ($ svg/Line {:key f
                         :x1 pad :y1 (+ pad (* f (- H (* 2 pad))))
                         :x2 (- W pad) :y2 (+ pad (* f (- H (* 2 pad))))
                         :stroke (if (or (zero? f) (= f 1.0)) (:grid-line t) (:grid-line-soft t))
                         :stroke-width 1}))
          (for [{:keys [pts color]} normed-lines]
            (let [line (polyline-pts pts)
                  area (when (seq pts)
                         (str line " " (.toFixed (- W pad) 1) "," (.toFixed (- H pad) 1)
                              " " (.toFixed pad 1) "," (.toFixed (- H pad) 1)))]
              ($ svg/G {:key color}
                 (when area
                   ($ svg/Polygon {:points area :fill color :fill-opacity 0.08}))
                 ($ svg/Polyline {:points line :fill "none" :stroke color :stroke-width 2.5
                                  :stroke-linejoin "round" :stroke-linecap "round"}))))
          ($ svg/Text {:x 4 :y 13 :fill (:chart-label t) :font-size 10} (fmt-count maxy))
          ($ svg/Text {:x 4 :y (- H 4) :fill (:chart-label t) :font-size 10} (fmt-count miny))
          (when (and start end)
            ($ svg/Text {:x pad :y (- H 4) :fill (:chart-time t) :font-size 10} (fmt-axis start range))
            ($ svg/Text {:x (- W pad) :y (- H 4) :fill (:chart-time t) :font-size 10 :text-anchor "end"} (fmt-axis end range)))))))

(defui cumulative-panel
  [{:keys [series-per-button range W]}]
  (let [t (theme/use-theme)
        lines (->> series-per-button
                   (keep (fn [{:keys [label color series]}]
                           (let [pts (timeline/points {:series series :k :cumulative :start (:start series) :end (:end series) :W W})]
                             (when (seq pts) {:points pts :color color :label label}))))
                   vec)
        start (some-> series-per-button first :series :start)
        end (some-> series-per-button first :series :end)]
    (if (empty? lines)
      ($ rn/Text {:style {:color (:chart-label t) :font-size 13 :margin-bottom 12}} (i18n/t :charts/cumulative-empty))
      ($ multi-chart-card {:title (i18n/t :charts/cumulative-title) :lines lines :start start :end end :range range}))))

(defui rate-panel
  [{:keys [series-per-button range W]}]
  (let [t (theme/use-theme)
        lines (->> series-per-button
                   (keep (fn [{:keys [label color series]}]
                           (let [pts (timeline/points {:series series :k :rate :start (:start series) :end (:end series) :W W})]
                             (when (seq pts) {:points pts :color color :label label}))))
                   vec)
        start (some-> series-per-button first :series :start)
        end (some-> series-per-button first :series :end)]
    (if (empty? lines)
      ($ rn/Text {:style {:color (:chart-label t) :font-size 13 :margin-bottom 12}} (i18n/t :charts/rate-empty))
      ($ multi-chart-card {:title (i18n/t :charts/rate-title) :lines lines :start start :end end :range range}))))

(defui accel-panel
  [{:keys [series-per-button range W]}]
  (let [t (theme/use-theme)
        lines (->> series-per-button
                   (keep (fn [{:keys [label color series]}]
                           (let [pts (timeline/points {:series series :k :accel :start (:start series) :end (:end series) :W W})]
                             (when (seq pts) {:points pts :color color :label label}))))
                   vec)
        start (some-> series-per-button first :series :start)
        end (some-> series-per-button first :series :end)]
    (if (empty? lines)
      ($ rn/Text {:style {:color (:chart-label t) :font-size 13 :margin-bottom 12}} (i18n/t :charts/accel-empty))
      ($ multi-chart-card {:title (i18n/t :charts/accel-title) :lines lines :start start :end end :range range}))))

(defui range-chips []
  (let [t (theme/use-theme)
        chart (use-subscribe [:chart])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :margin-bottom 8}}
       (for [{:keys [k label]} ranges]
         ($ rn/Pressable {:key k
                          :on-press #(set-opt! :range k)
                          :accessibility-label (i18n/tf :charts/range-accessibility label)
                          :style {:padding-horizontal 12 :padding-vertical 6
                                  :border-radius 16 :margin-right 8
                                  :background-color (if (= k (:range chart)) (:accent t) (:accent-soft t))}}
            ($ rn/Text {:style {:color (if (= k (:range chart)) (:text-on-accent t) (:text t))
                                :font-size 14}} label))))))

(defui toggles []
  (let [t (theme/use-theme)
        chart (use-subscribe [:chart])
        set-opt! #(rf/dispatch [:chart/set %1 %2])]
    ($ rn/View {:style {:flex-direction :row :margin-bottom 12}}
       (for [[k label] [[:show-rate (i18n/t :charts/show-rate)] [:show-accel (i18n/t :charts/show-accel)]]]
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
        series-per-button (use-subscribe [:chart/series-per-button])
        chart (use-subscribe [:chart])]
    ($ rn/View {:style {:flex 1 :padding 16 :background-color (:bg t)}}
       ($ rn/Text {:style {:font-size 24 :font-weight "700" :color (:text t) :margin-bottom 12}}
          (i18n/t :charts/title))
       ($ range-chips)
       ($ toggles)
       ($ rn/ScrollView {:style {:flex 1}}
          ($ cumulative-panel {:series-per-button series-per-button :range (:range chart) :W W})
          (when (:show-rate chart)
            ($ rate-panel {:series-per-button series-per-button :range (:range chart) :W W}))
          (when (:show-accel chart)
            ($ accel-panel {:series-per-button series-per-button :range (:range chart) :W W}))))))
