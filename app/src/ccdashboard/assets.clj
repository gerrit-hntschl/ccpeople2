;; adapted from https://github.com/weavejester/ring-webjars/blob/master/src/ring/middleware/webjars.clj
(ns ccdashboard.assets
  (:require [ring.util.response :as resp]
            [ring.middleware.head :as head]
            [ring.util.request :as req]
            [ring.util.codec :as codec]))

(defn- request-path [request]
  (codec/url-decode (req/path-info request)))

(defn assets [url prefix]
  (when (.startsWith url prefix)
    (subs url (count prefix))))

(defn wrap-assets
  ([handler] (wrap-assets handler "/assets"))
  ([handler prefix]
   (fn [request]
     (if (#{:head :get} (:request-method request))
       (if-let [path (assets (request-path request) prefix)]
         (-> (resp/resource-response path)
             (head/head-response request))
         (handler request))
       (handler request)))))
