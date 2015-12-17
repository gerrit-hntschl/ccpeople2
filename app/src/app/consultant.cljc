(ns app.consultant
  (:require #?@(:cljs [[cljs-time.core :as time]]
                :clj [[clj-time.core :as time]])))

(defn current-period-start [today]
  ;; period is closed on 5th of next month, unless in January
  (if (and (<= (time/day today) 5) (> (time/month today) 1))
    (-> today
        (time/minus (-> 1 (time/months)))
        (time/first-day-of-the-month))
    (time/first-day-of-the-month today)))
