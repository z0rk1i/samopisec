(ns app.events.chart
  "re-frame: события и подписки графиков (диапазон, фильтр по кнопке, тумблеры)
   и селекторы данных. Отделено от app.db по смыслу."
  (:require [re-frame.core :as rf]
            [app.clock :as clock]
            [app.selectors :as selectors]))

(rf/reg-event-db
 :chart/set
 (fn [db [_ k v]]
   (assoc-in db [:chart k] v)))

(rf/reg-sub
 :chart
 (fn [db _] (:chart db)))

(rf/reg-sub
 :chart/series
 (fn [db _]
   (selectors/series (:chart db) (:datapoints db) (clock/now-ms))))

(rf/reg-sub
 :today/counts
 (fn [db _]
   (selectors/today-counts (:datapoints db) (clock/now-ms))))