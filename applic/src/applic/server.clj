(ns applic.server
  (:require [aleph.http :as http]
            [environ.core :refer [env]]
            [hiccup.page :as page]
            [ring.middleware.defaults :as ring-defaults]))


(defn handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "hi"})

(defn -main [& args]
  (def server
    (http/start-server (ring-defaults/wrap-defaults handler ring-defaults/site-defaults)
                       {:port (Integer/parseInt (env :server/port "8080"))})))