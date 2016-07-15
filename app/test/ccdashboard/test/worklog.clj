(ns ccdashboard.test.worklog
  (:require [schema.experimental.complete :as c]
            [schema.experimental.generators :as g]
            [ccdashboard.ticket-import.core :refer :all]
            [datomic.api :as d]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [ccdashboard.system :as system]
            [ccdashboard.config :as config]
            [ccdashboard.domain.data-model :as model]
            [com.stuartsierra.component :as component]
            [ccdashboard.persistence.core :as storage]
            [clojure.test :refer [is deftest run-tests]]
            [ccdashboard.graph :as graph])
  (:import (java.util UUID)))

(def ^:const bob-baumeister "bob.baumeister")

(def ^:const bau-issue-id 123)

(def ^:const cc-plus-one-id 999)

(def ^:const bau-company "BAU")

(def ^:const bob-email "bob.baumeister@codecentric.de")

(def bob-bau-base-worklog {:username bob-baumeister
                           :issue_id bau-issue-id})

(defn complete-non-nil [schema]
  (fn [x]
    (c/complete x schema)))

(def create-scenario
  {:fixture           [[{:db/id              (storage/people-tempid)
                         :user/email         bob-email
                         :user/jira-username bob-baumeister}]] ; empty fixture besides user
   :jira-state        {:prefetched-worklogs (->> [(assoc bob-bau-base-worklog
                                                    :hours 8.
                                                    :work_date (time-coerce/to-date (time/date-time 2016 1 18))
                                                    :worklog_id 1)
                                                  (assoc bob-bau-base-worklog
                                                    :hours 5.
                                                    :work_date (time-coerce/to-date (time/date-time 2016 1 19))
                                                    :worklog_id 2)
                                                  (assoc bob-bau-base-worklog
                                                    :issue_id cc-plus-one-id
                                                    :hours 7.
                                                    :work_date (time-coerce/to-date (time/date-time 2016 1 20))
                                                    :worklog_id 3)
                                                  (assoc bob-bau-base-worklog
                                                    :issue_id 154
                                                    :hours 7.
                                                    :work_date (time-coerce/to-date (time/date-time 2016 1 21))
                                                    :worklog_id 4)]
                                                 (mapv (complete-non-nil model/JiraWorkLog)))
                       :prefetched-issues   (->> [{:id     bau-issue-id
                                                   :fields {:components        [{:id   100
                                                                                 :name bau-company}]
                                                            :customfield_12300 {:value "Nach Aufwand (T&M)"}
                                                            :issuetype         {:name "Quote"}}}
                                                  {:id     cc-plus-one-id
                                                   :fields {:components [{:id   99
                                                                          :name "codecentric"}]
                                                            :issuetype  {:name "Administrative Time"}}}
                                                  {:id     154
                                                   :fields {:components [{:id   99
                                                                          :name "codecentric"}]
                                                            :customfield_12300 {:value "Support"}
                                                            :issuetype  {:name "Quote"}}}]
                                                 (mapv (complete-non-nil model/JiraIssue)))
                       :prefetched-users    (->> [{:name         bob-baumeister
                                                   :emailAddress bob-email}]
                                                 (mapv (complete-non-nil model/JiraUser)))}
   :expected-result [{:entity-key :worklogs
                      :id-key :worklog/id
                      :expected #{{:worklog/id        1
                                   :worklog/hours     8.
                                   :worklog/work-date (time/local-date 2016 1 18)
                                   :worklog/ticket    bau-issue-id}
                                  {:worklog/id        2
                                   :worklog/hours     5.
                                   :worklog/work-date (time/local-date 2016 1 19)
                                   :worklog/ticket    bau-issue-id}
                                  {:worklog/id        3
                                   :worklog/hours     7.
                                   :worklog/work-date (time/local-date 2016 1 20)
                                   :worklog/ticket    cc-plus-one-id}
                                  {:worklog/id        4
                                   :worklog/hours     7.
                                   :worklog/work-date (time/local-date 2016 1 21)
                                   :worklog/ticket    154}}}
                     {:entity-key :tickets
                      :id-key :ticket/id
                      :expected #{{:ticket/id        bau-issue-id
                                   :ticket/customer  100,
                                   :ticket/invoicing :invoicing/time-monthly,
                                   :ticket/type      :ticket.type/quote}
                                  {:ticket/id        cc-plus-one-id
                                   :ticket/customer  99,
                                   :ticket/type      :ticket.type/admin}
                                  {:ticket/id        154
                                   :ticket/customer  99
                                   :ticket/invoicing :invoicing/support
                                   :ticket/type      :ticket.type/quote}}}
                     {:entity-key :customers
                      :id-key :customer/id
                      :expected #{{:customer/id 100, :customer/name "BAU"}
                                  {:customer/id 99, :customer/name "codecentric"}}}]})

(def delete-scenario
  {:fixture         [[{:db/id              (storage/people-tempid)
                       :user/email         bob-email
                       :user/jira-username bob-baumeister}]
                     [{:db/id            (storage/people-tempid)
                       :ticket/id        222
                       :ticket/key       "*"
                       :ticket/title     "DO IT"
                       :ticket/invoicing :invoicing/fixed-price}]
                     [{:db/id               (storage/people-tempid)
                       :worklog/id          111
                       :worklog/hours       8.
                       :worklog/user        [:user/jira-username bob-baumeister]
                       :worklog/work-date   (-> (time/today) (time-coerce/to-date))
                       :worklog/description "egal"
                       :worklog/ticket      [:ticket/id 222]}]]
   :jira-state      {:prefetched-worklogs []
                     :prefetched-issues   []
                     :prefetched-users    []}
   :expected-result [{:entity-key :worklogs
                      :id-key     :worklog/id
                      :expected   #{}}
                     {:entity-key :tickets
                      :id-key     :ticket/id
                      :expected   #{}}
                     {:entity-key :customers
                      :id-key     :customer/id
                      :expected   #{}}]})

(def update-scenario
  {:fixture         [[{:db/id              (storage/people-tempid)
                       :user/email         bob-email
                       :user/jira-username bob-baumeister}]
                     ;; todo ignores customers
                     [{:db/id            (storage/people-tempid)
                       :ticket/id        222
                       :ticket/key       "*"
                       :ticket/title     "DO IT"
                       :ticket/invoicing :invoicing/fixed-price}]
                     [{:db/id               (storage/people-tempid)
                       :worklog/id          333
                       :worklog/hours       8.
                       :worklog/user        [:user/jira-username bob-baumeister]
                       :worklog/work-date   (-> (time/today) (time-coerce/to-date))
                       :worklog/description "egal"
                       :worklog/ticket      [:ticket/id 222]}]]
   :jira-state      {:prefetched-worklogs [(c/complete (assoc bob-bau-base-worklog
                                                         :hours 2.
                                                         :work_date (java.util.Date.)
                                                         :worklog_id 333
                                                         :issue_id 222)
                                                       model/JiraWorkLog)]
                     :prefetched-issues   []
                     :prefetched-users    []}
   :expected-result [{:entity-key :worklogs
                      :id-key :worklog/id
                      :expected #{{:worklog/id        333
                                   :worklog/hours     2.
                                   :worklog/work-date (time/today)
                                   :worklog/ticket    222}}}]})

(def future-create-scenario
  {:fixture           [[{:db/id              (storage/people-tempid)
                         :user/email         bob-email
                         :user/jira-username bob-baumeister}]] ; empty fixture besides user
   :jira-state        {:prefetched-worklogs (->> [(assoc bob-bau-base-worklog
                                                    :hours 8.
                                                    :work_date (time-coerce/to-date (time/date-time 2016 12 31))
                                                    :worklog_id 1)
                                                  (assoc bob-bau-base-worklog
                                                    :issue_id cc-plus-one-id
                                                    :hours 7.
                                                    :work_date (time-coerce/to-date (time/date-time 2016 12 31))
                                                    :worklog_id 3)]
                                                 (mapv (complete-non-nil model/JiraWorkLog)))
                       :prefetched-issues   (->> [{:id     bau-issue-id
                                                   :fields {:components        [{:id   100
                                                                                 :name bau-company}]
                                                            :customfield_12300 {:value "Nach Aufwand (T&M)"}
                                                            :issuetype         {:name "Quote"}}}
                                                  {:id     cc-plus-one-id
                                                   :fields {:components [{:id   99
                                                                          :name "codecentric"}]
                                                            :issuetype  {:name "Administrative Time"}}}]
                                                 (mapv (complete-non-nil model/JiraIssue)))
                       :prefetched-users    (->> [{:name         bob-baumeister
                                                   :emailAddress bob-email}]
                                                 (mapv (complete-non-nil model/JiraUser)))}
   :expected-result [{:entity-key :worklogs
                      :id-key :worklog/id
                      :expected #{{:worklog/id        1
                                   :worklog/hours     8.
                                   :worklog/work-date (time/local-date 2016 12 31)
                                   :worklog/ticket    bau-issue-id}
                                  {:worklog/id        3
                                   :worklog/hours     7.
                                   :worklog/work-date (time/local-date 2016 12 31)
                                   :worklog/ticket    cc-plus-one-id}}}
                     {:entity-key :tickets
                      :id-key :ticket/id
                      :expected #{{:ticket/id        bau-issue-id
                                   :ticket/customer  100,
                                   :ticket/invoicing :invoicing/time-monthly,
                                   :ticket/type      :ticket.type/quote}
                                  {:ticket/id       cc-plus-one-id
                                   :ticket/customer 99,
                                   :ticket/type     :ticket.type/admin}}}
                     {:entity-key :customers
                      :id-key :customer/id
                      :expected #{{:customer/id 100, :customer/name "BAU"}
                                  {:customer/id 99, :customer/name "codecentric"}}}]})

(def future-delete-scenario
  {:fixture         [[{:db/id              (storage/people-tempid)
                       :user/email         bob-email
                       :user/jira-username bob-baumeister}]
                     [{:db/id            (storage/people-tempid)
                       :ticket/id        222
                       :ticket/key       "*"
                       :ticket/title     "DO IT"
                       :ticket/invoicing :invoicing/fixed-price}]
                     [{:db/id               (storage/people-tempid)
                       :worklog/id          111
                       :worklog/hours       8.
                       :worklog/user        [:user/jira-username bob-baumeister]
                       :worklog/work-date   (time-coerce/to-date (time/date-time 2016 12 30))
                       :worklog/description "egal"
                       :worklog/ticket      [:ticket/id 222]}]]
   :jira-state      {:prefetched-worklogs []
                     :prefetched-issues   []
                     :prefetched-users    []}
   :expected-result [{:entity-key :worklogs
                      :id-key     :worklog/id
                      :expected   #{}}
                     {:entity-key :tickets
                      :id-key     :ticket/id
                      :expected   #{}}
                     {:entity-key :customers
                      :id-key     :customer/id
                      :expected   #{}}]})

(defn new-in-memory-system [config]
  (assoc-in config [:datomic :connect-url] (format "datomic:mem://%s" (UUID/randomUUID))))

(defn new-test-system [scenario]
  (let [schedule-atom (atom [])]
    (-> (system/new-system (new-in-memory-system config/defaults))
        ;; don't need an http server for testing currently
        (dissoc :http)
        (assoc :jira-client (map->JiraFakeClient (:jira-state scenario)))
        (assoc :scheduler (reify Scheduler
                            (schedule [this f repeat-delay]
                              (swap! schedule-atom conj f))))
        (assoc :schedule-atom schedule-atom)
        (component/system-using {:jira-importer [:conn :jira-client :scheduler]}))))

(defn assert-result-matches [expected-spec actual]
  (doseq [{:keys [entity-key id-key expected]} expected-spec]
    (let [actual-ents (get actual entity-key)
          id->actual-ent (into {} (map (juxt id-key identity)) actual-ents)]
      (if (empty? expected)
        (is (empty? (get actual entity-key)) "No results expected.")
        (doseq [expected-ent expected]
          (is (= expected-ent
                 (select-keys (get id->actual-ent (get expected-ent id-key))
                              (keys expected-ent)))))))))

(defn test-scenario [scenario]
  (let [sys (component/start (new-test-system scenario))
        conn (:conn sys)]
    (def sys-under-test sys)
    (try
      (doseq [tx (:fixture scenario)]
        @(d/transact conn tx))
      ;; execute the scheduled import synchronously
      (def impres ((first @(:schedule-atom sys))))
      ;; simulate login
      (let [user-id (storage/create-openid-user conn
                                                {:user/email         bob-email
                                                 :user/display-name  "Bob Baumeister"
                                                 :user/jira-username bob-baumeister})]
        (def rr (storage/existing-user-data-for-user conn user-id))
        (assert-result-matches (:expected-result scenario) rr))
      (finally
        (component/stop sys)))))

(deftest should-handle-creates-correctly
  (test-scenario create-scenario))

(deftest should-handle-future-creates-correctly
  (test-scenario future-create-scenario))

(deftest should-handle-deletes-correctly
  (test-scenario delete-scenario))

(deftest should-handle-update-correctly
  (test-scenario update-scenario))

(deftest should-handle-future-deletes-correctly
  (test-scenario future-delete-scenario))

(def jira-import-date-graph
  (-> jira-import-graph
      (select-keys [:import-start-date :current-period-start])
      (assoc :re-import? (graph/constant true))
      (graph/compile-cancelling)))

(deftest should-calculate-the-period-start-right
  (is (= #date/local[2016 3 1] (:current-period-start (jira-import-date-graph {:today #date/local [2016 4 1]}))))
  (is (= #date/local[2016 4 1] (:current-period-start (jira-import-date-graph {:today #date/local [2016 4 6]})))))

(deftest should-be-able-to-import-shit-users
  (with-redefs [ccdashboard.ticket-import.core/fetch-users (fn [& _] [{:name "Hans" :emailAddress "Hans_Meier" :displayName "Hans Meier"}])]
    (fetch-all-jira-users "" "" ""))
  )


