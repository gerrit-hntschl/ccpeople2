(ns app.system
  (:require [com.stuartsierra.component :as component]
            [aleph.http :as http]
            [schema.core :as s]
            [app.server :as server]
            [clojure.java.io :as io]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [meta-merge.core :refer [meta-merge]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [app.middlewared-handler :refer [handler-component router-component]]
            [app.storage :as storage]
            [ring.middleware.format :as ring-format])
  (:import (com.stuartsierra.component Lifecycle)
           (java.io Closeable)))

(def base-config
  {:app {:middleware [[wrap-not-found :not-found]
                      ring-format/wrap-restful-format
                      [wrap-defaults :defaults]]
         :not-found  (io/resource "errors/404.html")
         :defaults   (meta-merge site-defaults {:static {:resources "public"}})}})

(defrecord Webserver [app]
  Lifecycle
  (start [component]
    (let [server (http/start-server (:handler app) component)]
      (assoc component :server server)))
  (stop [component]
    (when-let [^Closeable server (:server component)]
      (.close server))
    (dissoc component :server)))

(def new-webserver-schema
  {:port s/Int
   s/Keyword s/Any})

(defn aleph-server [{:as opts}]
  (->> opts
       (s/validate new-webserver-schema)
       map->Webserver))

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
          :database (storage/new-datomic-database (:datomic config))
          :conn (storage/new-datomic-connection)
          :schema (storage/new-conform-schema (:datomic config))
          :app (handler-component (:app config))
          :http (aleph-server (:http config))
          :auth-endpoint (server/auth-endpoint)
          :index-endpoint (server/index-endpoint)
          :router (router-component))

        (component/system-using
          {;; web
           :http   [:app]
           :app    [:router]
           :router [:index-endpoint :auth-endpoint]

           ;; datomic
           :database []
           :conn [:database]
           :schema [:conn]

           ;; endpoints
           :auth-endpoint [:conn]
           }))))
