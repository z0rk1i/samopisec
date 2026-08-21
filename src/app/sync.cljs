(ns app.sync
  "Пуш CSV на Grafana-хост для телефона (физ. устройство не может adb pull).
   Grafana читает /tmp/samopisec.csv на хосте, телефон POST'ит туда csv."
  (:require [clojure.string :as str]
            [app.csv :as csv]
            [app.storage :as storage]
            [react-native :as rn]))

(defn- grafana-urls
  []
  (let [extra (try
                (let [^js c (js/require "expo-constants")]
                  (or (.-extra (.-expoConfig (.-default c)))
                      (.-extra (.-manifest (.-default c)))
                      (.-extra (.-manifest2 (.-default c)))
                      #js {}))
                (catch :default _ #js {}))
        debugger-host (try
                        (let [^js c (js/require "expo-constants")
                              ^js d (.-default c)]
                          (or (.-hostUri ^js (.-expoConfig ^js d))
                              (.-hostUri ^js (.-manifest ^js d))
                              (.-debuggerHost ^js (.-expoGo ^js (.-extra ^js (.-manifest2 ^js d))))
                              (.-debuggerHost ^js (.-manifest ^js d))))
                        (catch :default _ nil))
        lan-ip (when debugger-host
                 (let [h (str/replace (str debugger-host) #"^.*://" "")
                       host (first (str/split h #":"))]
                   (when (re-matches #"\d+\.\d+\.\d+\.\d+" host) host)))
        base (or (aget extra "grafanaUrl")
                 (when lan-ip (str "http://" lan-ip ":3000"))
                 "http://10.0.2.2:3000")
        uploader (or (aget extra "grafanaUploaderUrl")
                     (when lan-ip (str "http://" lan-ip ":8002"))
                     "http://10.0.2.2:8002")
        platform (try (.-OS ^js rn/Platform) (catch :default _ "ios"))]
    {:grafana base
     :uploader uploader
     :platform platform
     :lan-ip lan-ip
     :debugger-host debugger-host}))

(defn grafana-dashboard-url
  "URL дашборда для WebView (kiosk, без логина — anonymous Viewer)."
  []
  (let [{:keys [grafana]} (grafana-urls)]
    (str grafana "/d/samopisec/samopisec?orgId=1&kiosk&from=now-7d&to=now")))

(defn uploader-url []
  (str (:uploader (grafana-urls)) "/upload"))

(defn push-csv!
  "Пушит текущий CSV на uploader. Best-effort, ошибки глотаются."
  []
  (-> (storage/read-datapoints)
      (.then (fn [{:keys [dps]}]
               (let [body (csv/serialize-csv dps)
                     url (uploader-url)]
                 (js/fetch url
                           #js {:method "POST"
                                :headers #js {"Content-Type" "text/csv"}
                                :body body}))))
      (.then (fn [^js res]
               (when res
                 (when-not (.-ok res)
                   (js/console.warn (str "samopisec sync push failed: " (.-status res)))))))
      (.catch (fn [e]
                (js/console.warn "samopisec sync push failed" e)))))

(defonce ^:private timer (atom nil))

(defn start-periodic-push!
  "Запускает периодический push каждые 30s когда app active. Идемпотентно."
  []
  (when-not @timer
    (reset! timer
            (js/setInterval
             (fn []
               (when (= "active" (some-> ^js rn/AppState .-currentState))
                 (push-csv!)))
             30000))))

(defn stop-periodic-push! []
  (when @timer
    (js/clearInterval @timer)
    (reset! timer nil)))
