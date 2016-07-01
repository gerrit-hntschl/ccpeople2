(ns ^:figwheel-always ccdashboard.client.core
  (:require
    [reagent.core :as reagent]
    [reagent.interop :refer-macros [$]]
    [ccdashboard.domain.core :as domain]
    [bidi.bidi :as bidi]
    [goog.events :as events]
    cljsjs.react
    [goog.history.EventType :as EventType]
    [ccdashboard.client.locations :as locations]
    [ccdashboard.client.user-stats :as user-stats])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

(def routes ["" {"profile/"  {[:consultant ""] :profile}
                 "people"    :people
                 "locations" :locations}])

(defmulti handlers :handler :default :profile)

(defmethod handlers :locations [] locations/location-page)

(defmethod handlers :profile [params]
  (let [{{consultant :consultant} :route-params} params]
    (cond (nil? consultant)
          (domain/change-selected-consultant (:user/identity @domain/app-state))
          (not= consultant (get-in @domain/app-state [:consultant :consultant/selected]))
          (domain/change-selected-consultant consultant)))
  user-stats/profile-page)

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

(defn sign-in-component []
  (let [state @domain/app-state
        user-sign-in-state (domain/user-sign-in-state state)]
    (cond (nil? user-sign-in-state)
          [:p "Initializing..."]
          (= (:error state) :error/unexpected-api-response)
          [:h2 {:style {:color "black"}} "Oops, something went wrong. Please report issue in #ccdashboard-feedback."]
          user-sign-in-state
          [:div                                             ;{:style {:margin-top "20px"}}
           ;[:a.button {:href "/logout"} (str "Sign out " (:user/display-name (:user state)))]
           [dispatcher]]
          :else
          [:div
           [:div {:style {:margin-top "20px"}}
            [:a.button {:href "/login"} "Sign-in"]
            [:p "Yes, it uses Duo Mobile... But you only need to log-in once, then a cookie will keep you logged in for a year."]]])))

(def toggle-show-menu (reagent/atom false))

(defn toggle-for-show-menu []
  (swap! toggle-show-menu not))

(defn page []
  (let [user-signed-in (domain/user-sign-in-state @domain/app-state)]
    [:div
     [:div#navbar
      (if user-signed-in
        [:label.show-menu {:for "show-menu"}
         [:i.icon-menu.large-icon]])
      [:span#banner
       [:a {:href "/#"} "ccDashboard"]]
      [:input#show-menu {:type "checkbox"
                         :role "button"
                         :on-click toggle-for-show-menu
                         :checked @toggle-show-menu}]
      (if user-signed-in
        [:span#menu
         [:ul
          [:li.menuitem [:a {:href "/#"
                             :on-click toggle-for-show-menu}
                         "Home"]]
          [:li.menuitem [:a {:href "/#locations"
                            :on-click toggle-for-show-menu}
                         "Locations"]]
          [:li.menuitem [:a {:href "/login"
                             :on-click toggle-for-show-menu}
                         [:i.icon-off.medium-icon]]]]])]
     [:div {:style {:text-align "center"}}
      [sign-in-component]]]))


(defn start []
  (domain/call-api)
  (domain/call-team-stats-api)
  (reagent/render-component [page]
                            (.getElementById js/document "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application

)

