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
            [figwheel-sidecar.repl-api :as ra]
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

(def figwheel-config
  {:figwheel-options {}                                     ;; <-- figwheel server config goes here
   :build-ids        ["dev"]                                ;; <-- a vector of build ids to start autobuilding
   :all-builds                                              ;; <-- supply your build configs here
                     [{:id           "dev"
                       :figwheel     true
                       :source-paths ["src" "dev"]
                       :compiler     {:main            "cljs.user"
                                      :asset-path      "js"
                                      :output-to       "target/figwheel/public/js/main.js"
                                      :output-dir      "target/figwheel/public/js"
                                      :source-map      true
                                      :source-map-path "js"
                                      ;    :verbose    true
                                      }}]})

(def dev-config
  {:app {:middleware [wrap-stacktrace
                      wrap-last-request]}
   :figwheel
        figwheel-config})

(def config
  (meta-merge config/defaults
;              config/environ
              dev-config))

(defrecord Figwheel []
  component/Lifecycle
  (start [config]
    (ra/start-figwheel! config)
    config)
  (stop [config]
    (ra/stop-figwheel!)
    config))



(defn new-system []
   (into (system/new-system config)
        {:figwheel (map->Figwheel (:figwheel config))}))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(defn cljs-repl []
 (ra/cljs-repl))

(defn reset []
  (reload/reset))

(defn go []
  (reload/go))

(defn routes []
  (-> system :router :routes))

(defn reset-database []
  (d/delete-database (:uri (:database (system)))))

(defn route-handler-for [uri]
  (bidi/match-route (routes) uri))

(defn path-for [handler-tag]
  (bidi/path-for (routes) handler-tag))

(reloaded.repl/set-init! new-system)