(ns app.server
  (:require [aleph.http :as http]
            [bidi.ring :as bidi-ring]
            [bidi.bidi :as bidi]
            [app.storage :as storage]
            [environ.core :refer [env]]
            [hiccup.form :as form]
            [hiccup.page :as page]
            [app.oauth :as oauth]
            [clojure.java.io :as io]
            [ring.middleware.defaults :as ring-defaults]
            [ring.middleware.format :as ring-format]
            [ring.util.response :refer [response redirect content-type]]
            [buddy.sign.jws :as jws]
            [buddy.auth :as auth :refer [authenticated?]]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth.middleware :as auth-middleware :refer [wrap-authentication]]
            [com.stuartsierra.component :as component]))

(comment
  (def token-secret (get env :token-secret "supersecrettokensecret"))

  (def auth-backend (jws-backend {:secret token-secret :options {:alg :hs512}}))

  (defn layout [body]
    (page/html5                                             ;; {:manifest "offline.appcache"}
      [:head
       [:title "CCPeople2"]
       [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/font-awesome/3.2.0/css/font-awesome.css"}]
       [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.1/css/bootstrap-combined.min.css"}]]
      body))


  (defn wrap-unauthenticated-redirect-to-login [handler]
    (fn [req]
      (if (auth/authenticated? req)
        (handler req)
        (redirect "/login")))))

(defn auth-handler [conn req]
  (if-let [user-data (oauth/extract-user-data (get-in req [:params :token]))]
    (let [stored-data (storage/existing-user-data-for-user conn user-data)]
      (println "responsedata:" stored-data)
      (response stored-data))
    {:status 400
     :body "Invalid token"}))

(defrecord Endpoint [route handler-builder tag handler]
  component/Lifecycle
  (start [component]
    (if (:handler component)
      component
      (assoc component :handler (handler-builder component))))
  (stop [component]
    (if (:handler-builder component)
      (dissoc component :handler)
      component)))

(defn auth-endpoint []
  (component/using
    (map->Endpoint {:route           "/auth"
                    :handler-builder (fn [component]
                                       (partial auth-handler (:conn component)))
                    :tag             :auth})
    [:conn]))

(defn index-endpoint []
  (map->Endpoint {:route   "/"
                  ;; no component dependencies, so create handler directly
                  :handler (fn [req]
                             (time (-> (io/resource "public/index.html")
                                  (io/input-stream)
                                  (response)
                                  (content-type "text/html"))))
                  :tag     :index}))


