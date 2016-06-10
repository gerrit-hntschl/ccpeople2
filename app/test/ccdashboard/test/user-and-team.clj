(ns ccdashboard.test.user-and-team
  (:require [ccdashboard.ticket-import.core :refer :all]
            [datomic.api :as d]
            [ccdashboard.system :as system]
            [ccdashboard.config :as config]
            [com.stuartsierra.component :as component]
            [ccdashboard.persistence.core :as storage]
            [clojure.test :refer [is deftest run-tests]])
  (:import (java.util UUID)))

(def ^:const team-berlin  {:id 101 :name "Berlin"})

(def ^:const team-hamburg {:id 200 :name "Hamburg"})

(def ^:const bob-baumeister "bob.baumeister")

(def ^:const bob-email (str bob-baumeister "@codecentric.de"))

(def ^:const team-member-test-list  [{:team-id (:id team-berlin)  :jira-username "peter.lustig"}
                                     {:team-id (:id team-berlin)  :jira-username "pipi.langstrumpf"}
                                     {:team-id (:id team-berlin)  :jira-username "bibi.blocksberg"}
                                     {:team-id (:id team-hamburg) :jira-username "kaptain.blaubaer"}
                                     {:team-id (:id team-hamburg) :jira-username bob-baumeister}])

(defn to-datomic-test-user [member]
  (let [jira-username (:jira-username member)]
    {:db/id              (storage/people-tempid)
     :user/email         (str jira-username "@codecentric.de")
     :user/jira-username jira-username}))

(defn test-team-member [member]
  {:member      {:name (:jira-username member)}
   :membership  {:teamId (:team-id member)}})

(def team-scenario
  {:fixture         [(mapv to-datomic-test-user team-member-test-list)]
   :jira-state      {:teams-all        [team-berlin team-hamburg]
                     :team-members-all (mapv test-team-member team-member-test-list)}
   :expected-result {:entity-key :team-member
                     :id-key     :user/team
                     :expected   #{{}}}})

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
        conn (:conn sys)
        jira-import-data (jira-user-start-date-import {:dbval (d/db conn) :jira (:jira-client sys)})]
    (println jira-import-data)
    (try
      (doseq [tx (:fixture scenario)]
        @(d/transact conn tx))
      (doseq [tx (:db-transactions jira-import-data)]
        @(d/transact conn tx))
      ;; execute the scheduled import synchronously
      (def impres ((first @(:schedule-atom sys))))
      ;; simulate login
      (let [user-id (storage/create-openid-user conn
                                                {:user/email         bob-email
                                                 :user/display-name  "Bob Baumeister"
                                                 :user/jira-username bob-baumeister})]
        (def rr (storage/existing-user-data-for-user conn user-id))
        (println rr)
        (assert-result-matches (:expected-result scenario) rr))
      (finally
        (component/stop sys)))
    jira-import-data))

(deftest should-be-user-in-team
  (test-scenario team-scenario))