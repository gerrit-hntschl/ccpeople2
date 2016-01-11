(ns app.oauth
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.set :as set]
            [clojure.data.json :as json]
            [environ.core :refer [env]])
  (:import (java.util Collections)
           (net.oauth OAuthServiceProvider OAuthConsumer OAuthAccessor OAuth OAuth$Parameter)
           [net.oauth.client OAuthClient]
           [net.oauth.client.httpclient4 HttpClient4]
           [net.oauth.signature RSA_SHA1]))

(def request-token-url (str (env :jira-base-url) "/plugins/servlet/oauth/request-token"))

(def authorize-url (str (env :jira-base-url) "/plugins/servlet/oauth/authorize"))

(def access-token-url (str (env :jira-base-url) "/plugins/servlet/oauth/access-token"))

(def consumer-key "ccdashboard_consumer_key")

(def callback-uri "https://ccDashboard.callback")

(def ^:const tokeninfo-endpoint "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=")

(def oauth->domain-keys {:given_name :user/first-name
                         :family_name :user/last-name
                         :email :user/email
                         ;; sub is a unique immutable identifier for google accounts
                         :sub :user/google-id})

(defn extract-user-data [token]
  (let [;; todo check response code
        token-data (-> @(http/get (str tokeninfo-endpoint token))
                       :body
                       bs/to-string
                       (json/read-str :key-fn keyword))
        ]
    (when (= (:aud token-data) "493824973703-h2ambsalvru64vmegfnebmobp3sel4c7.apps.googleusercontent.com")
      (-> (set/rename-keys token-data oauth->domain-keys)
          (select-keys (vals oauth->domain-keys))))))

(defn- create-accessor
  ([]
    (create-accessor (env :jira-consumer-private-key)))
  ([jira-consumer-private-key]
   (let [service-provider (OAuthServiceProvider. request-token-url authorize-url access-token-url)
         consumer (doto (OAuthConsumer. callback-uri consumer-key nil service-provider)
                    (.setProperty RSA_SHA1/PRIVATE_KEY jira-consumer-private-key)
                    (.setProperty OAuth/OAUTH_SIGNATURE_METHOD OAuth/RSA_SHA1))]
     (OAuthAccessor. consumer))))

(defn create-client []
  (OAuthClient. (HttpClient4.)))

(defn request-token []
  (let [accessor (create-accessor)
        client (create-client)
        callback-object [(OAuth$Parameter. OAuth/OAUTH_CALLBACK "oob")]
        message (.getRequestTokenResponse client accessor "POST" callback-object)]
    {:request-token (.-requestToken accessor)
     :token-secret (.-tokenSecret accessor)
     :authorize-url (str authorize-url "?oauth_token=" (.-requestToken accessor))}))

(defn access-token [{:keys [request-token token-secret]} verifier]
  (let [accessor (create-accessor)
        client (create-client)]
    (set! (.-requestToken accessor) request-token)
    (set! (.-tokenSecret accessor) token-secret)
    (-> client
        (.getAccessToken accessor "POST" [(OAuth$Parameter. OAuth/OAUTH_VERIFIER verifier)])
        (.getToken))))

(defn get-req [url access-token jira-consumer-private-key]
  (let [accessor (create-accessor jira-consumer-private-key)
        client (create-client)]
    (set! (.-accessToken accessor) access-token)
    (.readBodyAsString (.invoke client accessor url (Collections/emptySet)))))
