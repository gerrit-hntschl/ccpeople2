(ns ccdashboard.ticket-import.core
  (:require [net.cgrand.enlive-html :as html]
            [aleph.http :as http]
            [aleph.http.client-middleware :as mw]
            [clojure.string :as str]
            [ccdashboard.util :refer [matching]]
            [datomic.api :refer [db] :as d]
            [plumbing.core :refer [safe-get fnk]]
            [environ.core :refer [env]]
            [ccdashboard.graph :as graph]
            [ccdashboard.log :as log]
            [ccdashboard.oauth.core :as oauth]
            [byte-streams :as bs]
            [schema.core :as s]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [cheshire.core :as json]
            [clojure.set :as set]
            [ccdashboard.retry :as retry]
            [ccdashboard.persistence.core :as storage]
            [ccdashboard.domain.data-model :as model]
            [com.stuartsierra.component :as component]
            [ccdashboard.domain.days :as days]
            [ccdashboard.domain.core :as domain])
  (:import (java.io StringReader)
           (io.netty.channel ConnectTimeoutException)
           (org.slf4j LoggerFactory)))

(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger "ccdashboard.ticket-import.core"))

(def ^:const jira-timesheet-project-key "TS")

(defn get-jira-issue-type [jira-issue]
  (get-in jira-issue [:fields :issuetype :name]))

(defn get-jira-invoicing-type [jira-issue]
  (get-in jira-issue [:fields :customfield_12300 :value]))

(defn get-jira-customer [jira-issue]
  (get-in jira-issue [:fields :components 0 :id]))

(defn get-daily-rate [jira-issue]
  (get-in jira-issue [:fields :customfield_10084]))

(defn jira-issue-to-datomic-ticket [jira-issue]
  (cond-> {:db/id (storage/people-tempid)
           :ticket/id (:id jira-issue)
           :ticket/key (:key jira-issue)}
          (get-jira-customer jira-issue)
          (assoc :ticket/customer [:customer/id (get-jira-customer jira-issue)])
          (get-in jira-issue [:fields :summary])
          (assoc :ticket/title (get-in jira-issue [:fields :summary]))
          (get-jira-issue-type jira-issue)
          (assoc :ticket/type (safe-get model/jira-issue-type->datomic-issue-type
                                        (get-jira-issue-type jira-issue)))
          (get-jira-invoicing-type jira-issue)
          (assoc :ticket/invoicing (safe-get model/jira-invoicing-types->datomic-invoicing
                                             (get-jira-invoicing-type jira-issue)))
          (get-daily-rate jira-issue)
          (assoc :ticket/daily-rate (get-daily-rate jira-issue))))

(defn extract-customer-from-jira-issue [jira-issue]
  (get-in jira-issue [:fields :components 0]))

(s/defn jira-component-to-datomic-customer [jira-component :- model/JiraCustomer]
  {:db/id (storage/people-tempid)
   :customer/id (:id jira-component)
   :customer/name (:name jira-component)})

;; worklog_id -> entity-id

(defn get-body [uri]
  (-> @(http/get uri)
      :body
      bs/to-string))

(defn fetch-worklogs-raw [jira-tempo-uri]
  ;; todo pass token around separately
  (log/info logger "requesting tempo:" (str/replace jira-tempo-uri #"tempoApiToken=[^&]+&" "tempoApiToken=%s&"))
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

(defn extract-data [worklog-xml-string]
  (-> (StringReader. worklog-xml-string)
      (html/xml-parser)
      (html/select [:worklog])
      (->> (map (comp
                  model/to-jira-worklog
                  simple-worklog)))))

(defn jira-data-to-datomic [attributes jira-data]
  (-> jira-data
      (set/rename-keys attributes)
      (select-keys (vals attributes))
      (assoc :db/id (storage/people-tempid))))

(defn lookup-user-reference [worklog]
  (update worklog :worklog/user (fn [username] [:user/jira-username username])))

(def jira-worklog-to-datomic-transform (partial jira-data-to-datomic model/jira-worklog-attributes->db-attributes))

(defn jira-worklog-to-datomic-worklog [jira-worklog]
  (-> jira-worklog
      (jira-worklog-to-datomic-transform)
      (model/ignore-nil-mappings)
      (lookup-user-reference)
      (update :worklog/ticket (fn [issue-id] [:ticket/id issue-id]))))

(defn user-import [conn jira-users]
  (->> jira-users
       (map (partial jira-data-to-datomic model/jira-user-attributes))
       (d/transact conn)
       (deref)))

(def teams-uri "/rest/tempo-teams/1/team/")

;;(oauth/fetch-jira "/rest/tempo-core/1/holidayscheme/6/")

(defn team-member-url [team]
  (format "/rest/tempo-teams/2/team/%s/member" team))

(def solingen-team-id 15)

(defn issue-details-uri [issue-id]
  (format "/rest/api/2/issue/%d" issue-id))

(defn fetch-users [jira-base-url jira-token jira-consumer-private-key l]
  (Thread/sleep 100)
  (oauth/fetch-jira
    jira-base-url
    (format "/rest/api/2/user/search?username=%s&maxResults=1000" l)
    jira-token
    jira-consumer-private-key))

(defn fetch-team-member
  ([team-id]
    (fetch-team-member team-id (env :jira-base-url) (env :jira-access-token) (env :jira-consumer-private-key)))
  ([team-id jira-base-url jira-token jira-consumer-private-key]
   (oauth/fetch-jira
     jira-base-url
     (team-member-url team-id)
     jira-token
     jira-consumer-private-key )))

(def codecentric-all-id 4)

(defn fetch-all-teams-except-codecentric-all [jira-base-url jira-token jira-consumer-private-key]
  (->> (oauth/fetch-jira
         jira-base-url
         teams-uri
         jira-token
         jira-consumer-private-key)
       (remove (matching :id codecentric-all-id))
       (s/validate [model/JiraTeam])))

(defn get-team-member-start-date [jira-team-member]
  (get-in jira-team-member [:membership :dateFromANSI]))

(defn get-team-member-jira-username [team-member]
  (get-in team-member [:member :name]))

(def ignored-users #{"CoDiRadiator"
                     "jiraapi"
                     "admin"
                     "generali-guest"
                     "reporting"
                     "ui-test"
                     "eai-monitor"})

(defn fetch-all-jira-users [jira-base-url jira-token jira-consumer-private-key]
  (->> (seq "abcdefghijklmnopqrstuvwxyz")
       (mapcat (partial fetch-users jira-base-url jira-token jira-consumer-private-key))
       (map (fn [user] (s/validate model/JiraUser user)))
       (filter (fn [user] (.endsWith (:emailAddress user) "@codecentric.de")))
       ;; remove duplicate email user
       (remove (comp ignored-users :name))
       (set)))

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
  (users [this user-names] "Read all users with the given names. Returns seq of JiraUsers.")
  (teams [this] "Read all teams. Returns seq of JiraTeams")
  (team-member [this team-id] "Reads the member of the given team. Returns seq of JiraTeamMemberships"))

(defn fetch-issues [jira-base-uri issue-ids jira-token jira-consumer-private-key]
  (mapv (fn [issue-id]
          (Thread/sleep 20)
          (oauth/fetch-jira jira-base-uri
                      (issue-details-uri issue-id)
                      jira-token
                      jira-consumer-private-key))
        issue-ids))

(s/defschema JiraRestClientOptions {:jira-base-url model/NonEmptyString
                                    :jira-access-token model/NonEmptyString
                                    :jira-tempo-api-token model/NonEmptyString
                                    :jira-consumer-private-key model/NonEmptyString})

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

(defrecord JiraRestClient [jira-base-url jira-tempo-api-token jira-access-token jira-consumer-private-key]
  Jira
  (worklogs [this from-date to-date]
    (worklogs-from-to (assoc this
                        :from-date from-date
                        :to-date to-date)))
  (issues [this issue-ids]
    (fetch-issues jira-base-url issue-ids jira-access-token jira-consumer-private-key))
  (users [this usernames]
    ;; todo if less than 26 usernames, fetch directly
    (->> (fetch-all-jira-users jira-base-url jira-access-token jira-consumer-private-key)
         (filter (fn [jira-user] (contains? usernames (:name jira-user))))))
  (teams [this]
    (fetch-all-teams-except-codecentric-all jira-base-url jira-access-token jira-consumer-private-key))
  (team-member [this team-id]
    (fetch-team-member team-id jira-base-url jira-access-token jira-consumer-private-key)))

(defrecord JiraFakeClient [prefetched-worklogs prefetched-issues prefetched-users]
  Jira
  (worklogs [this from-date to-date]
    {:worklogs prefetched-worklogs})
  (issues [this issue-ids]
    prefetched-issues)
  (users [this usernames]
    prefetched-users)
  (teams [this]))

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

(defn any-work-date [dbval]
  (ffirst
    (d/q '{:find  [?work-date]
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

(defn all-domain-worklog-ids-in-range [dbval import-start-date import-end-date]
  (->> (d/q '{:find [?worklog-id]
              :in [$ ?start-date ?end-date]
              :where [[?worklog :worklog/work-date ?work-date]
                      [(<= ?start-date ?work-date)]
                      [(<= ?work-date ?end-date)]
                      [?worklog :worklog/id ?worklog-id]]}
            dbval
            import-start-date
            import-end-date)
       (into #{} (map first))))

(defn worklog-retraction [worklog-id]
  [:db.fn/retractEntity [:worklog/id worklog-id]])

(def to-db-user (partial jira-data-to-datomic model/jira-user-attributes))

(def missing-users-graph
  ;; input -> import-usernames-all & dbval & jira
  {:db-usernames-all                 (fnk [dbval]
                                       (all-usernames dbval))
   :import-usernames-new           (fnk [import-usernames-all db-usernames-all]
                                       (set/difference import-usernames-all db-usernames-all))
   :jira-users-new                   (fnk [jira import-usernames-new]
                                       (when (not-empty import-usernames-new)
                                         (users jira import-usernames-new)))
   :domain-users-new                 (fnk [jira-users-new]
                                       (mapv to-db-user
                                             jira-users-new))})

(def jira-import-graph
  ;; input:
  ;; dbval :- Datomic database value,
  ;; today :- Date of today with time 1 millisecond before tomorrow
  ;; jira :- Jira Client implementation
  (merge
    {:re-import?           (fnk [dbval]
                             (boolean (any-work-date dbval)))
     :current-period-start (fnk [today]
                             (domain/current-period-start today))
     :import-start-date    (fnk [re-import? today current-period-start]
                             (if re-import?
                               current-period-start
                               (time/first-day-of-the-month (time/year today) 1)))
     :import-end-date      (fnk [today]
                             (time/local-date (time/year today) 12 31))
     :worklogs-retrieved   (fnk [jira import-start-date import-end-date]
                             ;; returns a map containing all intermediate state used to retrieve actual worklogs
                             ;; other entries are not used, but useful for debugging
                             (worklogs jira import-start-date import-end-date))
     :worklogs-all         (fnk worklogs-all :- [model/JiraWorkLog]
                             [worklogs-retrieved]
                             ;; simplify access to actual worklogs
                             (:worklogs worklogs-retrieved))
     :import-usernames-all (fnk [worklogs-all]
                             (into #{} (map :username) worklogs-all))}
    missing-users-graph
    {:jira-usernames-new               (fnk [jira-users-new]
                                         (into #{} (map :name) jira-users-new))
     :worklogs-usernames-unknown       (fnk [jira-usernames-new import-usernames-new]
                                         (set/difference import-usernames-new jira-usernames-new))
     :issue-ids-all                    (fnk [worklogs-all]
                                         (into #{} (map :issue_id) worklogs-all))
     :db-ticket-ids-all                (fnk [dbval]
                                         (all-domain-ticket-ids dbval))
     :issue-ids-new                    (fnk [issue-ids-all db-ticket-ids-all]
                                         (set/difference issue-ids-all db-ticket-ids-all))
     :issues-new-parsed-json           (fnk [jira issue-ids-new]
                                         (issues jira issue-ids-new))
     :issues-new-coerced               (fnk [issues-new-parsed-json]
                                         (mapv model/to-jira-issue
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
     :db-worklog-ids-in-import-range   (fnk [re-import? dbval import-start-date import-end-date]
                                         (when re-import?
                                           (all-domain-worklog-ids-in-range dbval
                                                                            (time-coerce/to-date import-start-date)
                                                                            (time-coerce/to-date import-end-date))))
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
                                                         domain-worklogs-transaction)))
     :db-transactions-stats            (fnk [domain-users-new domain-customers-new tickets-new domain-worklogs-deleted domain-worklogs-new]
                                         (zipmap [:new-users :new-customers :new-tickets :deleted-worklogs :worklogs-in-period]
                                                 (map count
                                                      [domain-users-new domain-customers-new tickets-new domain-worklogs-deleted domain-worklogs-new])))}))

(def jira-import (graph/compile-cancelling jira-import-graph))

(def jira-full-re-import (graph/compile-cancelling
                           (assoc jira-import-graph
                             :re-import? (graph/constant true)
                             :import-start-date (fnk [today] (time/first-day-of-the-month (time/year today) 1)))))


(defn to-datomic-user-join-date [team-member]
  {:db/id (storage/people-tempid)
   :user/start-date (get-team-member-start-date team-member)
   :user/jira-username (get-team-member-jira-username team-member)})

(def jira-start-date-import-graph
  ;; input: jira :- Jira Client implementation
  ;;        dbval :- datomic database value
  (merge missing-users-graph
         {:teams-all                   (fnk [jira]
                                         (teams jira))
          :team-ids-all                (fnk [teams-all]
                                         (into #{}
                                               (map :id)
                                               teams-all))
          :team-members-all            (fnk team-members-all :- [model/JiraTeamMember]
                                         [jira team-ids-all]
                                         (into []
                                               (mapcat (partial team-member jira))
                                               team-ids-all))
          :jira-username-and-team-ids  (fnk [team-members-all]
                                         (into [] (map (fn [member]
                                                         {:jira-username (get-in member [:member :id])
                                                          :team-id       (get-in member [:membership :teamId])})
                                                       team-members-all)))
          :team-name-and-team-ids      (fnk [teams-all]
                                         (into [] (map (fn [team]
                                                         {:team-name (:name team)
                                                          :team-id   (:id team)})
                                                       teams-all)))
          :team-members-with-join-date (fnk [team-members-all]
                                         (into []   ;; ignore spurious empty values
                                               (comp (filter (comp seq get-team-member-start-date))
                                                     (map model/to-jira-team-member))
                                               team-members-all))
          :import-usernames-all        (fnk [team-members-with-join-date]
                                         (into #{}
                                               (map get-team-member-jira-username)
                                               team-members-with-join-date))
          :db-user-with-join-date      (fnk [team-members-with-join-date]
                                         (into []
                                               (map to-datomic-user-join-date)
                                               team-members-with-join-date))
          :db-transactions             (fnk [db-user-with-join-date domain-users-new]
                                         (vector domain-users-new
                                                 db-user-with-join-date))
          :import-stats                (fnk [db-user-with-join-date domain-users-new]
                                         {:number-of-users-with-join-date         (count db-user-with-join-date)
                                          :number-of-corresponding-new-jira-users (count domain-users-new)})}))

(def jira-user-start-date-import (graph/compile-cancelling jira-start-date-import-graph))

(defprotocol Scheduler
  (schedule [this f repeat-delay] "Schedule f for periodic execution with given repeat-delay in seconds."))

(defn sync-with-jira! [conn jira import-fn]
  (let [import-result (import-fn {:dbval (db conn)
                                  :today (time/today)
                                  :jira  jira})]
    (log/info logger "done import jira" (:db-transactions-stats import-result))
    (def ii import-result)
    (doseq [tx (:db-transactions import-result)]
      @(d/transact conn tx))

    import-result))

(defn sync-start-dates! [conn jira]
  (log/info logger "Starting user-start-date-import: " (.getName (Thread/currentThread)))
  (let [import-graph-result (jira-user-start-date-import {:jira jira
                                                          :dbval (db conn)})]
    (doseq [tx (:db-transactions import-graph-result)]
      @(d/transact conn tx))
    (log/info logger "Finished user-start-date-import: " (:import-stats import-graph-result))))

(defrecord JiraImporter [conn jira-client scheduler scheduled]
  component/Lifecycle
  (start [this]
    (if (:scheduled this)
      this
      (do                                                   ;(future (sync-with-jira! conn jira-client jira-full-re-import))
          (schedule scheduler
                    (fn []
                      (log/info logger "Starting import: " (.getName (Thread/currentThread)))
                      (try (sync-with-jira! conn jira-client jira-import)
                           (catch Throwable ex
                             (log/error logger ex "import error:" (.getMessage ex))
                             (def impex ex))))
                    60)
          (schedule scheduler
                    (partial sync-start-dates! conn jira-client)
                    (* 60 60 24))
          (assoc this :scheduled true)))
    this)
  (stop [this]
    (dissoc this :scheduled)))

(defn new-jira-importer []
  (map->JiraImporter {}))
