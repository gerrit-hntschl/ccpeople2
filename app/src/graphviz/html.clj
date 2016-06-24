(ns graphviz.html
  (:require [cheshire.core :as json]
            [hiccup.page :as page]
            [graphviz.core :as graphviz]
            [ring.util.request :as req]
            [ring.util.codec :as codec]
            [ring.util.response :as resp]
            [clojure.string :as str])
  (:import (java.io File)))

(defn dev-html [graph-svg-str graph-desc]
  (page/html5
    [:head
     [:link {:rel "stylesheet" :href "/css/dev.css"}]
     [:link {:rel "stylesheet" :href "//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.3.0/styles/default.min.css"}]
     [:link {:rel "stylesheet" :href "/css/default.css"}]
     ]
    [:body
     graph-svg-str
     [:script {:src "/js/compiled/graphviz-interact.js"}]
     [:script (str "graphviz.interact.start(" (json/generate-string graph-desc) ");")]
     [:script {:src "/js/highlight.pack.js"}]
     [:script "hljs.initHighlightingOnLoad();"]]))

(defn viz-graph [g]
  (let [tmp-file (File/createTempFile "viz-graph" ".html")]
    (-> (graphviz/as-svg-str g 350)
        (dev-html (graphviz/graph-description g 350))
        (->> (spit tmp-file)))
    tmp-file))

(defn- request-path [request]
  (codec/url-decode (req/path-info request)))

(defn some-viz-graph-route [url prefix]
  (when (.startsWith url prefix)
    (subs url (inc (count prefix)))))

(defn resolve-graph [graph-qname]
  (let [[var-ns var-name] (str/split graph-qname #"/")]
    (var-get (ns-resolve (the-ns (symbol var-ns)) (symbol var-name)))))

(defn wrap-viz-graph [handler]
  ;; todo make configurable
  (let [node-width 350]
    (fn [request]
      (if (= :get (:request-method request))
        (if-let [var-name (some-viz-graph-route (request-path request) "/dev2")]
          (if-let [graph (resolve-graph var-name)]
            (-> (dev-html (graphviz/as-svg-str graph node-width)
                          (graphviz/graph-description graph node-width))
                (resp/response)
                (resp/content-type "text/html; charset=utf-8"))
            (resp/not-found (str "no such graph: " var-name)))
          (handler request))
        (handler request)))))