(ns app.events.config
  "re-frame: события и fx конфига кнопок — CRUD, грязный флаг, сохранение,
   обновление виджета. Отделено от app.db по смыслу."
  (:require [re-frame.core :as rf]
            [app.storage :as storage]
            [app.widget :as widget]
            [app.selectors :as selectors]))

(rf/reg-event-db
 :config/loaded
 (fn [db [_ cfg]]
   (assoc db :buttons (or (:buttons cfg) []))))

(rf/reg-event-db
 :config/add
 (fn [db [_ label color]]
   (let [btn {:id (storage/new-id) :label label :color color}]
     (-> db
         (assoc :config/dirty true)
         (assoc :buttons (conj (:buttons db) btn))))))

(rf/reg-event-db
 :config/remove
 (fn [db [_ id]]
   (-> db
       (assoc :config/dirty true)
       (assoc :buttons (vec (remove #(= id (:id %)) (:buttons db))))
       (update :chart selectors/chart-after-button-remove id))))

(rf/reg-event-db
 :config/update
 (fn [db [_ id patch]]
   (-> db
       (assoc :config/dirty true)
       (assoc :buttons
              (mapv (fn [b] (if (= id (:id b)) (merge b patch) b))
                    (:buttons db))))))

(rf/reg-event-db
 :config/move
 (fn [db [_ id dir]]
   (let [bs (:buttons db)
         i (some (fn [[idx b]] (when (= id (:id b)) idx))
                 (map-indexed vector bs))]
     (if (or (nil? i)
             (and (= dir :up) (zero? i))
             (and (= dir :down) (= i (dec (count bs)))))
       db
       (let [j (if (= dir :up) (dec i) (inc i))
             bs (vec (assoc bs i (nth bs j) j (nth bs i)))]
         (-> db
             (assoc :config/dirty true)
             (assoc :buttons bs)))))))

(rf/reg-fx
 :storage/save-config
 (fn [cfg]
   (storage/write-config! cfg)))

(rf/reg-event-fx
 :config/commit
 (fn [{:keys [db]} _]
   (if (:config/dirty db)
     {:db (assoc db :config/dirty false)
      :storage/save-config (select-keys db [:buttons])
      :widget/refresh nil}
     {:db db})))

(rf/reg-fx
 :widget/refresh
 (fn [_]
   (widget/refresh-widgets!)))