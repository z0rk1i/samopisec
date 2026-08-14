(ns app.widget
  "Мост к нативному виджету (Android). refresh-widgets! перечитывает config.json
   и перерисовывает виджеты на домашнем экране. Безопасно no-op на iOS."
  (:require ["react-native" :as rn]))

(defn- native-module []
  (let [m (.. rn -NativeModules -WidgetBridge)]
    (when (and m (some? (aget m "refreshWidgets"))) m)))

(defn refresh-widgets!
  "Перерисовывает виджеты на домашнем экране."
  []
  (when-let [m (native-module)]
    (.refreshWidgets m)))
