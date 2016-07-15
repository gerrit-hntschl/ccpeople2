(ns ccdashboard.test.team_and_hours
  (:require [datomic.api :as d]
            [ccdashboard.persistence.core :as storage]
            [clojure.test :refer [is deftest run-tests]])
  (:import (java.util UUID)))

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
                         :customer/name      "BAU"}]])

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
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 8.0}}})

(def one-user-one-ticket-equal-worklog-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      4.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 8.0}}})

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
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 8.0}
                          {:team/id             200
                           :team/name           "Hamburg"
                           :team/billable-hours 4.0}}})

(def two-user-same-team-two-ticket-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}
                          {:db/id              (storage/people-tempid)
                           :ticket/id          202
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      8.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 202]
                           :worklog/user       [:user/jira-username "pipi.langstrumpf"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 12.0}}})

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
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 12.0}}})

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
                           :worklog/user       [:user/jira-username "pipi.langstrumpf"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 4.0}}})

(def invoicing-not-billable-ticket-scenario
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}
                          {:db/id              (storage/people-tempid)
                           :ticket/id          202
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/not-billable}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      4.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 202]
                           :worklog/user       [:user/jira-username "pipi.langstrumpf"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 4.0}}})

(def codecentric-support-is-billable
  {:fixture             default-fixtures
   :billable-components [[{:db/id              (storage/people-tempid)
                           :ticket/id          201
                           :ticket/customer    [:customer/id 2]
                           :ticket/invoicing   :invoicing/fixed-price}
                          {:db/id              (storage/people-tempid)
                           :ticket/id          202
                           :ticket/customer    [:customer/id 1]
                           :ticket/invoicing   :invoicing/support}]
                         [{:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 201]
                           :worklog/user       [:user/jira-username "peter.lustig"]
                           :worklog/hours      4.}
                          {:db/id              (storage/people-tempid)
                           :worklog/ticket     [:ticket/id 202]
                           :worklog/user       [:user/jira-username "pipi.langstrumpf"]
                           :worklog/hours      4.}]]
   :expected-result     #{{:team/id             101
                           :team/name           "Berlin"
                           :team/billable-hours 8.0}}})

(defn create-and-start-new-test-system [db-uri]
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)
        schema (storage/load-resource "datomic-schema.edn")]
    (doseq [tx (get-in schema [:ccpeople2/schema1 :txes])]
      @(d/transact conn tx))
    conn))

(defn stop-test-system [db-uri]
  (d/delete-database db-uri))

(defn test-scenario [scenario]
  (let [db-uri "datomic:mem://test-db"
        conn (create-and-start-new-test-system db-uri)]
    (try
      (doseq [tx (into (:fixture scenario)
                       (:billable-components scenario))]
        @(d/transact conn tx))
      (is (= (:expected-result scenario)
             (storage/billable-hours-for-teams (d/db conn))))
      (finally
        (stop-test-system db-uri)))))

(deftest should-be-billable-for-one-user-one-ticket
  (test-scenario one-user-one-ticket-scenario))

(deftest should-be-billable-for-one-user-one-ticket-equal-worklog
  (test-scenario one-user-one-ticket-equal-worklog-scenario))

(deftest should-be-billable-for-two-teams-two-tickets
  (test-scenario two-user-different-team-one-ticket-scenario))

(deftest should-be-billable-for-one-team-one-ticket
  (test-scenario two-user-same-team-one-ticket-scenario))

(deftest should-be-billable-for-one-team-two-ticket
  (test-scenario two-user-same-team-two-ticket-scenario))

(deftest should-not-book-codecentric-ticket
  (test-scenario booking-codecentric-ticket-scenario))

(deftest should-not-book-not-billable-tikets
  (test-scenario invoicing-not-billable-ticket-scenario))

(deftest should-book-codecentric-support
  (test-scenario codecentric-support-is-billable))
