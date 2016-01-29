(ns app.days
  #?(:clj
     (:require [clj-time.core :as time]
               [clj-time.format :as format]
               [clj-time.periodic :as time-periodic]
               [clj-time.predicates :as time-predicates]
               [clj-time.format :as format]
               [clj-time.coerce :as time-coerce]))
  #?(:cljs (:require
             [cljs-time.core :as time]
             [cljs-time.format :as format]
             [cljs-time.periodic :as time-periodic]
             [cljs-time.predicates :as time-predicates]
             [cljs-time.coerce :as time-coerce]
             [goog.array :as garray])))

#?(:cljs
   (extend-type js/goog.date.Date
     IEquiv
     (-equiv [o other]
       (and (instance? js/goog.date.Date other)
            (.equals o other)))
     IHash
     (-hash [o]
       (+ (.getTime o)
          (* 37 (.getTimezoneOffset o))))
     IComparable
     (-compare [this other]
       (if (instance? js/goog.date.Date other)
         (garray/defaultCompare (.valueOf this) (.valueOf other))
         (throw (js/Error. (str "Cannot compare " this " to " other)))))))

(def german-timestamp-formatter (format/formatter "dd.MM.yyyy"))

(def holidays {2015 #{(time/local-date 2015 1 1)
                      (time/local-date 2015 4 3)
                      (time/local-date 2015 4 6)
                      (time/local-date 2015 5 1)
                      (time/local-date 2015 5 14)
                      (time/local-date 2015 5 25)
                      (time/local-date 2015 6 4)
                      (time/local-date 2015 10 3)
                      (time/local-date 2015 11 1)
                      (time/local-date 2015 12 25)
                      (time/local-date 2015 12 26)}
               2016 #{(time/local-date 2016 1 1)
                      (time/local-date 2016 3 25)
                      (time/local-date 2016 3 28)
                      (time/local-date 2016 5 1)
                      (time/local-date 2016 5 5)
                      (time/local-date 2016 5 16)
                      (time/local-date 2016 5 26)
                      (time/local-date 2016 10 3)
                      (time/local-date 2016 11 1)
                      (time/local-date 2016 12 25)
                      (time/local-date 2016 12 26)}})


(def yyyy-MM-dd-formatter (format/formatter "yyyy-MM-dd"))

(def month-day-formatter (format/formatter "MMM d"))

(defn month-day-today [today]
  (format/unparse-local-date month-day-formatter today))

(defn this-year []
  (time/year (time/today)))

(defn last-day-of-year [year]
  (time/local-date year 12 31))

(defn day-at-midnight [day]
  (time/date-midnight (time/year day) (time/month day) (time/day day)))

(defn today-at-midnight []
  (day-at-midnight (time/now)))

(defn format-simple-date [date]
  (format/unparse-local-date month-day-formatter date))

(defn month-start-ends [start-day end-day]
  ;; assumes start and end are in the same year
  (let [year (time/year start-day)
        end-day-midnight (day-at-midnight end-day)
        start-ends-without-last-month
        (->> (range (time/month start-day) (time/month end-day))
             (mapv (juxt (partial time/first-day-of-the-month year) (partial time/last-day-of-the-month year))))]
    (conj start-ends-without-last-month [(time/first-day-of-the-month end-day-midnight) end-day-midnight])))

(defn date-range [start end-inclusive step]
  (let [end-exclusive (time/plus end-inclusive step)
        inf-range (time-periodic/periodic-seq start step)
        below-end? (fn [t] (time/before? t end-exclusive))]
    (take-while below-end? inf-range)))

(defn days-between [start-day end-day-inclusive]
  (date-range start-day end-day-inclusive (time/days 1)))

(defn workdays-between [start-day end-day-inclusive]
  (remove (some-fn time-predicates/weekend?
                   (get holidays (time/year start-day)))
          (days-between start-day end-day-inclusive)))

(defn workdays-till-end-of-year [today]
  (workdays-between today (last-day-of-year (time/year today))))

(defn workdays-till-today [today]
  (workdays-between (time/local-date (time/year today) 1 1) today))

(defn as-yyyy-MM-dd [date]
  (format/unparse yyyy-MM-dd-formatter date))
