(ns app.storage
  (:require [datomic.api :refer [db q] :as d]
            [io.rkn.conformity :as c]
            [clojure.java.io :as io]
            [environ.core :refer [env]]))

(def people-tempid
  (partial d/tempid :partition/people))

(def uri (get env :datomic-connect-url))
(d/create-database uri)
(def conn (d/connect uri))

(defn load-resource [filename] (read-string (slurp (io/reader (io/resource filename)))))
(def norms-map (load-resource "datomic-schema.edn"))

(c/ensure-conforms conn norms-map [:ccpeople2/schema1])

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

(defn existing-user-data [id]
  (->>  id (d/entity (db conn)) (d/touch) (into {})))
