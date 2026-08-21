(ns app.db
  "re-frame: состояние приложения (default-db) и низкоуровневые события/подписки
   жизни цикла: инициализация, загрузка из файлов, навигация по экранам, ошибки
   хранилища. Логика данных/конфига/графиков вынесена в app.events.* (см.
   app.events.data, app.events.config, app.events.chart)."
  (:require [re-frame.core :as rf]
            [app.clock :as clock]
            [app.storage :as storage]
            [app.selectors :as selectors]))

(defn default-db
  []
  {:screen :charts
   :buttons []
   :datapoints []
   :config/dirty false
   :storage/error nil
   :chart {:range :day
           :button-id :all
           :show-rate false
           :show-accel false}})

(rf/reg-event-db
 :app/init
 (fn [_ _]
   (default-db)))

(rf/reg-event-fx
 :storage/load
 (fn [_ _]
   (-> (storage/read-datapoints)
       (.then #(rf/dispatch [:data/loaded %]))
       (.catch (fn [e]
                 (storage/report-error! "read-datapoints" e)
                 (rf/dispatch [:data/loaded {:dps [] :main-count 0}]))))
   (-> (storage/read-config)
       (.then #(rf/dispatch [:config/loaded %]))
       (.catch (fn [e]
                 (storage/report-error! "read-config" e)
                 (rf/dispatch [:config/loaded {:buttons []}]))))
   nil))

(rf/reg-event-db
 :storage/error
 (fn [db [_ msg]]
   (assoc db :storage/error msg)))

(rf/reg-event-db
 :storage/error-dismiss
 (fn [db _]
   (assoc db :storage/error nil)))

(rf/reg-event-db
 :screen/set
 (fn [db [_ screen]]
   (assoc db :screen screen)))

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
 :storage/error
 (fn [db _] (:storage/error db)))

(rf/reg-sub
 :stats/totals
 (fn [db _]
   (selectors/per-button-totals (:datapoints db))))

(rf/reg-sub
 :stats/streak
 (fn [db _]
   (selectors/current-streak (:datapoints db) (clock/now-ms))))
(rf/reg-sub
 :stats/best-day
 (fn [db _]
   (selectors/best-day (:datapoints db))))

(rf/reg-sub
 :stats/heatmap
 (fn [db _]
   (selectors/per-hour-heatmap (:datapoints db))))