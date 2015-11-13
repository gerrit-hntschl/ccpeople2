(ns app.days
  #?(:clj
     (:require [clj-time.core :as time]
               [clj-time.periodic :as time-periodic]
               [clj-time.predicates :as time-predicates]))
  #?(:cljs (:require [cljs-time.core :as time]
             [cljs-time.periodic :as time-periodic]
             [cljs-time.predicates :as time-predicates])))


(defn this-year []
  (time/year (time/today)))

(defn last-day-of-year [day-in-year]
  (time/date-time (time/year day-in-year) 12 31))

(defn today-at-midnight []
  (let [now (time/now)]
    (time/date-midnight (time/year now) (time/month now) (time/day now))))

(defn days-till-end-of-year [today]
  (time-periodic/periodic-seq today (time/plus (last-day-of-year today) (time/days 1)) (time/days 1)))

(defn workdays-till-end-of-year [today]
  (->> (days-till-end-of-year today)
       (remove time-predicates/weekend?)))