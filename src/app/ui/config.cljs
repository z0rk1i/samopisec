(ns app.ui.config
  (:require [uix.core :refer [$ defui]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [react-native :as rn]
            [app.storage :as storage]
            [app.contract :as contract]
            [app.theme :as theme]
            [app.i18n :as i18n]))

(defonce colors
  ["#e53935" "#fb8c00" "#fdd835" "#43a047"
   "#1e88e5" "#8e24aa" "#5e35b1" "#757575"])

(defui add-button-form []
  (let [t (theme/use-theme)
        [*label set-label!] (uix.core/use-state "")
        [*color set-color!] (uix.core/use-state (first colors))
        buttons (use-subscribe [:buttons])
        at-limit? (>= (count buttons) contract/max-buttons)]
    ($ rn/View {:style {:margin-bottom 16}}
       ($ rn/View {:style {:flex-direction :row :align-items :baseline
                           :margin-bottom 8}}
          ($ rn/Text {:style {:font-size 16 :font-weight "600" :color (:text t)
                              :margin-right 8}}
             (i18n/t :add/title))
          ($ rn/Text {:style {:font-size 13 :color (:text-secondary t)}}
             (str (count buttons) "/" contract/max-buttons)))
       ($ rn/TextInput {:value *label
                        :on-change-text set-label!
                        :placeholder (i18n/t :add/name-placeholder)
                        :placeholder-text-color (:text-faint t)
                        :style {:border-width 1 :border-color (:input-border t)
                                :border-radius 8 :padding 10
                                :font-size 16 :color (:text t)
                                :background-color (:card t)}})
       ($ rn/View {:style {:flex-direction :row :margin-top 8 :flex-wrap :wrap}}
          (for [c colors]
            ($ rn/Pressable {:key c
                             :on-press #(set-color! c)
                             :accessibility-label (i18n/tf :color/a11y c)
                             :style {:width 32 :height 32 :border-radius 16
                                     :background-color c :margin-right 8
                                     :border-width (if (= c *color) 3 0)
                                     :border-color "#000"}})))
       (when at-limit?
         ($ rn/Text {:style {:color (:danger t) :font-size 13 :margin-top 8}}
            (i18n/t :add/limit)))
       ($ rn/Pressable {:on-press (fn []
                                    (when (and (seq *label) (not at-limit?))
                                      (rf/dispatch [:config/add *label *color])
                                      (rf/dispatch [:config/commit])
                                      (set-label! "")
                                      (set-color! (first colors))))
                        :style {:background-color (if (or at-limit? (empty? *label))
                                                    (:text-faint t) (:accent t))
                                :padding 12 :border-radius 8 :margin-top 12
                                :align-items :center}}
          ($ rn/Text {:style {:color (:text-on-accent t) :font-size 16 :font-weight "600"}}
             (i18n/t :add/save))))))

(defui edit-button-form [{:keys [id label color on-save on-cancel]}]
  (let [t (theme/use-theme)
        [*label set-label!] (uix.core/use-state label)
        [*color set-color!] (uix.core/use-state color)]
    ($ rn/View {:style {:border-width 1 :border-color (:border t) :border-radius 8
                        :padding 10 :background-color (:card t) :margin-top 8}}
       ($ rn/TextInput {:value *label
                        :on-change-text set-label!
                        :placeholder (i18n/t :edit/name-placeholder)
                        :placeholder-text-color (:text-faint t)
                        :style {:border-width 1 :border-color (:input-border t)
                                :border-radius 8 :padding 8 :font-size 15
                                :color (:text t) :background-color (:bg t)}})
       ($ rn/View {:style {:flex-direction :row :margin-top 8 :flex-wrap :wrap}}
          (for [c colors]
            ($ rn/Pressable {:key c
                             :on-press #(set-color! c)
                             :accessibility-label (i18n/tf :color/a11y c)
                             :style {:width 24 :height 24 :border-radius 12
                                     :background-color c :margin-right 6 :margin-bottom 6
                                     :border-width (if (= c *color) 2 0)
                                     :border-color "#000"}})))
       ($ rn/View {:style {:flex-direction :row :margin-top 8 :align-items :center}}
          ($ rn/Text {:style {:font-size 13 :color (:text-secondary t) :margin-right 10}}
(i18n/t :edit/order))
           ($ rn/Pressable {:on-press (fn []
                                        (rf/dispatch [:config/move id :up])
                                       (rf/dispatch [:config/commit]))
                           :accessibility-label (i18n/t :edit/move-up)
                           :style {:padding 6 :border-width 1 :border-color (:input-border t)
                                   :border-radius 6 :margin-right 6}}
             ($ rn/Text {:style {:font-size 14 :color (:accent t)}} "↑"))
          ($ rn/Pressable {:on-press (fn []
                                       (rf/dispatch [:config/move id :down])
                                       (rf/dispatch [:config/commit]))
                           :accessibility-label (i18n/t :edit/move-down)
                           :style {:padding 6 :border-width 1 :border-color (:input-border t)
                                   :border-radius 6}}
             ($ rn/Text {:style {:font-size 14 :color (:accent t)}} "↓")))
       ($ rn/View {:style {:flex-direction :row :margin-top 8}}
          ($ rn/Pressable {:on-press #(on-save *label *color)
                           :accessibility-label (i18n/t :edit/save-changes)
                           :style {:padding 8 :background-color (:accent t)
                                   :border-radius 6 :margin-right 8}}
             ($ rn/Text {:style {:color (:text-on-accent t) :font-size 14}} (i18n/t :edit/save))
          ($ rn/Pressable {:on-press on-cancel
                           :accessibility-label (i18n/t :edit/cancel-edit)
                           :style {:padding 8 :border-width 1 :border-color (:input-border t)
                                   :border-radius 6}}
             ($ rn/Text {:style {:color (:text-secondary t) :font-size 14}} (i18n/t :edit/cancel))))))))

(defui button-row [{:keys [id label color count]}]
  (let [t (theme/use-theme)
        [*editing? set-editing!] (uix.core/use-state false)
        record! #(rf/dispatch [:data/record id])
        save! (fn [new-label new-color]
                (when (seq new-label)
                  (rf/dispatch [:config/update id {:label new-label :color new-color}])
                  (rf/dispatch [:config/commit]))
                (set-editing! false))
        remove! (fn []
                  (rn/Alert.alert
                   (i18n/t :delete/title)
                   (i18n/tf :delete/body label)
                   (clj->js [{:text (i18n/t :delete/abort) :style "cancel"}
                             {:text (i18n/t :delete/confirm) :style "destructive"
                              :onPress (fn []
                                         (rf/dispatch [:config/remove id])
                                         (rf/dispatch [:config/commit]))}])))]
    ($ rn/View {:style {:margin-bottom 8}}
       ($ rn/View {:style {:flex-direction :row :align-items :center
                           :padding 10 :border-width 1 :border-color (:border t)
                           :border-radius 8 :margin-bottom 0
                           :background-color (:card t)}}
          ($ rn/View {:style {:width 16 :height 16 :border-radius 8
                              :background-color color :margin-right 10}})
          ($ rn/Text {:style {:flex 1 :font-size 16 :color (:text t)}} label)
          ($ rn/Text {:style {:font-size 14 :color (:text-secondary t) :margin-right 12}}
             (if (zero? count) "0" (str count)))
          ($ rn/Pressable {:on-press record!
:accessibility-label (i18n/tf :record/label label)
                           :style {:padding 8 :background-color (:success t)
                                   :border-radius 6 :margin-right 8}}
              ($ rn/Text {:style {:color (:text-on-accent t)}} (i18n/t :record/button)))
          ($ rn/Pressable {:on-press #(set-editing! (not *editing?))
                           :accessibility-label (i18n/tf :edit/button-label label)
                           :style {:padding 8 :background-color (:remove-bg t)
                                   :border-radius 6 :margin-right 8}}
             ($ rn/Text {:style {:color (:accent t)}} "✎"))
          ($ rn/Pressable {:on-press remove!
                           :accessibility-label (i18n/tf :delete/button-label label)
                           :style {:padding 8 :background-color (:remove-bg t)
                                   :border-radius 6}}
             ($ rn/Text {:style {:color (:danger t)}} "✕")))
       (when *editing?
         ($ edit-button-form {:id id :label label :color color
                              :on-save save! :on-cancel #(set-editing! false)})))))

(defui screen []
  (let [t (theme/use-theme)
        buttons (use-subscribe [:buttons])
        counts (use-subscribe [:today/counts])
        datapoints (use-subscribe [:datapoints])
        can-undo? (seq datapoints)
        export! (fn []
                  (-> (js/Promise.all #js [(storage/read-config) (storage/read-datapoints)])
                      (.then (fn [res]
                               (let [data (clj->js {:export-version 1
                                                    :exported-at (js/Date.now)
                                                    :config (aget res 0)
                                                    :datapoints (:dps (aget res 1))})]
                                 (.share (.-Share rn) #js {:message (js/JSON.stringify data)}))))
                      (.catch (fn [e] (storage/report-error! "export" e)))))]
    ($ rn/View {:style {:flex 1 :padding 16 :background-color (:bg t)}}
       ($ rn/View {:style {:flex-direction :row :align-items :baseline
                           :margin-bottom 16}}
          ($ rn/Text {:style {:font-size 24 :font-weight "700" :color (:text t)
                              :margin-right 8}}
(i18n/t :tabs/config))
           ($ rn/Text {:style {:font-size 14 :color (:text-secondary t)}}
              (str (i18n/t :header/today) " " (:total counts)))
          ($ rn/View {:style {:flex 1}})
          ($ rn/Pressable {:on-press #(rf/dispatch [:data/undo])
                           :accessibility-label (i18n/t :undo/label)
                           :disabled (not can-undo?)
                           :style {:padding 8 :border-width 1
                                   :border-color (if can-undo? (:input-border t) (:border t))
                                   :border-radius 6 :margin-right 8}}
             ($ rn/Text {:style {:color (if can-undo? (:accent t) (:text-faint t))
                                 :font-size 14}}
(i18n/t :undo)))
           ($ rn/Pressable {:on-press export!
                           :accessibility-label (i18n/t :export/label)
                           :style {:padding 8 :border-width 1 :border-color (:input-border t)
                                   :border-radius 6}}
             ($ rn/Text {:style {:color (:accent t) :font-size 14}} (i18n/t :export))))
       ($ add-button-form)
       ($ rn/Text {:style {:font-size 14 :color (:text-secondary t) :margin-bottom 8}}
          (i18n/t :config/sync-hint))
(if (empty? buttons)
          ($ rn/View {:style {:border-width 1 :border-color (:border t)
                              :border-radius 10 :padding 16
                              :background-color (:card t) :margin-top 8}}
             ($ rn/Text {:style {:font-size 16 :font-weight "600" :color (:text t)}}
                (i18n/t :empty/title))
             ($ rn/Text {:style {:font-size 14 :color (:text-secondary t)
                                 :margin-top 8 :line-height 20}}
                (i18n/t :empty/step1)
                "\n"
                (i18n/t :empty/step2)
                "\n"
                (i18n/t :empty/step3)
                "\n"
                (i18n/t :empty/hint)))
          (for [b buttons]
            ($ button-row {:key (:id b) :id (:id b)
                           :label (:label b) :color (:color b)
                           :count (get (:by-button counts) (:id b) 0)}))))))