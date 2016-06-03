(ns ccdashboard.oauth.core
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.set :as set]
            [ring.util.response :as response]
            [environ.core :refer [env]]
            [ccdashboard.retry :as retry]
            [cheshire.core :as json]
            [ccdashboard.log :as log]
            [schema.core :as s]
            [ccdashboard.domain.data-model :as model]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.sign.jws :as jws]
            [ccdashboard.persistence.core :as storage]
            [clojure.java.io :as io])
  (:import (java.util Collections)
           (net.oauth OAuthServiceProvider OAuthConsumer OAuthAccessor OAuth OAuth$Parameter)
           [net.oauth.client OAuthClient]
           [net.oauth.client.httpclient4 HttpClient4]
           [net.oauth.signature RSA_SHA1]
           (org.apache.http.conn ConnectTimeoutException)
           (org.slf4j LoggerFactory)
           (clojure.lang ExceptionInfo)))

(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger "ccdashboard.oauth"))

(def jws-token-secret (get env :jws-token-secret))

(def request-token-url (str (env :jira-base-url) "/plugins/servlet/oauth/request-token"))

(def authorize-url (str (env :jira-base-url) "/plugins/servlet/oauth/authorize"))

(def access-token-url (str (env :jira-base-url) "/plugins/servlet/oauth/access-token"))

(def consumer-key "ccdashboard_consumer_key")

(def callback-uri (str (env :app-hostname) "/auth"))

(def ^:const aliases-endpoint "https://www.googleapis.com/admin/directory/v1/users/%s/aliases")

(def ^:const tokeninfo-endpoint "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=")

(def oauth->domain-keys {:given_name :user/first-name
                         :family_name :user/last-name
                         :email :user/email
                         ;; sub is a unique immutable identifier for google accounts
                         :sub :user/google-id})

(defn request-token [oauth-accessor callback-url]
  (let [oauth-client (OAuthClient. (HttpClient4.))
        oauth-params [(OAuth$Parameter. OAuth/OAUTH_CALLBACK callback-url)]
        message (.getRequestTokenResponse oauth-client oauth-accessor "POST" oauth-params)]
    {:token (.-requestToken oauth-accessor)
     :secret (.-tokenSecret oauth-accessor)
     :verifier (.getParameter message OAuth/OAUTH_VERIFIER)}))

(defn extract-user-data [{:keys [id-token access-token] :as xx}]
  (let [;; todo check response code
        token-data (-> @(http/get (str tokeninfo-endpoint id-token))
                       :body
                       bs/to-string
                       (json/parse-string keyword))
        ]
    (when (= (:aud token-data) "493824973703-h2ambsalvru64vmegfnebmobp3sel4c7.apps.googleusercontent.com")
      (-> (set/rename-keys token-data oauth->domain-keys)
          (select-keys (vals oauth->domain-keys))))))

(comment (defn get-aliases [user-key access-token]
   @(http/get (format aliases-endpoint user-key) {:headers {"Authorization" (str "Bearer " access-token)}})))

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

;; TODO use database or distributed cache
(def request-token->token-secret (atom {}))

(defn request-token
  ([]
    (request-token "oob"))
  ([callback-uri]
   (let [accessor (create-accessor)
         client (create-client)
         callback-object [(OAuth$Parameter. OAuth/OAUTH_CALLBACK callback-uri)]
         message (.getRequestTokenResponse client accessor "POST" callback-object)
         token-data {:request-token (.-requestToken accessor)
                   :token-secret  (.-tokenSecret accessor)
                   :authorize-url (str authorize-url "?oauth_token=" (.-requestToken accessor))}]
     (swap! request-token->token-secret assoc (.-requestToken accessor) (.-tokenSecret accessor))
     (def ttt token-data)
     token-data)))

(defn access-token [{:keys [request-token token-secret] :as tt} verifier]
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

(defn fetch-jira-raw
  ([uri-suffix]
   (fetch-jira-raw (env :jira-base-url) uri-suffix (env :jira-access-token) (env :jira-consumer-private-key)))
  ([jira-base-uri uri-suffix jira-token jira-consumer-private-key]
   (log/info logger "requesting jira:" uri-suffix)
   (-> (get-req (str jira-base-uri uri-suffix) jira-token jira-consumer-private-key)
       (json/parse-string keyword))))

(def fetch-jira (retry/retryable fetch-jira-raw (some-fn
                                                  (fn [ex]
                                                    (= (.getMessage ex) "connection was closed"))
                                                  (partial instance? ConnectTimeoutException))))

(defn fetch-user-of-token [access-token]
  (->> (fetch-jira (env :jira-base-url) "/rest/api/2/myself"
                   access-token
                   (env :jira-consumer-private-key))
       (s/validate model/JiraUser)))

(defn create-signed-cookie-if-auth-successful [conn oauth-params]
  (def xxreqeq oauth-params)
  (let [{request-token :oauth_token verifier :oauth_verifier} oauth-params
        token-secret (get @request-token->token-secret request-token)
        token (access-token {:request-token request-token :token-secret token-secret} verifier)
        {email-address :user/email :as user} (model/jira-user->domain-user (fetch-user-of-token token))]
    (if-not (.endsWith email-address "@codecentric.de")
      {:result :error
       :email email-address}
      (try
        (let [user-id (storage/user-id-by-email-or-create conn user)
              auth-token (jws/sign {:sub user-id} jws-token-secret)]
          {:result :success
           :token auth-token})
        (catch ExceptionInfo exi
             (if (-> exi (ex-data) (:error) (= :error/unknown-user))
               (do (log/info logger (str "unknown user login attempt: " email-address))
                   {:result :error
                    :email email-address})
               (throw exi)))))))

(defn login-handler []
  (-> (request-token callback-uri) (:authorize-url) (response/redirect)))

(defn get-signed-user-id [req]
  (when-let [cookie-val (get-in req [:cookies "ccdid" :value])]
    (try (-> cookie-val (jws/unsign jws-token-secret) :sub)
         (catch Exception ex
           (def token-decode-ex ex)
           (log/info logger "signed token decoding failed:" (.getMessage ex))
           nil))))
