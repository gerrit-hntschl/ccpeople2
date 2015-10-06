(ns app.config
  (:require [environ.core :refer [env]]))

(def defaults
   {:http {:port 8000}
    :datomic {:schema-file "datomic-schema.edn"
                        :schema-name :ccpeople2/schema1
                        :connect-url (get env :datomic-connect-url)}})

(def environ
  {:http {:port (some-> env :port (Integer.))}
   ;:datomic {:connect-url (get env :datomic-connect-url)}
   })
