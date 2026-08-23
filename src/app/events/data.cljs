(ns app.events.data
  "re-frame: события и fx дата-поинтов — запись тапа, undo, загрузка из файла,
   компакция. Отделено от app.db по смыслу (данные vs конфиг)."
  (:require [re-frame.core :as rf]
            [app.clock :as clock]
            [app.selectors :as selectors]
            [app.storage :as storage]))

(rf/reg-event-fx
 :data/loaded
 ;; merge вместо замены: оптимистичный тап, случившийся между постановкой
 ;; чтения в очередь и его резолвом, не вымывается из UI (дедупликация по :id).
 (fn [{:keys [db]} [_ {:keys [dps main-count]}]]
   (let [dps (selectors/merge-datapoints (:datapoints db) (or dps []))]
     (if (> (or main-count 0) storage/compact-threshold)
       {:db (assoc db :datapoints dps)
        :compact/run nil}
       {:db (assoc db :datapoints dps)}))))

(rf/reg-fx
 :compact/run
 (fn [_]
   (-> (storage/compact-datapoints!)
       (.then (fn [dropped]
                (when (pos? (or dropped 0))
                  (js/console.log (str "samopisec: компакция — в архив перенесено " dropped " точек"))
                  (rf/dispatch [:storage/load]))))
       (.catch (fn [e] (storage/report-error! "compact/run" e))))))

(rf/reg-event-fx
 :data/record
 (fn [{:keys [db]} [_ button-id]]
   (let [dp {:id (storage/new-id) :button-id button-id :ts (clock/now-ms)}]
     (storage/append-datapoint! dp)
     {:db (update db :datapoints conj dp)})))

(rf/reg-event-fx
 :data/undo
 (fn [{:keys [db]} _]
   (if (seq (:datapoints db))
     {:db db :storage/undo nil}
     nil)))

(rf/reg-fx
 :storage/undo
 (fn [_]
   (-> (storage/delete-last-datapoint!)
       (.then (fn [dp]
                (when dp
                  (rf/dispatch [:data/undone (:id dp)]))))
       (.catch (fn [e] (storage/report-error! "undo" e))))))

(rf/reg-event-fx
 :data/undone
 (fn [{:keys [db]} [_ id]]
   {:db (if (string? id)
          (let [dps (->> (:datapoints db)
                         (remove #(= (:id %) id))
                         vec)]
            (assoc db :datapoints dps))
          db)}))