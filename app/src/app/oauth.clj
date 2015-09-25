(ns app.oauth
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.set :as set]
            [clojure.data.json :as json]))

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
