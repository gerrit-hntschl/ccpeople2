(ns app.worklog
  (:require [net.cgrand.enlive-html :as html]
            [aleph.http :as http]
            [aleph.http.client-middleware :as mw]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [byte-streams :as bs]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [schema.utils :as s-util]
            [clojure.set :as set]
            [app.storage :as storage])
  (:import (java.io StringReader)
           (java.util Date)))

(def datetime-regex #"\d{4}-\d{2}-\d{2}( \d{2}:\d{2}:\d{2})?")

(def timestamp-formatter (format/formatter time/utc "yyyy-MM-dd HH:mm:ss" "yyyy-MM-dd"))

(s/defschema TimeIssue (s/pred (fn [s] (.startsWith s "TIMXIII")) "Time tracking issue name"))

(s/defschema WorkLog
  "The schema of Jira worklog tags as returned by the Tempo Plugin"
  {:billing_key                        (s/maybe s/Str)
   :issue_id                           long
   :issue_key                          TimeIssue
   :hash_value                         s/Str
   :username                           s/Str
   ;; daily rate
   (s/optional-key :customField_10084) Double
   ;; date format "2015-10-23 00:00:00"
   :work_date_time                     Date
   :work_description                   s/Str
   :activity_name                      s/Str
   :reporter                           s/Str
   ;; date "2015-10-23"
   :work_date                          Date
   :hours                              Double
   ;; remaining hours?
   (s/optional-key :customField_10406) Double
   ;; contract type? "Monatl. nach Zeit"
   (s/optional-key :customField_10100) s/Str
   :activity_id                        s/Str
   ;; unique over all worklogs? identity of entry
   :worklog_id                         Long
   ;; same as username
   :staff_id                           s/Str
   ;; some date? "2014-08-01 00:00:00.0"
   (s/optional-key :customField_10501) s/Str
   ; :external_hours                     Double
   ; :billing_attributes
   ; :external_tstamp
   ; :parent_key
   ; :external_result
   ; :external_id
   ;; allow other fields
   s/Keyword                           s/Any})

(defn read-instant-date [date-string]
  (-> (format/parse timestamp-formatter date-string)
      (.toDate)))

(defn datetime-matcher [schema]
  (when (= Date schema)
    (coerce/safe
      (fn [x]
        (if (and (string? x) (re-matches datetime-regex x))
          (read-instant-date x)
          x)))))

(def worklog-coercer
  (coerce/coercer WorkLog
                  (coerce/first-matcher [datetime-matcher coerce/string-coercion-matcher])))

(defn keep-known-fields [worklog]
  (select-keys worklog (keys WorkLog)))

(defn ignore-nil-mappings [worklog]
  (into {}
        (remove (comp nil? val))
        worklog))

;; worklog_id -> entity-id

(defn get-body [uri]
  (-> @(http/get uri)
      :body
      bs/to-string))

(defn fetch-worklogs [jira-tempo-uri]
  (get-body jira-tempo-uri))

(defn simple-worklog [worklog-node]
  (->> worklog-node
       :content
       (into {} (map (juxt :tag
                           (comp first :content))))))

(defn extract-data [worklog-xml-string]
  (-> (StringReader. worklog-xml-string)
      (html/xml-parser)
      (html/select [:worklog])
      (->> (map (comp
                  ignore-nil-mappings
                  keep-known-fields
                  worklog-coercer
                  simple-worklog)))))

(def datomic-attributes {:worklog_id :worklog/id
                         :work_description :worklog/description
                         :hours :worklog/hours})

(defn jira-data-to-datomic [jira-data]
  (-> jira-data
      (set/rename-keys datomic-attributes)
      (select-keys (vals datomic-attributes))
      (assoc :db/id (storage/people-tempid))))

(defn worklog-import [conn jira-worklogs]
  (->> jira-worklogs
       (map jira-data-to-datomic)
       (d/transact conn)
       (deref)))

