(ns app.server
  (:require
            [app.storage :as storage]
            [environ.core :refer [env]]
            [hiccup.page :as page]
            [app.oauth :as oauth]
            [clojure.java.io :as io]
            [ring.util.response :refer [response redirect content-type] :as resp]
            [buddy.auth :as auth :refer [authenticated?]]
            [com.stuartsierra.component :as component]
            [ring.util.response :as response])
  (:import (org.slf4j LoggerFactory)
           (java.util UUID)))

(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger "app.server"))

(comment


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

(defn login-endpoint []
  (map->Endpoint {:route   "/login"
                  :handler (fn [req]
                             (oauth/login-handler))
                  :tag     :jira/oauth}))

(defn unauthorized-user-response [email-address]
  (-> {:status 400
       :body   (str "Unknown user: " email-address)}
      (response/content-type "text/html")))

(def ccdashboard-cookie-id "ccdid")

(defn logout-endpoint []
  (map->Endpoint {:route   "/logout"
                  :handler (fn [req]
                             (-> (response/redirect "/")
                                 (response/set-cookie ccdashboard-cookie-id "" {:max-age 0})))
                  :tag     :logout}))

(defn auth-handler [conn req]
  (let [{:keys [result] :as cookie-result} (oauth/create-signed-cookie-if-auth-successful conn (:params req))]
    (cond (= :success result)
          (-> (response/redirect "/")
              (response/set-cookie ccdashboard-cookie-id (:token cookie-result) {:http-only true :max-age (* 1 365 24 60 60)}))
          (= :error result)
          (unauthorized-user-response (:email cookie-result))
          :else
          (throw (ex-info (str "unknown result type: " result) cookie-result)))))

(defn auth-endpoint []
  (component/using
    (map->Endpoint {:route           "/auth"
                    :handler-builder (fn [component]
                                       (partial auth-handler (:conn component)))
                    :tag             :auth})
    [:conn]))

(defn api-handler [conn req]
  (def apireq req)
  (if-let [user-id (oauth/get-signed-user-id req)]
    (response (storage/existing-user-data-for-user conn (UUID/fromString user-id)))
    (-> (response {:error :error/unknown-user})
        (resp/status 401))))

(defn api-endpoint []
  (component/using
    (map->Endpoint {:route           "/api"
                    :handler-builder (fn [component]
                                       (partial api-handler (:conn component)))
                    :tag             :api})
    [:conn]))

(defn index-endpoint []
  (map->Endpoint {:route   "/"
                  ;; no component dependencies, so create handler directly
                  :handler (fn [req]
                             (-> (io/resource "public/index.html")
                                 (io/input-stream)
                                 (response)
                                 (content-type "text/html")))
                  :tag     :index}))


