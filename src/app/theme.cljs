(ns app.theme
  "Палитра цветов приложения. Один источник правды вместо хардкода
   в каждом экране; поддерживает light/dark по системной схеме."
  (:require [react-native :as rn]))

(def light
  {:bg "#f5f5f7"
   :card "#ffffff"
   :border "#e0e0e0"
   :input-border "#ccc"
   :text "#222"
   :text-secondary "#888"
   :text-faint "#aaa"
   :text-on-accent "#ffffff"
   :accent "#1976d2"
   :accent-soft "#eee"
   :success "#43a047"
   :danger "#c62828"
   :remove-bg "#eee"})

(def dark
  {:bg "#000000"
   :card "#1c1c1e"
   :border "#3a3a3c"
   :input-border "#48484a"
   :text "#f2f2f7"
   :text-secondary "#98989f"
   :text-faint "#636366"
   :text-on-accent "#ffffff"
   :accent "#4f9cf7"
   :accent-soft "#2c2c2e"
   :success "#30d158"
   :danger "#ff453a"
   :remove-bg "#2c2c2e"})

(defn use-theme
  "Палитра текущей темы (light/dark по системной схеме устройства)."
  []
  (let [scheme (rn/useColorScheme)]
    (if (= scheme "dark") dark light)))