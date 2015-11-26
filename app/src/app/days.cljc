(ns app.days
  #?(:clj
     (:require [clj-time.core :as time]
               [clj-time.format :as format]
               [clj-time.periodic :as time-periodic]
               [clj-time.predicates :as time-predicates]
               [clj-time.format :as format]))
  #?(:cljs (:require [cljs-time.core :as time]
             [cljs-time.format :as format]
             [cljs-time.periodic :as time-periodic]
             [cljs-time.predicates :as time-predicates])))

(def german-timestamp-formatter (format/formatter "dd.MM.yyyy"))

(def yyyy-MM-dd-formatter (format/formatter "yyyy-MM-dd"))

(defn this-year []
  (time/year (time/today)))

(defn last-day-of-year [day-in-year]
  (time/date-time (time/year day-in-year) 12 31))

(defn day-at-midnight [day]
  (time/date-midnight (time/year day) (time/month day) (time/day day)))

(defn today-at-midnight []
  (day-at-midnight (time/now)))

(defn days-till-end-of-year [today]
  (time-periodic/periodic-seq today (time/plus (last-day-of-year today) (time/days 1)) (time/days 1)))

(defn workdays-till-end-of-year [today]
  (->> (days-till-end-of-year today)
       (remove time-predicates/weekend?)))

(defn format-simple-date [date]
  ;(format/unparse-local-date timestamp-formatter date)
  date)

(defn month-start-ends [start-day end-day]
  ;; todo handle ranges across years
  (let [year (time/year start-day)
        end-day-midnight (day-at-midnight end-day)
        start-ends-without-last-month
        (->> (range (time/month start-day) (time/month end-day))
             (mapv (juxt (partial time/first-day-of-the-month year) (partial time/last-day-of-the-month year))))]
    (conj start-ends-without-last-month [(time/first-day-of-the-month end-day-midnight) end-day-midnight])))

(defn as-yyyy-MM-dd [date]
  (format/unparse yyyy-MM-dd-formatter date))
