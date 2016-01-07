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
            [app.worklog :as worklog]
            [ring.middleware.format :as ring-format]
    ;; to load data reader
            app.date
            [cognitect.transit :as transit]
            [app.log :as log])
  (:import (com.stuartsierra.component Lifecycle)
           (java.io Closeable)
           (java.util.concurrent Executors TimeUnit)
           (java.lang.invoke MethodHandles)
           (org.slf4j LoggerFactory)))

(def base-config
  {:app {:middleware     [[wrap-not-found :not-found]
                          [ring-format/wrap-restful-format :transit-custom]
                          [wrap-defaults :defaults]]
         :not-found      (io/resource "errors/404.html")
         :defaults       (meta-merge site-defaults {:static {:resources "public"}})
         :transit-custom {:response-options
                          {:transit-json
                           {:handlers
                            {org.joda.time.LocalDate
                             (transit/write-handler "date/local"
                                                    (fn [local-date] [(.getYear local-date)
                                                                      (.getMonthOfYear local-date)
                                                                      (.getDayOfMonth local-date)]))}}}}}})

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

(defrecord ExecutorScheduler [executor]
  component/Lifecycle
  (start [this]
    (if (:executor this)
      this
      (assoc this :executor (Executors/newScheduledThreadPool 1))))
  (stop [this]
    (when-let [executor (:executor this)]
      (.shutdownNow executor))
    (dissoc this :executor))

  worklog/Scheduler
  (schedule [this f]
    ;; todo handle exception happening in f
    (.scheduleWithFixedDelay (:executor this) f 10 60 TimeUnit/SECONDS)))

(defn new-scheduler []
  (map->ExecutorScheduler {}))

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
          :database (storage/new-datomic-database (:datomic config))
          :conn (storage/new-datomic-connection)
          :schema (storage/new-conform-schema (:datomic config))

          :scheduler (new-scheduler)
          :jira-client (worklog/new-jira-rest-client (:jira config))
          :jira-importer (worklog/new-jira-importer)
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

           ;; jira imports
           :jira-importer [:conn :jira-client :scheduler]

           ;; endpoints
           :auth-endpoint [:conn]
           }))))

(defn new-offline-worklog-system [config]
  (-> (new-system config)
      (assoc :jira-downloaded-worklogs-client (worklog/new-downloaded-worklogs-jira-client
                                                (map #(str "worklogmonth" % ".xml")
                                                     (range 1 13))))
      (component/system-using
        {:jira-downloaded-worklogs-client [:jira-client]
         :jira-importer {:conn :conn
                         :scheduler :scheduler
                         :jira-client :jira-downloaded-worklogs-client}})))

(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger (.lookupClass (MethodHandles/lookup))))

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error logger ex "Uncaught exception on" (.getName thread) " : " (.getMessage ex))
      (def exex ex))))