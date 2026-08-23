(ns app.events.grafana
  "Кэш payload офлайн-дашборда в db: переживает размонтирование WebView при
  переключении вкладок — при возврате страница сразу инжектится с последними
  данными, без пустого рендера."
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
 :grafana/payload-set
 (fn [db [_ payload]]
   (assoc db :grafana/payload payload)))
