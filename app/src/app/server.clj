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
            [buddy.auth.middleware :as auth-middleware :refer [wrap-authentication]]))

(def token-secret (get env :token-secret "supersecrettokensecret"))

(def auth-backend (jws-backend {:secret token-secret :options {:alg :hs512}}))

(defn handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "hi"})

(defn layout [body]
  (page/html5                                               ;; {:manifest "offline.appcache"}
    [:head
     [:title "CCPeople2"]
     [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/font-awesome/3.2.0/css/font-awesome.css"}]
     [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.1/css/bootstrap-combined.min.css"}]]
    body))

(def routes ["/" {""       :index
                  "auth"  :auth}])

(def navbar [:div.navbar.navbar-inverse
             [:div.navbar-inner
              [:div.container
               [:a {:class "brand" :href "#"} "CCPeople2"]]]])


(defn login-page []
  (layout
    [:body
     navbar
     [:div.login (form/form-to [:post "/oauth2"]
                               (form/hidden-field
                                 "identifier"
                                 "https://www.google.com/accounts/o8/id")
                               [:button.btn.btn-primary.btn-large
                                {:type "submit"} [:span.glyphicon.glyphicon-google-plus " Sign in with Google"]])]]))

(defn wrap-unauthenticated-redirect-to-login [handler]
  (fn [req]
    (if (auth/authenticated? req)
      (handler req)
      (redirect "/login"))))

(defn auth-handler [req]
  (if-let [user-data (oauth/extract-user-data (get-in req [:params :token]))]
    (let [stored-user (-> (storage/user-id-by-openid-or-create storage/conn user-data)
                          (storage/existing-user-data))]
      (response stored-user))
    {:status 400
     :body "Invalid token"}))

(def handlers {:main  (-> handler (wrap-unauthenticated-redirect-to-login) (auth-middleware/wrap-authentication auth-backend))
               :login (fn [req] (-> (login-page) (response) (content-type "text/html")))
               :index (fn [req] (-> (io/resource "public/index.html")
                                    (io/input-stream)
                                    (response)
                                    (content-type "text/html")))
               :auth  #'auth-handler})

(defn wrap-last-request [handler]
  (fn [req]
    (def rq req)
    (let [resp (handler req)]
      (def rs resp)
      resp)))

(defn -main [& args]
  (def server
    (http/start-server (-> (bidi-ring/make-handler routes handlers)
                           (wrap-last-request)
                           (ring-defaults/wrap-defaults ring-defaults/site-defaults)
                           (ring-format/wrap-restful-format))
                       {:port (Integer/parseInt (env :server/port "8000"))})))
