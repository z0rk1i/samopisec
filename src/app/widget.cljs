(ns app.widget
  "Мост к нативным виджетам. Android: WidgetBridge перерисовывает виджеты
   на домашнем экране. iOS: ExtensionStorage.reloadWidget() заставляет
   WidgetKit пересчитать таймлайн виджета."
  (:require ["react-native" :as rn]
            ["@bacons/apple-targets" :as bacon]))

(defn- android-module []
  (let [m (.. rn -NativeModules -WidgetBridge)]
    (when (and m (some? (aget m "refreshWidgets"))) m)))

(defn- ios-reload []
  (when-let [m (.-ExtensionStorage bacon)]
    (.reloadWidget m nil)))

(defn refresh-widgets!
  "Перерисовывает виджеты на домашнем экране."
  []
  (when-let [m (android-module)]
    (.refreshWidgets m))
  (ios-reload))
