(ns ccdashboard.test.team_and_hours
  (:require [schema.experimental.complete :as c]
            [ccdashboard.ticket-import.core :refer :all]
            [datomic.api :as d]
            [ccdashboard.system :as system]
            [ccdashboard.config :as config]
            [ccdashboard.domain.data-model :as model]
            [com.stuartsierra.component :as component]
            [ccdashboard.persistence.core :as storage]
            [clojure.test :refer [is deftest run-tests]])
  (:import (java.util UUID)))

(defn complete-non-nil [schema]
  (fn [x]
    (c/complete x schema)))

(def default-fixtures [[{:db/id              (storage/people-tempid)
                         :team/id            101
                         :team/name          "Berlin"}
                        {:db/id              (storage/people-tempid)
                         :team/id            200
                         :team/name          "Hamburg"}]
                       [{:db/id              (storage/people-tempid)
                         :user/jira-username "peter.lustig"
                         :user/email         "peter.lustig@codecentric.de"
                         :user/display-name  "Peter Lustig"
                         :user/team          [:team/id 101]}
                        {:db/id              (storage/people-tempid)
                         :user/jira-username "pipi.langstrumpf"
                         :user/email         "pipi.langstrumpf@codecentric.de"
                         :user/display-name  "Pipi Langstrumpf"
                         :user/team          [:team/id 101]}
                        {:db/id              (storage/people-tempid)
                         :user/jira-username "bob.baumeister"
                         :user/email         "bob.baumeister@codecentric.de"
                         :user/display-name  "Bob Baumeister"
                         :user/team          [:team/id 200]}]
                       [{:db/id              (storage/people-tempid)
                         :customer/id        1
                         :customer/name      "codecentric"}
                        {:db/id              (storage/people-tempid)
                         :customer/id        2
                         :customer/name      "BAU"}]]
)

(def one-user-one-ticket-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      8.}]]
   :expected-result     #{{:team/name "Berlin" :hours 8.0}}})

(def two-user-different-team-one-ticket-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      8.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "bob.baumeister"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/name "Berlin", :hours 8.0} {:team/name "Hamburg", :hours 4.0}}})

(def two-user-same-team-one-ticket-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      8.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "pipi.langstrumpf"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/name "Berlin", :hours 12.0}}})

(def booking-codecentric-ticket-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}
                          {:db/id              (storage/people-tempid)
                           :ticket/id          202
                           :ticket/customer    [:customer/id 1]
                           :ticket/invoicing   :invoicing/fixed-price}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      4.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 202]
                           :worklog/user       [:user/jira-username "bob.baumeister"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/name "Berlin", :hours 4.0}}})

(defn new-in-memory-system [config]
  (assoc-in config [:datomic :connect-url] (format "datomic:mem://%s" (UUID/randomUUID))))

(defn new-test-system [scenario]
  (-> (system/new-system (new-in-memory-system config/defaults))
      ;; don't need an http server for testing currently
      (dissoc :http)
      (assoc :jira-client (map->JiraFakeClient (:jira-state scenario)))
      (component/system-using {:jira-importer [:conn :jira-client :scheduler]})))

(defn test-scenario [scenario]
  (let [sys (component/start (new-test-system scenario))
        conn (:conn sys)]
    (try
      (doseq [tx (into (:fixture scenario)
                       (:billable-components scenario))]
        @(d/transact conn tx))
      (is (= (:expected-result scenario)
             (storage/billable-hours-for-teams (d/db conn))))
      (finally
        (component/stop sys)))))

(deftest should-be-billable-for-one-user-one-ticket
  (test-scenario one-user-one-ticket-scenario))

(deftest should-be-billable-for-two-teams-two-tickets
  (test-scenario two-user-different-team-one-ticket-scenario))

(deftest should-be-billable-for-one-team-one-ticket
  (test-scenario two-user-same-team-one-ticket-scenario))

(deftest should-not-book-codecentric-ticket
  (test-scenario booking-codecentric-ticket-scenario))
