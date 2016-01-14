(ns app.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [duct.middleware.errors :refer [wrap-hide-errors]]
            [meta-merge.core :refer [meta-merge]]
            [app.system :as system]
            [app.config :as config]
            [app.log :as log])
  (:import (java.lang.invoke MethodHandles)
           (org.slf4j LoggerFactory)))

(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger (.lookupClass (MethodHandles/lookup))))

(def prod-config
  {:app {:middleware     [[wrap-hide-errors :internal-error]]
         :internal-error (io/resource "errors/500.html")}})

(def config
  (meta-merge config/defaults
              config/environ
              prod-config))

(defn -main [& args]
  (let [system (system/new-live-system config)]
    (log/info logger "Starting HTTP server on port" (-> system :http :port))
    (component/start system)))
