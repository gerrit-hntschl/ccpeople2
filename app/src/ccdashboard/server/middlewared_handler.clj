(ns ccdashboard.server.middlewared-handler
  (:require [com.stuartsierra.component :as component]
            [bidi.ring :as bidi-ring]))

(defrecord Router [routes router]
  component/Lifecycle
  (start [component]
    (if-not (:router component)

      (let [endpoints (filter (every-pred :route :tag :handler) (vals component))
            routes ["" (into {}
                             (map (juxt :route :tag))
                             endpoints)]
            handlers (into {}
                           (map (juxt :tag
                                      :handler))
                           endpoints)
            router (bidi-ring/make-handler routes handlers)]
        (assoc component
          :routes routes
          :router router))
      component))
  (stop [this] (dissoc this :router)))

(defn router-component []
  (map->Router {}))

(defn- middleware-fn [component middleware]
  (if (vector? middleware)
    (let [[f & keys] middleware
          arguments  (map #(get component %) keys)]
      #(apply f % arguments))
    middleware))



(defn- compose-middleware [{:keys [middleware] :as component}]
  (->> (reverse middleware)
       (map #(middleware-fn component %))
       (apply comp identity)))

(defrecord Handler [defaults middleware router]
  component/Lifecycle
  (start [component]
    (if-not (:handler component)
      (let [wrap-mw (compose-middleware component)
            handler (wrap-mw (:router router))]
        (assoc component :handler handler))
      component))
  (stop [component]
    (dissoc component :handler)))

(defn handler-component [options]
  (map->Handler options))
