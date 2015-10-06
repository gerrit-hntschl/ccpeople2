(ns app.storage
  (:require [datomic.api :refer [db q] :as d]
            [io.rkn.conformity :as c]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]))

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
                               :db/id user-tempid)])
        db-after (:db-after r)
        tempids (:tempids r)]
    (d/resolve-tempid db-after tempids user-tempid)))

(defn user-id-by-email [dbval email]
  (q-one '[:find ?u
           :in [$ ?email]
           :where [?u :user/email ?email]]
         dbval))

(defn user-id-by-unique-identity [dbval openid-identity]
  (q-one '{:find [?u]
           :in [$ ?openid-identity]
           :where [[?u :user/google-id ?openid-identity]]}
         dbval
         openid-identity))

(defn user-id-by-openid-or-create [conn user-data]
  (if-let [id (user-id-by-unique-identity (db conn) (:user/google-id user-data))]
    id
    (create-openid-user conn user-data)))

(defn existing-user-data [conn id]
  (->>  id (d/entity (db conn)) (d/touch) (into {})))

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
