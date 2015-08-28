(ns ^:figwheel-always applic.core
  (:require
              [reagent.core :as reagent]
              [applic.domain :as domain]
              [bidi.bidi :as bidi]
              [goog.events :as events]

              [goog.history.EventType :as EventType])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn hello-world [{:keys [username]}]
  [:h1 (str (:hello/text @domain/app-state) username)])

(defn bye-world [_]
  [:h1 (:bye/text @domain/app-state)])

(def routes ["" {"hello"                :hello
                 ["hello/" [ #"[A-Za-z]+" :username]] :hello-user
                 "bye"                  :bye}])

(defmulti handlers :handler :default :hello)

(defmethod handlers :bye [] bye-world)

(defmethod handlers :hello [] hello-world)
(defmethod handlers :hello-user [] hello-world)

(defn update-page-to-token [token]
  (swap! domain/app-state assoc :page (bidi/match-route routes token)))

(defonce history (doto (Html5History.)
                   (.setEnabled true)
                   (events/listen EventType/NAVIGATE
                                  (fn [e]
                                    (update-page-to-token (.-token e))))))

(update-page-to-token (.getToken history))

(defn dispatcher []
  (let [{page-params :route-params :as route-state} (:page @domain/app-state)]
    ((handlers route-state) page-params)))


(reagent/render-component [dispatcher]
                          (.getElementById js/document "app"))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)

)

