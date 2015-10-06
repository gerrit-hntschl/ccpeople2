(ns user
  (:require [figwheel-sidecar.repl-api :as rapi]
            [alembic.still :refer [load-project]]
            [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [reloaded.repl :refer [system init start stop] :as reload]
            [meta-merge.core :refer [meta-merge]]
            [com.stuartsierra.component :as component]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [duct.component.figwheel :as figwheel]
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

(def dev-config
  {:app {:middleware [wrap-stacktrace
                      wrap-last-request]}
   :figwheel
        {:css-dirs ["resources/public/css"]
         :builds   [{:source-paths ["src" "dev"]
                     :build-options
                                   {:optimizations :none
                                    :main "cljs.user"
                                    :asset-path "js"
                                    :output-to  "target/figwheel/public/js/main.js"
                                    :output-dir "target/figwheel/public/js"
                                    :source-map true
                                    :source-map-path "js"}}]}})

(def config
  (meta-merge config/defaults
;              config/environ
              dev-config))

(defn new-system []
   (into (system/new-system config)
        {:figwheel (figwheel/server (:figwheel config))}))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(defn cljs-repl []
 (rapi/cljs-repl))

(defn reset []
  (reload/reset))

(defn go []
  (reload/go))

(defn routes []
  (-> system :router :routes))

(defn route-handler-for [uri]
  (bidi/match-route (routes) uri))

(defn path-for [handler-tag]
  (bidi/path-for (routes) handler-tag))

(reloaded.repl/set-init! new-system)