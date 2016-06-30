(ns ccdashboard.test.user_and_team
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

(def team-name-and-id-scenario
  {:fixture         []
   :jira-state      {:teams-all (->> [{:id   101
                                       :name "Berlin"}
                                      {:id   200
                                       :name "Hamburg"}]
                                     (mapv (complete-non-nil model/JiraTeam)))}
   :expected-result #{{:team/id   101
                       :team/name "Berlin"}
                      {:team/id   200
                       :team/name "Hamburg"}}})

(defn team-name-and-id-test-function [dbval scenario]
  (is (= (:expected-result scenario)
        (storage/all-team-informations dbval))))

(def user-to-team-scenario
  {:fixture         [[{:db/id              (storage/people-tempid)
                       :user/jira-username "peter.lustig"
                       :user/email         "peter.lustig@codecentric.de"
                       :user/display-name  "Peter Lustig"}
                      {:db/id              (storage/people-tempid)
                       :user/jira-username "pipi.langstrumpf"
                       :user/email         "pipi.langstrumpf@codecentric.de"
                       :user/display-name  "Pipi Langstrumpf"}
                      {:db/id              (storage/people-tempid)
                       :user/jira-username "bibi.blocksberg"
                       :user/email         "bibi.blocksberg@codecentric.de"
                       :user/display-name  "Bibi Blocksberg"}
                      {:db/id              (storage/people-tempid)
                       :user/jira-username "kaptain.blaubaer"
                       :user/email         "kaptain.blaubaer@codecentric.de"
                       :user/display-name  "Kaptain Blaubaer"}
                      {:db/id              (storage/people-tempid)
                       :user/jira-username "bob.baumeister"
                       :user/email         "bob.baumeister@codecentric.de"
                       :user/display-name  "Bob Baumeister"}]]
   :jira-state      {:teams-all        (->> [{:id   101
                                              :name "Berlin"}
                                             {:id   200
                                              :name "Hamburg"}]
                                            (mapv (complete-non-nil model/JiraTeam)))
                     :team-members-all (->> [{:member     {:name "peter.lustig"}
                                              :membership {:teamId 101}}
                                             {:member     {:name "pipi.langstrumpf"}
                                              :membership {:teamId 101}}
                                             {:member     {:name "bibi.blocksberg"}
                                              :membership {:teamId 101}}
                                             {:member     {:name "kaptain.blaubaer"}
                                              :membership {:teamId 200}}
                                             {:member     {:name "bob.baumeister"}
                                              :membership {:teamId 200}}]
                                            (mapv (complete-non-nil model/JiraTeamMember)))}
   :expected-result #{{:user {:user/jira-username "peter.lustig",
                              :user/email "peter.lustig@codecentric.de",
                              :user/display-name "Peter Lustig",
                              :user/team 101},
                       :worklogs [],
                       :tickets [],
                       :customers []}
                      {:user {:user/jira-username "kaptain.blaubaer",
                              :user/email "kaptain.blaubaer@codecentric.de",
                              :user/display-name "Kaptain Blaubaer",
                              :user/team 200},
                       :worklogs [],
                       :tickets [],
                       :customers []}
                      {:user {:user/jira-username "pipi.langstrumpf",
                              :user/email "pipi.langstrumpf@codecentric.de",
                              :user/display-name "Pipi Langstrumpf",
                              :user/team 101},
                       :worklogs [],
                       :tickets [],
                       :customers []}
                      {:user {:user/jira-username "bibi.blocksberg",
                              :user/email "bibi.blocksberg@codecentric.de",
                              :user/display-name "Bibi Blocksberg",
                              :user/team 101},
                       :worklogs [],
                       :tickets [],
                       :customers []}
                      {:user {:user/jira-username "bob.baumeister",
                              :user/email "bob.baumeister@codecentric.de",
                              :user/display-name "Bob Baumeister",
                              :user/team 200},
                       :worklogs [],
                       :tickets [],
                       :customers []}}})

(defn user-to-team-test-function [dbval scenario]
  (let [data (set (->> (first (:fixture scenario))
                       (map :user/jira-username)
                       (map (partial storage/entity-id-by-username dbval))
                       (map (partial storage/existing-user-data dbval))))]
    (is (= (:expected-result scenario) data))))

(defn new-in-memory-system [config]
  (assoc-in config [:datomic :connect-url] (format "datomic:mem://%s" (UUID/randomUUID))))

(defn new-test-system [scenario]
  (-> (system/new-system (new-in-memory-system config/defaults))
      ;; don't need an http server for testing currently
      (dissoc :http)
      (assoc :jira-client (map->JiraFakeClient (:jira-state scenario)))
      (component/system-using {:jira-importer [:conn :jira-client :scheduler]})))

(defn test-scenario [scenario test-function]
  (let [sys (component/start (new-test-system scenario))
        conn (:conn sys)]
    (try
      (doseq [tx (:fixture scenario)]
        @(d/transact conn tx))
      (doseq [tx (:db-transactions (jira-user-start-date-import {:dbval (d/db conn)
                                                                 :jira  (:jira-client sys)}))]
        @(d/transact conn tx))
      (test-function (d/db conn) scenario)
      (finally
        (component/stop sys)))))

(deftest schould-be-in-datomic-team-name-and-id
  (test-scenario team-name-and-id-scenario team-name-and-id-test-function))

(deftest should-be-in-datomic-user-to-team
  (test-scenario user-to-team-scenario user-to-team-test-function))
