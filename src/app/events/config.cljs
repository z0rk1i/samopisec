(ns app.events.config
  "re-frame: события и fx конфига кнопок — CRUD, грязный флаг, сохранение,
  обновление виджета. Отделено от app.db по смыслу."
  (:require [re-frame.core :as rf]
            [app.storage :as storage]
            [app.widget :as widget]))

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
       (assoc :buttons (vec (remove #(= id (:id %)) (:buttons db)))))))

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
 ;; fx принимает {:cfg cfg :on-done f}: запись асинхронная (write-queue),
 ;; on-done вызывается ПОСЛЕ того как config.json реально записан.
 (fn [{:keys [cfg on-done]}]
   (-> (storage/write-config! cfg)
       (.then (fn [] (when on-done (on-done)))))))

(rf/reg-event-fx
 :config/commit
 (fn [{:keys [db]} _]
   (if (:config/dirty db)
     {:db (assoc db :config/dirty false)
      :storage/save-config
      {:cfg (select-keys db [:buttons])
       ;; Обновление виджета НЕЛЬЗЯ делать синхронно в этом же событии:
       ;; write-config! идёт через очередь записи, и виджет перечитал бы
       ;; config.json ДО фактической записи (гонка → старые кнопки).
       :on-done #(rf/dispatch [:widget/refresh])}}
     {:db db})))

(rf/reg-fx
 :widget/refresh
 (fn [_]
   (widget/refresh-widgets!)))

(rf/reg-event-fx
 :widget/refresh
 ;; Мост: событие -> fx. rf/dispatch требует обработчик СОБЫТИЯ; без него
 ;; диспатч молча ронялся и виджет не обновлялся (ADR-0017).
 (fn [_ _] {:widget/refresh nil}))
