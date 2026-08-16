(ns app.clock
  "Единый источник времени приложения: now-ms (эпоха, мс) и границы
   календарного дня. В проде — реальные часы; set-now! позволяет подменить
   источник (тесты, демо) без правки логики.")

(defonce ^:private clock-fn (atom nil))

(defn now-ms
  "Текущее время в мс (эпоха). По умолчанию js/Date.now; set-now! подменяет."
  []
  (if-let [f @clock-fn]
    (f)
    (js/Date.now)))

(defn set-now!
  "Подменяет источник времени: функция без аргументов -> мс, либо nil, чтобы
   вернуть реальные часы."
  [f-or-nil]
  (reset! clock-fn f-or-nil))

(defn start-of-day
  "Метка начала календарного дня now-ms (локально), мс."
  [now-ms]
  (let [d (js/Date. now-ms)]
    (.setHours d 0 0 0 0)
    (.getTime d)))

(defn day-start-ms
  "Начало календарного дня с отступом days от now-ms (0 = сегодня)."
  [now-ms days]
  (let [d (js/Date. now-ms)]
    (.setHours d 0 0 0 0)
    (.setDate d (- (.getDate d) days))
    (.getTime d)))