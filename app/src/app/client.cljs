(ns ^:figwheel-always app.client
  (:require
    [reagent.core :as reagent]
    [reagent.interop :refer-macros [$]]
    [app.domain :as domain]
    [bidi.bidi :as bidi]
    [goog.events :as events]
    cljsjs.react
    [app.donut-service :as donut-service]
    ;    [material-ui.core :as ui :include-macros true]
    [goog.history.EventType :as EventType]
    [app.days :as days]
    [cljs.pprint :as pprint])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defn latest-worklog-work-date [state]
  (if-let [worklogs (seq (:worklogs state))]
    (days/format-simple-date (apply max-key #(.getTime %) (map :worklog/work-date worklogs)))
    "?"))

(defn metric-style [text]
  [:span {:style {:font-size 60}} text])

(defn current-stats-did-mount [state-atom component-name]
  #_(donut-service/progress 730 250)
  (let [state @state-atom
        actual-hours (domain/billed-hours state)
        todays-goal-hours (domain/todays-hour-goal state)
        viewport-size (:viewport/size state)]
    (donut-service/balance-view component-name viewport-size todays-goal-hours actual-hours)))

(defn current-stats-update [state-atom component-name this old-argv]
  (let [state @state-atom
        actual-hours (domain/billed-hours state)
        todays-goal-hours (domain/todays-hour-goal state)]
    (donut-service/update-balance-view-transitioned component-name todays-goal-hours actual-hours)))

(defn current-stats [state-atom component-name]
  ;; is it really necessary to deref app-state here just to trigger an invocation of component-did-update??
  (let [_ @state-atom]
    [:div {:style {:margin-left  "auto"
                   :margin-right "auto"}
           :id component-name}
     [:svg]]))

(defn current-stats-component [state-atom component-name]
  (reagent/create-class {:reagent-render       (partial current-stats state-atom component-name)
                         :component-did-mount  (partial current-stats-did-mount state-atom component-name)
                         :component-did-update (partial current-stats-update state-atom component-name)}))


(defn profile-page [_]
  (let [state @domain/app-state
        remaining-work-days-minus-vacation (str (domain/actual-work-days-left state))
        rem-holidays (str (domain/number-remaining-holidays state))
        days-to-100-percent (pprint/cl-format nil "~,2f" (domain/days-needed-to-reach-goal state))
        unbooked-days-count (str (count (domain/unbooked-days state)))
        billed-days (pprint/cl-format nil "~,1f%" (* 100 (/ (domain/billed-hours state) 8 domain/billable-days-goal)))
        num-sick-leave-days (str (domain/number-sick-leave-days state))
        today-str (days/month-day-today (:today state))]
    (cond (= (:error state) :error/unknown-user)
          [:h2 {:style {:color "white"}} "Sorry, but we don't know that user."]
          (:user state)
          [:div {:style {:margin-left  "auto"
                         :margin-right "auto"
                         :width        "100%"
                         :color "#121212"}}
           [:h2 "Today " (metric-style today-str)]
           [:p "days w/o booked hours"
            (metric-style unbooked-days-count)]
           [current-stats-component domain/app-state "goal-stats"]
           [:ul {:padding 1
                 :style   {:width "100%"}}

            [:li "your workdays left"
             (metric-style remaining-work-days-minus-vacation)]
            [:li "days needed to reach 100%"
             (metric-style days-to-100-percent)]
            [:li "remaining leave"
             (metric-style rem-holidays)]
            [:li "sick leave"
             (metric-style num-sick-leave-days)]]
           [:div (str "Latest workdate considered: " (latest-worklog-work-date state))]])))

(defn tabs []
  [:div ""])


(def routes ["" {"profile" :profile
                 "people" :people}])

(defmulti handlers :handler :default :profile)

(defmethod handlers :profile [] profile-page)

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
          user-sign-in-state
          [:div {:style {:margin-top "20px"}}
           [:a.button {:href "/logout"} (str "Sign out " (:user/display-name (:user state)))]
           [dispatcher]]
          :else

          [:div {:style {:margin-top "20px"}}
           [:a.button {:href "/login"} "Sign-in"]
           [:p "Yes, it uses Duo Mobile... But you only need to log-in once, then a cookie will keep you logged in for a year."]])))

(defn page []
  [:div
   [:div {:style {:text-align "center"}}
    [sign-in-component]]])


(defn start []
  (domain/call-api)
  (reagent/render-component [page]
                            (.getElementById js/document "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)

)

