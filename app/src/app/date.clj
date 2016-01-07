(ns app.date
  (:require [clj-time.core :as time])
  (:import org.joda.time.LocalDate))

(defn parse-local-date [date-fields]
  (apply time/local-date date-fields))

(defn print-local-date [^LocalDate local-date, ^java.io.Writer w]
  (.write w "#date/local [")
  (.write w (str (.getYear local-date)))
  (.write w " ")
  (.write w (str (.getMonthOfYear local-date)))
  (.write w " ")
  (.write w (str (.getDayOfMonth local-date)))
  (.write w "]"))

(defmethod print-method org.joda.time.LocalDate 
  [duration w]
  (print-local-date duration w))

(defmethod print-dup org.joda.time.LocalDate
  [duration w]
  (print-local-date duration w))