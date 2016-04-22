(ns user
  (:require [alembic.still :refer [load-project]]
            [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [datomic.api :as d]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [reloaded.repl :refer [init start stop] :as reload]
            [meta-merge.core :refer [meta-merge]]
            [com.stuartsierra.component :as component]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [eftest.runner :as eftest]
            [app.config :as config]
            [app.system :as system]
            [bidi.bidi :as bidi]))

;; last handled request and response
(def rq)
(def rs)

(defn wrap-last-request [handler]
  (fn [req]
    (alter-var-root #'rq req)
    (let [resp (handler req)]
      (alter-var-root #'rs resp)
      resp)))

(defn system []
  reload/system)

(def dev-config
  {:app {:middleware [wrap-stacktrace
                      wrap-last-request]}})

(def config
  (meta-merge config/defaults
;              config/environ
              dev-config))


(defn new-system []
  (system/new-live-system config))

(defn in-memory-system [config]
  (assoc-in config [:datomic :connect-url] "datomic:mem://ccpeople123"))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(defn reset []
  (reload/reset))

(defn go []
  (reload/go))

(defn routes []
  (-> (system) :router :routes))

(defn reset-database []
  (d/delete-database (:connect-url (:datomic config))))

(defn route-handler-for [uri]
  (bidi/match-route (routes) uri))

(defn path-for [handler-tag]
  (bidi/path-for (routes) handler-tag))

(reloaded.repl/set-init! new-system)