(ns app.db
  "re-frame: состояние, события, подписки и селекторы данных для графиков."
  (:require [re-frame.core :as rf]
            [app.storage :as storage]
            [app.widget :as widget]
            [app.selectors :as selectors]))

(defn default-db
  []
  {:screen :config
   :buttons []
   :datapoints []
   :chart {:range :day
           :button-id :all
           :show-rate false
           :show-accel false}})

;; ---- events ---------------------------------------------------------------

(rf/reg-event-db
 :app/init
 (fn [_ _]
   (default-db)))

(rf/reg-event-fx
 :storage/load
 (fn [_ _]
   (-> (storage/read-datapoints)
       (.then #(rf/dispatch [:data/loaded %]))
       (.catch #(rf/dispatch [:data/loaded []])))
   (-> (storage/read-config)
       (.then #(rf/dispatch [:config/loaded %]))
       (.catch #(rf/dispatch [:config/loaded {:buttons []}])))
   nil))

(rf/reg-event-db
 :config/loaded
 (fn [db [_ cfg]]
   (assoc db :buttons (or (:buttons cfg) []))))

(rf/reg-event-db
 :data/loaded
 (fn [db [_ dps]]
   (assoc db :datapoints (or dps []))))

(rf/reg-event-db
 :config/add
 (fn [db [_ label color]]
   (let [btn {:id (storage/new-id) :label label :color color}]
     (assoc db :buttons (conj (:buttons db) btn)))))

(rf/reg-event-db
 :config/remove
 (fn [db [_ id]]
   (assoc db :buttons (vec (remove #(= id (:id %)) (:buttons db))))))

(rf/reg-event-db
 :config/update
 (fn [db [_ id patch]]
   (assoc db :buttons
          (mapv (fn [b] (if (= id (:id b)) (merge b patch) b))
                (:buttons db)))))

(rf/reg-fx
 :storage/save-config
 (fn [cfg]
   (storage/write-config! cfg)))

(rf/reg-event-fx
 :config/commit
 (fn [{:keys [db]} _]
   {:db db
    :storage/save-config (select-keys db [:buttons])
    :widget/refresh nil}))

(rf/reg-fx
 :widget/refresh
 (fn [_]
   (widget/refresh-widgets!)))

(rf/reg-event-fx
 :data/record
 (fn [{:keys [db]} [_ button-id]]
   (let [dp {:id (storage/new-id) :button-id button-id :ts (js/Date.now)}]
     (storage/append-datapoint! dp)
     {:db (update db :datapoints conj dp)})))

(rf/reg-event-db
 :screen/set
 (fn [db [_ screen]]
   (assoc db :screen screen)))

(rf/reg-event-db
 :chart/set
 (fn [db [_ k v]]
   (assoc-in db [:chart k] v)))

;; ---- subs -----------------------------------------------------------------

(rf/reg-sub
 :screen
 (fn [db _] (:screen db)))

(rf/reg-sub
 :buttons
 (fn [db _] (:buttons db)))

(rf/reg-sub
 :datapoints
 (fn [db _] (:datapoints db)))

(rf/reg-sub
 :chart
 (fn [db _] (:chart db)))

(rf/reg-sub
 :chart/series
 (fn [db _]
   (selectors/series (:chart db) (:datapoints db) (js/Date.now))))

(rf/reg-sub
 :today/counts
 (fn [db _]
   (selectors/today-counts (:datapoints db) (js/Date.now))))
