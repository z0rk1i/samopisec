(ns app.i18n
  "Словарь строк UI. Пока один язык (RU); структура позволяет добавить другие.
   t — строка по ключу, tf — с подстановкой %s."
  (:require [clojure.string :as str]))

(defonce ^:private locale (atom :ru))

(defn set-locale! [l] (reset! locale l))

(defn- ru
  []
  {:tabs/charts "Графики"
   :tabs/config "Кнопки"
   :tabs/stats "Статистика"

   :header/today "сегодня:"

   :add/title "Новая кнопка"
   :add/name-placeholder "Название (например «Чай»)"
   :add/limit "Достигнут лимит кнопок — виджет вмещает 6"
   :add/save "Добавить"

   :color/a11y "Цвет %s"

   :edit/name-placeholder "Название"
   :edit/order "Порядок:"
   :edit/move-up "Переместить выше"
   :edit/move-down "Переместить ниже"
   :edit/save "Сохранить"
   :edit/cancel "Отмена"
   :edit/save-changes "Сохранить изменения"
   :edit/cancel-edit "Отменить редактирование"

   :delete/title "Удалить кнопку?"
   :delete/body "«%s» будет удалена. История нажатий останется в данных."
   :delete/confirm "Удалить"
   :delete/abort "Отмена"

   :record/label "Записать нажатие «%s»"
   :record/button "Жми"
   :edit/button-label "Редактировать кнопку «%s»"
   :delete/button-label "Удалить кнопку «%s»"

   :undo/label "Отменить последнее нажатие"
   :undo "Отменить"

   :export/label "Экспорт данных"
   :export "Экспорт"

   :config/sync-hint "Нажатия с виджета появятся здесь после синхронизации."

   :empty/title "С чего начать"
   :empty/step1 "1. Добавьте кнопку выше — например «Чай» или «Кофе»."
   :empty/step2 "2. На главном экране: удерживайте палец → «Виджеты» → Samopisec."
   :empty/step3 "3. Тапайте по кнопкам на виджете — каждое нажатие записывается."
   :empty/hint "Графики и статистика обновляются автоматически."

   :stats/total "Всего"
   :stats/streak "Серия"
   :stats/streak-zero "нет дней подряд"
   :stats/streak-sub "%s дней подряд"
   :stats/best-day "Лучший день"
   :stats/by-buttons "По кнопкам"
   :stats/button-col "Кнопка"
   :stats/count-col "Нажатий"
   :stats/by-buttons-empty "Нет кнопок — добавьте в разделе «Кнопки»."
   :stats/by-hour "По часам"

   :charts/title "Графики"
   :charts/range-day "сегодня"
   :charts/range-week "7д"
   :charts/range-month "30д"
   :charts/range-all "всё"
   :charts/range-accessibility "Диапазон %s"
   :charts/cumulative-title "Накопленные нажатия"
   :charts/cumulative-empty "Накопленные нажатия: нет данных за выбранный период"
   :charts/rate-title "Скорость (1/ч)"
   :charts/rate-empty "Скорость (1/ч): нет данных"
   :charts/accel-title "Ускорение (Δ/час²)"
   :charts/accel-empty "Ускорение (Δ/час²): нет данных"
   :charts/filter-accessibility "Фильтр: %s"
   :charts/filter-all "Все"
   :charts/filter-all-accessibility "Фильтр: все кнопки"
   :charts/show-rate "Скорость"
   :charts/show-accel "Ускорение"

   :storage/error-dismiss "Закрыть"})

(defn t
  "Строка по ключу; отсутствующий ключ возвращается как есть."
  [k]
  (get (ru) k k))

(defn tf
  "Строка по ключу с подстановкой всех %s в аргументы по порядку."
  [k & args]
  (reduce (fn [s a] (str/replace s "%s" (str a))) (t k) args))