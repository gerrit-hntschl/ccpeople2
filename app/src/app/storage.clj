(ns app.storage
  (:require [datomic.api :refer [db q] :as d]
            [io.rkn.conformity :as c]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [plumbing.core :refer [update-in-when]]
            [com.stuartsierra.component :as component]
            app.date
            [app.data-model :as model]))

(def people-tempid
  (partial d/tempid :partition/people))

(defn load-resource [filename] (read-string (slurp (io/reader (io/resource filename)))))

(defn q-one [query & inputs]
  (ffirst (apply q query inputs)))

(defn create-openid-user
  [conn user]
  (let [user-tempid (people-tempid -1)
        r @(d/transact conn [
                             (assoc user
                               :user/id (d/squuid)
                               :db/id [:user/email (:user/email user)])])
        db-after (:db-after r)
        tempids (:tempids r)]
    (d/resolve-tempid db-after tempids user-tempid)))

(defn all-usernames [dbval]
  (->> (d/q '{:find  [?username]
              :in    [$]
              :where [[?e :user/jira-username ?username]]}
            dbval)
       (into #{} (map first))))

(defn user-id-by-email [dbval email]
  (q-one '[:find ?u
           :in $ ?email
           :where [?u :user/email ?email]]
         dbval
         email))

(defn user-id-by-unique-identity [dbval openid-identity]
  (q-one '{:find [?u]
           :in [$ ?openid-identity]
           :where [[?u :user/google-id ?openid-identity]]}
         dbval
         openid-identity))

(defn user-id-by-openid-or-create [conn user-data]
  (if-let [id (user-id-by-unique-identity (db conn) (:user/google-id user-data))]
    id
    (do (create-openid-user conn user-data)
        (user-id-by-unique-identity (db conn) (:user/google-id user-data)))))

(def as-map
  (comp (partial into {}) d/touch))

(defn pull-without [& ks]
  (comp #(apply dissoc % ks)
        as-map))

(defn domain-worklog [db-worklog]
  (-> db-worklog
      (as-map)
      ;; for now keep the user implicit
      (dissoc :db/id :worklog/user)
      (update :worklog/ticket :ticket/id)
      (model/to-domain-worklog)))

(defn domain-ticket [db-ticket]
  (-> db-ticket
      (as-map)
      (dissoc :db/id)
      (update-in-when [:ticket/customer] :customer/id)
      (model/to-domain-ticket)))

(defn domain-customer [db-customer]
  (-> db-customer
      (as-map)
      (dissoc :db/id)
      (model/to-domain-customer)))

(defn existing-user-data [conn id]
  (def xxid id)
  (let [user-entity (d/entity (db conn) id)
        db-worklogs (:worklog/_user user-entity)
        tickets (into #{} (map :worklog/ticket) db-worklogs)
        customers (into #{} (keep :ticket/customer) tickets)]
    {:user      (->> user-entity (d/touch) (into {}))
     :worklogs  (mapv domain-worklog db-worklogs)
     :tickets   (mapv domain-ticket tickets)
     :customers (mapv domain-customer customers)}))

(defrecord DatomicDatabase [uri]
  component/Lifecycle
  (start [component]
    (d/create-database uri)
    component)
  (stop [component]
    (d/shutdown false)
    component))

(defn new-datomic-database [config]
  (map->DatomicDatabase {:uri (:connect-url config)}))

(defrecord DatomicConnection []
  component/Lifecycle
  (start [this] (d/connect (get-in this [:database :uri])))
  (stop [this] this))

(defn new-datomic-connection []
  (->DatomicConnection))

(defrecord DatomicSchema [schema-file schema-name]
  component/Lifecycle
  (start [component]
    (c/ensure-conforms (:conn component)
                       (load-resource schema-file)
                       [schema-name])
    component)
  (stop [this] this))

(defn new-conform-schema [options]
  (map->DatomicSchema {:schema-file (:schema-file options)
                       :schema-name (:schema-name options)}))

(defn existing-user-data-for-user [conn user-data]
  (->> (user-id-by-openid-or-create conn user-data)
       (existing-user-data conn)))
