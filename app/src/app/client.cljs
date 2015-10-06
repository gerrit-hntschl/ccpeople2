(ns ^:figwheel-always app.client
  (:require
              [reagent.core :as reagent]
              [app.domain :as domain]
              [bidi.bidi :as bidi]
              [goog.events :as events]
              [app.gsignin :as gs]
              [goog.history.EventType :as EventType])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn profile-page [_]
  [:div
   [:h1 (str (:user/first-name (:user @domain/app-state))
             " "
             (:user/last-name (:user @domain/app-state)))]
   [:pre (:user/email (:user @domain/app-state))]])

(defn bye-world [_]
  [:h1 (:bye/text @domain/app-state)])

(defn tabs []
  [:div ""])

(def routes ["" {"profile" :profile
                 "people" :people}])

(defmulti handlers :handler :default :profile)

(defmethod handlers :people [] bye-world)

(defmethod handlers :profile [] profile-page)

(defn update-page-to-token [token]
  (swap! domain/app-state assoc :page (bidi/match-route routes token)))

(defonce history (doto (Html5History.)
                   (.setEnabled true)
                   (events/listen EventType/NAVIGATE
                                  (fn [e]
                                    (update-page-to-token (.-token e))))))

(update-page-to-token (.getToken history))

(gs/watch-user :auth-watch
               (fn [user]
                 (swap! domain/app-state assoc :user user)))

(defn dispatcher []
  (let [{page-params :route-params :as route-state} (:page @domain/app-state)]
    ((handlers route-state) page-params)))

(defn page []
  [:div
   [gs/sign-in]
   [dispatcher]])

(defn start []
  (reagent/render-component [page]
                            (.getElementById js/document "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)

)

