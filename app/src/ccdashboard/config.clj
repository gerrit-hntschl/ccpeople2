(ns ccdashboard.config
  (:require [environ.core :refer [env]]))

(def defaults
   {:http {:port 8000}
    :datomic {:schema-file "datomic-schema.edn"
              :schema-name :ccpeople2/schema1
              :connect-url (get env :datomic-connect-url)}
    :jira {:jira-base-url (get env :jira-base-url)
           :jira-access-token (get env :jira-access-token)
           :jira-tempo-api-token (get env :jira-tempo)
           :jira-consumer-private-key (get env :jira-consumer-private-key)}})

(def environ
  {:http {:port (some-> env :port (Integer.))}
   ;:datomic {:connect-url (get env :datomic-connect-url)}
   })
