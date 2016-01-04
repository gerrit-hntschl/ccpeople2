(ns app.worklog
  (:require [net.cgrand.enlive-html :as html]
            [aleph.http :as http]
            [aleph.http.client-middleware :as mw]
            [datomic.api :as d]
            [plumbing.core :refer [safe-get fnk]]
            [environ.core :refer [env]]
            [app.graph :as graph]
            [app.consultant :as consultant]
            [byte-streams :as bs]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [clj-time.format :as format]
            [schema.utils :as s-util]
            [cheshire.core :as json]
            [clojure.set :as set]
            [app.retry :as retry]
            [app.storage :as storage]
            [com.stuartsierra.component :as component]
            [app.days :as days])
  (:import (java.io StringReader)
           (java.util Date)
           (io.netty.channel ConnectTimeoutException)))

(def datetime-regex #"\d{4}-\d{2}-\d{2}( \d{2}:\d{2}:\d{2})?")

(def timestamp-formatter (format/formatter time/utc "yyyy-MM-dd HH:mm:ss" "yyyy-MM-dd"))

(def ^:const jira-timesheet-project-key "TS")

(defn matches [r]
  (fn [s]
    (re-matches r s)))

(s/defschema JiraId (s/constrained long pos? "Positive number"))

(s/defschema NonEmptyString (s/constrained s/Str seq "Non-empty string"))

(s/defschema EmailAddress (s/constrained NonEmptyString (matches #"[^@]+@[^@]+") "Email address"))

(def jira-invoicing-types->datomic-invoicing {"Nicht abrechenbar" :invoicing/not-billable
                      "Monatl. nach Zeit" :invoicing/time-monthly
                      "Abschlag nach Vertrag" :invoicing/part-payment-by-contract
                      "Individuell nach Vertrag" :invoicing/individual-by-contract
                      "Kein Support-Vertrag" :invoicing/no-support-contract
                      "Produkt-Support" :invoicing/product-support})

(s/defschema InvoicingType (apply s/enum (keys jira-invoicing-types->datomic-invoicing)))

(def jira-project-types->datomic-project-type
  {"Festpreis" :project-type/fixed-price
   "Zeit und Material" :project-type/time-and-material})

(s/defschema ProjectType (apply s/enum (keys jira-project-types->datomic-project-type)))

(s/defschema JiraWorkLog
  "The schema of Jira worklog tags as returned by the Tempo Plugin"
  {; :billing_key                        (s/maybe NonEmptyString)
   :issue_id                           JiraId
   :issue_key                          NonEmptyString
   ; :hash_value                         s/Str
   :username                           NonEmptyString
   ;; daily rate
   (s/optional-key :customField_10084) Double
   ;; date format "2015-10-23 00:00:00"
   ; :work_date_time                     Date
   :work_description                   (s/maybe s/Str)
   ; :reporter                           NonEmptyString
   ;; date "2015-10-23"
   :work_date                          Date
   :hours                              Double
   ;; remaining hours?
   ; (s/optional-key :customField_10406) Double
   ; :activity_id                        (s/maybe NonEmptyString)
   ; :activity_name                      (s/maybe NonEmptyString)
   ;; unique over all worklogs -> identity of entry
   :worklog_id                         JiraId
   ;; same as username
   ; :staff_id                           NonEmptyString
   ;; some date? "2014-08-01 00:00:00.0"
   ; (s/optional-key :customField_10501) NonEmptyString
   ; :external_hours                     Double
   ; :billing_attributes
   ; :external_tstamp
   ; :parent_key
   ; :external_result
   ; :external_id
   ;; allow other fields
   s/Keyword                           s/Any})


(s/defschema JiraIssue
  "Represents a Jira issue"
  {:id       JiraId
   :key      NonEmptyString
   :fields   {:summary                            (s/maybe NonEmptyString)
              :components                         [{:id       JiraId
                                                    :name     NonEmptyString
                                                    s/Keyword s/Any}]
              ;; invoicing information
              (s/optional-key :customfield_10085) (s/maybe {:id       JiraId
                                                            :value    ProjectType
                                                            s/Keyword s/Any})
              (s/optional-key :customfield_10100) (s/maybe {:id       JiraId
                                                            :value    InvoicingType
                                                            s/Keyword s/Any})
              s/Keyword                           s/Any}
   s/Keyword s/Any})

(defn get-jira-project-type [jira-issue]
  (get-in jira-issue [:fields :customfield_10085 :value]))

(defn get-jira-invoicing-type [jira-issue]
  (get-in jira-issue [:fields :customfield_10100 :value]))

(defn get-jira-customer [jira-issue]
  (get-in jira-issue [:fields :components 0 :id]))

(defn jira-issue-to-datomic-ticket [jira-issue]
  (cond-> {:db/id (storage/people-tempid)
           :ticket/id (:id jira-issue)
           :ticket/key (:key jira-issue)}
          (get-jira-customer jira-issue)
          (assoc :ticket/customer [:customer/id (get-jira-customer jira-issue)])
          (get-in jira-issue [:fields :summary])
          (assoc :ticket/title (get-in jira-issue [:fields :summary]))
          (get-jira-project-type jira-issue)
          (assoc :ticket/project-type (safe-get jira-project-types->datomic-project-type
                                                (get-jira-project-type jira-issue)))
          (get-jira-invoicing-type jira-issue)
          (assoc :ticket/invoicing (safe-get jira-invoicing-types->datomic-invoicing
                                             (get-jira-invoicing-type jira-issue)))))

(defn extract-customer-from-jira-issue [jira-issue]
  (get-in jira-issue [:fields :components 0]))

(defn jira-component-to-datomic-customer [jira-component]
  {:db/id (storage/people-tempid)
   :customer/id (:id jira-component)
   :customer/name (:name jira-component)})

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

(def date-and-string-coercer
  (coerce/first-matcher [datetime-matcher coerce/string-coercion-matcher]))

(def worklog-coercer
  (coerce/coercer JiraWorkLog
                  date-and-string-coercer))

(def jira-issue-coercer
  (coerce/coercer JiraIssue
                  date-and-string-coercer))

(defn keep-known-fields [worklog]
  (select-keys worklog (keys JiraWorkLog)))

(defn ignore-nil-mappings [worklog]
  (into {}
        (remove (comp nil? val))
        worklog))

;; worklog_id -> entity-id

(defn get-body [uri]
  (-> @(http/get uri)
      :body
      bs/to-string))

(defn fetch-worklogs-raw [jira-tempo-uri]
  (println "requesting tempo:" jira-tempo-uri)
  (get-body jira-tempo-uri))

(def fetch-worklogs (retry/retryable fetch-worklogs-raw
                                      (some-fn
                                        (fn [ex]
                                          (= (.getMessage ex) "connection was closed"))
                                        (partial instance? ConnectTimeoutException))))

(defn simple-worklog [worklog-node]
  (->> worklog-node
       :content
       (into {} (map (juxt :tag
                           (comp first :content))))))

(defn throw-on-invalid-schema-error [x]
  (if (s-util/error? x)
    (throw (ex-info "schema violation" {:error x}))
    x))

(defn extract-data [worklog-xml-string]
  (-> (StringReader. worklog-xml-string)
      (html/xml-parser)
      (html/select [:worklog])
      (->> (map (comp
                  ignore-nil-mappings
                  keep-known-fields
                  throw-on-invalid-schema-error
                  worklog-coercer
                  simple-worklog)))))

(def worklog-attributes {:worklog_id       :worklog/id
                         :work_description :worklog/description
                         :username         :worklog/user
                         :hours            :worklog/hours
                         :work_date        :worklog/work-date
                         :issue_id         :worklog/ticket})

(def jira-user-attributes {:name          :user/jira-username
                           :emailAddress :user/email})

(defn jira-data-to-datomic [attributes jira-data]
  (-> jira-data
      (set/rename-keys attributes)
      (select-keys (vals attributes))
      (assoc :db/id (storage/people-tempid))))

(defn lookup-user-reference [worklog]
  (update worklog :worklog/user (fn [username] [:user/jira-username username])))

(def jira-worklog-to-datomic-transform (partial jira-data-to-datomic worklog-attributes))

(defn jira-worklog-to-datomic-worklog [jira-worklog]
  (-> jira-worklog
      (jira-worklog-to-datomic-transform)
      (ignore-nil-mappings)
      (lookup-user-reference)
      (update :worklog/ticket (fn [issue-id] [:ticket/id issue-id]))))

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

(defn issue-details-uri [issue-id]
  (format "/rest/api/2/issue/%d" issue-id))

(defn fetch-jira-raw
  ([uri-suffix]
   (fetch-jira-raw (env :jira-base-url) uri-suffix (env :jira-username) (env :jira-password)))
  ([jira-base-uri uri-suffix username password]
   (println "requesting jira:" uri-suffix)
   (-> @(http/get (str jira-base-uri uri-suffix) {:basic-auth [username password]
                                                  :middleware mw/wrap-basic-auth
                                                  :connection-timeout 10000
                                                  :request-timeout 30000})
       :body
       bs/to-string
       (json/parse-string keyword))))

(def fetch-jira (retry/retryable fetch-jira-raw (some-fn
                                                  (fn [ex]
                                                    (= (.getMessage ex) "connection was closed"))
                                                  (partial instance? ConnectTimeoutException))))

(defn fetch-users [jira-base-url jira-username jira-password l]
  (Thread/sleep 100)
  (fetch-jira
    jira-base-url
    (format "/rest/api/2/user/search?username=%s&maxResults=1000" l)
    jira-username
    jira-password))

(def ignored-users #{"CoDiRadiator"
                     "jiraapi"
                     "admin"
                     "generali-guest"
                     "reporting"
                     "ui-test"
                     "eai-monitor"})

(defn fetch-all-jira-users [jira-base-url jira-username jira-password]
  (->> (seq "abcdefghijklmnopqrstuvwxyz")
       (mapcat (partial fetch-users jira-base-url jira-username jira-password))
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

(defn worklog-uri-for-period [jira-base-url jira-tempo-api-token from-yyyy-MM-dd to-yyyy-MM-dd]
  (str jira-base-url
       "/plugins/servlet/tempo-getWorklog/?dateFrom="
       from-yyyy-MM-dd
       "&dateTo="
       to-yyyy-MM-dd
       "&format=xml&diffOnly=false&tempoApiToken="
       jira-tempo-api-token
       "&projectKey="
       jira-timesheet-project-key))

(defn monthly-import-urls [jira-base-url jira-tempo-api-token start-day end-day]
  (->> (days/month-start-ends start-day end-day)
       (mapv (fn [[first-day-of-month last-day-of-month]]
              (worklog-uri-for-period jira-base-url
                                      jira-tempo-api-token
                                      (days/as-yyyy-MM-dd first-day-of-month)
                                      (days/as-yyyy-MM-dd last-day-of-month))))))

(defprotocol Jira
  (worklogs [this from-date to-date]
    "Read all worklogs from from-date until to-date (inclusive).
     Returns map containing a seq of WorkLogs mapped to the key :worklogs.")
  (issues [this issue-ids] "Read all issues with the given ids. Returns seq of JiraIssues.")
  (users [this user-names] "Read all users with the given names. Returns seq of JiraUsers."))

(defn fetch-issues [jira-base-uri jira-username jira-password issue-ids]
  (mapv (fn [issue-id]
          (Thread/sleep 20)
          (fetch-jira jira-base-uri
                      (issue-details-uri issue-id)
                      jira-username
                      jira-password))
        issue-ids))

(s/defschema JiraRestClientOptions {:jira-base-url NonEmptyString
                                    :jira-username NonEmptyString
                                    :jira-password NonEmptyString
                                    :jira-tempo-api-token NonEmptyString})

(def worklog-graph
  {:monthly-import-urls
   (fnk [jira-base-url jira-tempo-api-token from-date to-date]
     (monthly-import-urls jira-base-url jira-tempo-api-token from-date to-date))
   :monthly-worklog-xmls
   (fnk [monthly-import-urls]
     (mapv fetch-worklogs monthly-import-urls))
   :worklogs
   (fnk [monthly-worklog-xmls]
     (mapcat extract-data monthly-worklog-xmls))})

(def worklogs-from-to (plumbing.graph/eager-compile worklog-graph))

(defrecord JiraRestClient [jira-base-url jira-username jira-password jira-tempo-api-token]
  Jira
  (worklogs [this from-date to-date]
    (worklogs-from-to (assoc this
                        :from-date from-date
                        :to-date to-date)))
  (issues [this issue-ids]
    (fetch-issues jira-base-url jira-username jira-password issue-ids))
  (users [this usernames]
    ;; todo if less than 26 usernames, fetch directly
    (->> (fetch-all-jira-users jira-base-url jira-username jira-password)
         (filter (fn [jira-user] (contains? usernames (:name jira-user)))))))

(defrecord JiraFakeClient [prefetched-worklogs prefetched-issues prefetched-users]
  Jira
  (worklogs [this from-date to-date]
    {:worklogs prefetched-worklogs})
  (issues [this issue-ids]
    prefetched-issues)
  (users [this usernames]
    prefetched-users))

(defn work-date-between [from-date to-date]
  (fn [jira-worklog]
    (let [work-date (time-coerce/from-date (:work_date jira-worklog))]
      (and (time/after? work-date
                        from-date)
           (time/before? work-date
                         to-date)))))

(defrecord JiraDownloadedWorklogsClient [jira-client worklog-file-paths]
  Jira
  (worklogs [this from-date to-date]
    {:worklogs
     (filter (work-date-between from-date to-date)
             (mapcat extract-data
                     (map slurp worklog-file-paths)))})
  (issues [this issue-ids]
    (issues jira-client issue-ids))
  (users [this usernames]
    (users jira-client usernames)))

(defn new-downloaded-worklogs-jira-client [paths]
  (map->JiraDownloadedWorklogsClient {:worklog-file-paths paths}))

(comment
  ;; useful for storing worklog xmls. allows the usage of the JiraDownloadedWorklogsClient
  (defn download-worklogs []
    (dorun
      (map-indexed (fn [n uri]
                     (spit (str "worklogmonth" (inc n) ".xml")
                           (fetch-worklogs uri)))
                   (monthly-import-urls (env :jira-base-url) (env :jira-tempo) (time/date-time 2015 1 1) (time/today)))))

  )

(defn new-jira-rest-client [options]
  (->> options
       (s/validate JiraRestClientOptions)
       (map->JiraRestClient)))

(defn max-work-date [dbval]
  (ffirst
    (d/q '{:find  [(max ?work-date)]
           :in    [$]
           :where [[?e :worklog/work-date ?work-date]]}
         dbval)))

(defn today-before-midnight []
  (time/today-at 23 59 59 999))

(defn all-domain-ticket-ids [dbval]
  (->> (d/q '{:find  [?ticket-id]
              :in    [$]
              :where [[?e :ticket/id ?ticket-id]]}
            dbval)
       (into #{} (map first))))

(defn all-domain-customer-ids [dbval]
  (->> (d/q '{:find  [?customer-id]
              :in    [$]
              :where [[?e :customer/id ?customer-id]]}
            dbval)
       (into #{} (map first))))

(defn all-usernames [dbval]
  (->> (d/q '{:find  [?username]
              :in    [$]
              :where [[?e :user/jira-username ?username]]}
            dbval)
       (into #{} (map first))))

(defn all-domain-worklog-ids-in-range [dbval import-start-date today]
  (->> (d/q '{:find [?worklog-id]
              :in [$ ?start-date ?end-date]
              :where [[?worklog :worklog/work-date ?work-date]
                      [(<= ?start-date ?work-date)]
                      [(<= ?work-date ?end-date)]
                      [?worklog :worklog/id ?worklog-id]]}
            dbval
            import-start-date
            today)
       (into #{} (map first))))

(defn worklog-retraction [worklog-id]
  [:db.fn/retractEntity [:worklog/id worklog-id]])

(def jira-import-graph
  ;; input:
  ;; dbval :- Datomic database value,
  ;; today :- Date of today with time 1 millisecond before tomorrow
  ;; jira :- Jira Client implementation
  {:db-max-work-date                 (fnk [dbval]
                                       (time-coerce/from-date (max-work-date dbval)))
   :import-start-date                (fnk [db-max-work-date today]
                                       (when db-max-work-date
                                         (assert (not (time/after? db-max-work-date today))
                                                 (str "db-max-work-date is after today: " db-max-work-date)))
                                       (if db-max-work-date
                                         (time/first-day-of-the-month db-max-work-date)
                                         (time/first-day-of-the-month (time/year today) 1)))
   :current-period-start             (fnk [today]
                                       (consultant/current-period-start today))
   :re-import?                       (fnk [db-max-work-date current-period-start]
                                       ;; todo correctly handle db-date == period-start
                                       (boolean (and db-max-work-date
                                                     (time/after? db-max-work-date current-period-start))))
   :worklogs-retrieved               (fnk [jira import-start-date today]
                                       ;; returns a map containing all intermediate state used to retrieve actual worklogs
                                       ;; other entries are not used, but useful for debugging
                                       (worklogs jira import-start-date today))
   :worklogs-all                     (fnk worklogs-all :- [JiraWorkLog]
                                       [worklogs-retrieved]
                                       ;; simplify access to actual worklogs
                                       (:worklogs worklogs-retrieved))
   :worklogs-usernames-all           (fnk [worklogs-all]
                                       (into #{} (map :username) worklogs-all))
   :db-usernames-all                 (fnk [dbval]
                                       (all-usernames dbval))
   :worklogs-usernames-new           (fnk [worklogs-usernames-all db-usernames-all]
                                       (set/difference worklogs-usernames-all db-usernames-all))
   :jira-users-new                   (fnk [jira worklogs-usernames-new]
                                       (when (not-empty worklogs-usernames-new)
                                         (users jira worklogs-usernames-new)))
   :domain-users-new                 (fnk [jira-users-new]
                                       (mapv (partial jira-data-to-datomic jira-user-attributes)
                                             jira-users-new))
   :jira-usernames-new               (fnk [jira-users-new]
                                       (into #{} (map :name) jira-users-new))
   :worklogs-usernames-unknown       (fnk [jira-usernames-new worklogs-usernames-new]
                                       (set/difference worklogs-usernames-new jira-usernames-new))
   :issue-ids-all                    (fnk [worklogs-all]
                                       (into #{} (map :issue_id) worklogs-all))
   :db-ticket-ids-all                (fnk [dbval]
                                       (all-domain-ticket-ids dbval))
   :issue-ids-new                    (fnk [issue-ids-all db-ticket-ids-all]
                                       (set/difference issue-ids-all db-ticket-ids-all))
   :issues-new-parsed-json           (fnk [jira issue-ids-new]
                                       (issues jira issue-ids-new))
   :issues-new-coerced               (fnk [issues-new-parsed-json]
                                       (mapv (comp throw-on-invalid-schema-error jira-issue-coercer)
                                             issues-new-parsed-json))
   :tickets-new                      (fnk [issues-new-coerced]
                                       (mapv jira-issue-to-datomic-ticket issues-new-coerced))
   :db-customer-ids-all              (fnk [dbval]
                                       (all-domain-customer-ids dbval))
   ;; can the component of a jira issue change? here we assume that only new issues can introduce new components
   :jira-customers-new               (fnk [issues-new-coerced]
                                       (distinct (mapv extract-customer-from-jira-issue issues-new-coerced)))
   :jira-customer-ids                (fnk [jira-customers-new]
                                       (into #{} (map :id) jira-customers-new))
   :customer-ids-new                 (fnk [db-customer-ids-all jira-customer-ids]
                                       (set/difference jira-customer-ids db-customer-ids-all))
   :domain-customers-new             (fnk [customer-ids-new jira-customers-new]
                                       (mapv jira-component-to-datomic-customer
                                             (filter (comp customer-ids-new :id) jira-customers-new)))
   :worklogs-known-usernames         (fnk [worklogs-all worklogs-usernames-unknown]
                                       (remove (comp worklogs-usernames-unknown :username) worklogs-all))
   :db-worklog-ids-in-import-range   (fnk [re-import? dbval import-start-date today]
                                       (when re-import?
                                         (all-domain-worklog-ids-in-range dbval
                                                                          (time-coerce/to-date import-start-date)
                                                                          (time-coerce/to-date today))))
   :jira-worklog-ids-in-import-range (fnk [worklogs-known-usernames]
                                       (into #{} (map :worklog_id) worklogs-known-usernames))
   :jira-worklog-ids-deleted         (fnk [re-import? db-worklog-ids-in-import-range jira-worklog-ids-in-import-range]
                                       (if re-import?
                                         (set/difference db-worklog-ids-in-import-range jira-worklog-ids-in-import-range)
                                         #{}))
   :domain-worklogs-new              (fnk [worklogs-known-usernames]
                                       (mapv jira-worklog-to-datomic-worklog worklogs-known-usernames))
   :domain-worklogs-deleted          (fnk [jira-worklog-ids-deleted]
                                       (mapv worklog-retraction jira-worklog-ids-deleted))
   :domain-worklogs-transaction      (fnk [domain-worklogs-new domain-worklogs-deleted]
                                       (concat domain-worklogs-new domain-worklogs-deleted))
   :db-transactions                  (fnk [domain-users-new domain-customers-new tickets-new domain-worklogs-transaction]
                                       (remove empty?
                                               (vector domain-users-new
                                                       domain-customers-new
                                                       tickets-new
                                                       domain-worklogs-transaction)))})

(def jira-import (graph/compile-cancelling jira-import-graph))

(defprotocol Scheduler
  (schedule [this f] "Schedule f for periodic execution"))

(defn sync-with-jira! [conn jira]
  (let [import-result (jira-import {:dbval (d/db conn)
                                    :today (today-before-midnight)
                                    :jira  jira})]
    (println "done import jira")
    (def ii import-result)
    (doseq [tx (:db-transactions import-result)]
      @(d/transact conn tx))

    import-result))

(defrecord JiraImporter [conn jira-client scheduler scheduled]
  component/Lifecycle
  (start [this]
    (if (:scheduled this)
      this
      (do (schedule scheduler (fn []
                                (println "Starting import: " (.getName (Thread/currentThread)))
                                (try (sync-with-jira! conn jira-client)
                                     (catch Throwable ex
                                       (println "import error:" (.getMessage ex))
                                       (def impex ex)
                                       (.printStackTrace ex)))))
          (assoc this :scheduled true)))
    this)
  (stop [this]
    (dissoc this :scheduled)))

(defn new-jira-importer []
  (map->JiraImporter {}))
