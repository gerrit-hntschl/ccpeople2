(ns app.log
  (:require [clojure.pprint :as pprint])
  (:import (clojure.lang ExceptionInfo)
           (java.io StringWriter)
           (ch.qos.logback.classic Logger)))

(defmacro debug [^Logger logger & msg]
  `(.debug ~logger (print-str ~@msg)))

(defmacro info [^Logger logger & msg]
  `(.info ~logger (print-str ~@msg)))

(defmacro warn [^Logger logger & msg]
  `(.warn ~logger (print-str ~@msg)))

(defmacro error [^Logger logger throwable & msg]
  `(cond (instance? ExceptionInfo ~throwable)
         (.error ~logger (print-str ~@msg "\n" (pr-str (ex-data ~throwable))) ~throwable)
         (instance? Throwable ~throwable)
         (.error ~logger (print-str ~@msg) ~throwable)
         :else
         (.error ~logger (print-str ~throwable ~@msg))))

(defmacro spy
  [^Logger logger expr]
  `(let [a# ~expr
         w# (StringWriter.)]
     (pprint/pprint '~expr w#)
     (.append w# " => ")
     (pprint/pprint a# w#)
     (error ~logger (.toString w#))
     a#))
