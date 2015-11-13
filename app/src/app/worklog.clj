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
            [cheshire.core :as json]
            [clojure.set :as set]
            [app.storage :as storage])
  (:import (java.io StringReader)
           (java.util Date)))

(def datetime-regex #"\d{4}-\d{2}-\d{2}( \d{2}:\d{2}:\d{2})?")

(def timestamp-formatter (format/formatter time/utc "yyyy-MM-dd HH:mm:ss" "yyyy-MM-dd"))


(defn matches [r]
  (fn [s]
    (re-matches r s)))

(s/defschema TimeIssue (s/pred (fn [s] (.startsWith s "TIMXIII")) "Time tracking issue name"))

(s/defschema NonEmptyString (s/constrained s/Str seq "Non-empty string"))

(s/defschema EmailAddress (s/constrained NonEmptyString (matches #"[^@]+@[^@]+") "Email address"))

(s/defschema WorkLog
  "The schema of Jira worklog tags as returned by the Tempo Plugin"
  {:billing_key                        (s/maybe NonEmptyString)
   :issue_id                           long
   :issue_key                          TimeIssue
   :hash_value                         s/Str
   :username                           NonEmptyString
   ;; daily rate
   (s/optional-key :customField_10084) Double
   ;; date format "2015-10-23 00:00:00"
   :work_date_time                     Date
   :work_description                   s/Str
   :activity_name                      NonEmptyString
   :reporter                           NonEmptyString
   ;; date "2015-10-23"
   :work_date                          Date
   :hours                              Double
   ;; remaining hours?
   (s/optional-key :customField_10406) Double
   ;; contract type? "Monatl. nach Zeit"
   (s/optional-key :customField_10100) NonEmptyString
   :activity_id                        NonEmptyString
   ;; unique over all worklogs? identity of entry
   :worklog_id                         Long
   ;; same as username
   :staff_id                           NonEmptyString
   ;; some date? "2014-08-01 00:00:00.0"
   (s/optional-key :customField_10501) NonEmptyString
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

(def worklog-attributes {:worklog_id       :worklog/id
                         :work_description :worklog/description
                         :username         :worklog/user
                         :hours            :worklog/hours
                         :work_date        :worklog/work-date})

(def jira-user-attributes {:name          :user/jira-username
                           :emailAddress :user/email})

(defn jira-data-to-datomic [attributes jira-data]
  (-> jira-data
      (set/rename-keys attributes)
      (select-keys (vals attributes))
      (assoc :db/id (storage/people-tempid))))

(defn lookup-user [worklog]
  (update worklog :worklog/user (fn [username] [:user/jira-username username]))
  )

(defn worklog-import [conn jira-worklogs]
  (->> jira-worklogs
       (map (partial jira-data-to-datomic worklog-attributes))
       (map lookup-user)
       (d/transact conn)
       (deref)))

(s/defschema JiraUser
  {:name NonEmptyString
   :emailAddress EmailAddress
   s/Keyword s/Any})

(defn user-import [conn jira-users]
  (->> jira-users
       (map (partial jira-data-to-datomic jira-user-attributes))
       (d/transact conn)
       (deref)))

(def users-uri-suffix "/rest/api/2/user/assignable/search?project=TIMXIII")

(def teams-uri "/rest/tempo-teams/1/team/")

(defn team-members [team]
  (format "/rest/tempo-teams/2/team/%s/member" team))

(defn fetch-jira [jira-base-uri uri-suffix username password]
  (-> @(http/get (str jira-base-uri uri-suffix) {:basic-auth [username password]
                                                 :middleware mw/wrap-basic-auth})
      :body
      bs/to-string
      (json/parse-string keyword)))

(defn fetch-users [l]
  (Thread/sleep 100)
  (fetch-jira
    (env :jira-base-url)
    (format "/rest/api/2/user/search?username=%s&maxResults=1000" l)
    (env :jira-username)
    (env :jira-password)))

(def ignored-users #{"CoDiRadiator"
                     "jiraapi"
                     "admin"
                     "generali-guest"
                     "reporting"
                     "ui-test"
                     "eai-monitor"})

(defn fetch-all-jira-users []
  (->> (seq "abcdefghijklmnopqrstuvwxyz")
       (mapcat fetch-users)
       (map (fn [user] (s/validate JiraUser user)))
       (filter (fn [user] (.endsWith (:emailAddress user) "@codecentric.de")))
       ;; remove duplicate email user
       (remove (comp ignored-users :name))
       (set)))

;; TODO workaround for missing authorization for using batch user retrieval
(defn stupid-fetch-jira-users []
  (->>
    ;; 15 is team solingen
    (fetch-jira (env :jira-base-url) (team-members "15") (env :jira-username) (env :jira-password))
    (map (fn [team-member] (get-in team-member [:member :name])))
    (mapv (fn [username]
            ;; don't overload jira
            (Thread/sleep 2000)
            (fetch-jira (env :jira-base-url) (str "/rest/api/2/user?username=" username) (env :jira-username) (env :jira-password))))))


