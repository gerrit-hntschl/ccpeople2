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
    [cljs.pprint :as pprint]
    [clojure.string :as str])
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
  (let [state @state-atom
        model-data (domain/app-model {:state state})
        actual-hours (:hours-billed model-data)
        todays-goal-hours (:hour-goal-today model-data)
        viewport-size (:viewport/size state)
        billable-days-goal-scaled (:billable-days-goal-scaled model-data)]
    (donut-service/balance-view component-name
                                viewport-size
                                todays-goal-hours
                                actual-hours
                                billable-days-goal-scaled)))

(defn current-stats-update [state-atom component-name this old-argv]
  (let [state @state-atom
        model-data (domain/app-model {:state state})
        actual-hours (:hours-billed model-data)
        todays-goal-hours (:hour-goal-today model-data)
        total-billable-hours-goal (* 8 (:billable-days-goal-scaled model-data))]
    (donut-service/update-balance-view-transitioned component-name todays-goal-hours actual-hours total-billable-hours-goal)))

(defn current-stats [state-atom component-name]
  ;; is it really necessary to deref app-state here just to trigger an invocation of component-did-update??
  (let [_ @state-atom]
    [:div {:style {:margin-left  "auto"
                   :margin-right "auto"}
           :id    component-name}
     [:svg]]))

(defn current-stats-component [state-atom component-name]
  (reagent/create-class {:reagent-render       (partial current-stats state-atom component-name)
                         :component-did-mount  (partial current-stats-did-mount state-atom component-name)
                         :component-did-update (partial current-stats-update state-atom component-name)}))

(defn progress-render [state-atom component-name]
  [:div {:id component-name
         :style {:margin-top "10px"}}
   [:svg]])

(defn progress-did-mount [state-atom component-name stat-key total-stat-key format-fn]
  (let [model-data (domain/app-model {:state @state-atom})
        stat (get model-data stat-key)
        total-stat (get model-data total-stat-key)]
    (donut-service/progress component-name stat total-stat format-fn)))

(defn progress-update [state-atom component-name stat-key total-stat-key])

(defn progress-component [state-atom component-name stat-key total-stat-key & [format-fn]]
  (let [format-fn (or format-fn str)]
    (reagent/create-class {:reagent-render       (partial progress-render state-atom component-name)
                           :component-did-mount  (partial progress-did-mount state-atom component-name stat-key total-stat-key format-fn)
                           :component-did-update (partial progress-update state-atom component-name stat-key total-stat-key)})))

(defn format-days [n]
  (if (= n 1)
    (str "1 day")
    (str n " days")))

(defn days-rect [icon-name color background-color label number-days]
  [:div {:style {:width "90px"
                 :height "120px"
                 :margin "6px"
                 :background-color background-color
                 :color color
                 :display "flex"
                 :justify-content "space-around"
                 :flex-direction "column"}}
   [:div
    [:div {:class "circle"
           :display "flex"}
     [:i {:class icon-name}]]]
   [:div label]
   [:div {:style {:font-size "1.2em"}} (format-days number-days)]])

(defn user-stats [state]
  (let [model-data (domain/app-model {:state state})
        rem-holidays (:number-holidays-remaining model-data)
        days-without-booked-hours (:days-without-booked-hours model-data)
        formatted-missing-days (->> days-without-booked-hours
                                    (map days/format-simple-date)
                                    (str/join ", "))
        unbooked-days-count (count days-without-booked-hours)
        num-sick-leave-days (:number-sick-leave-days model-data)
        used-leave (:number-taken-vacation-days model-data)
        number-parental-leave-days (:number-parental-leave-days model-data)
        number-planned-vacation-days (:number-planned-vacation-days model-data)
        today-str (days/month-day-today (:today state))]
    [:div {:style {:overflow "hidden"}}
     (when (pos? unbooked-days-count)
       [:div {:style {:background-color "#e36588"
                      :color            "white"
                      :padding          "8px 15px 8px 15px"}}
        (str "days w/o booked hours: " unbooked-days-count)
        [:br]
        formatted-missing-days])
     [:div {:style {:margin-left  "auto"
                    :margin-right "auto"
                    :width        "100%"
                    :color        "#121212"}}
      [:div {:style {:display         "flex"
                     :flex-wrap       "wrap"
                     :justify-content "center"
                     :border-bottom   "1px solid #f3f3f3"}}
       [:div
        [:h2 today-str]
        [current-stats-component domain/app-state "goal-stats"]]
       [:div {:style {:margin-top "10px"}}
        [:div
         "days needed to reach 100%"
         [progress-component
          domain/app-state
          "days-to-100"
          :days-to-reach-goal
          :billable-days-goal-scaled
          (fn [x] (str (js/Math.round x)))]]
        [:div
         "your workdays left"
         [progress-component
          domain/app-state
          "workdays-left"
          :workdays-left-actually
          :workdays-total]]]]
      [:div {:style {:display         "flex"
                     :justify-content "center"
                     :flex-wrap       "wrap"
                     ;:margin-left  "auto"
                     ;:margin-right "auto"
                     ;:width        "100%"
                     }}
       [:div {:style {:display        "flex"
                      :flex-direction "column"
                      :align-items    "flex-start"
                      :margin-right   "10px"}}
        [:h2 "Holidays"]
        [:div {:style {:display "flex"}}
         [days-rect "icon-flight" "black" "#f3f3f3" "Planned" number-planned-vacation-days]
         [days-rect "icon-globe" "white" "#9eb25d" "Free" rem-holidays]
         [days-rect "icon-cancel" "black" "#f3f3f3" "Used" used-leave]]]
       [:div {:style {:display        "flex"
                      :flex-direction "column"
                      :align-items    "flex-start"
                      :border-left    "1px solid #f3f3f3"
                      :padding-left   "10px"}}
        [:h2 "Absence"]
        [:div {:style {:display "flex"}}
         [days-rect "icon-medkit" "black" "#a5e2ed" "Sickness" num-sick-leave-days]
         (when (pos? number-parental-leave-days)
           [days-rect "icon-award" "white" "#9eb25d" "Parental leave" number-parental-leave-days])]]]]]))

(defn profile-page [_]
  (let [state @domain/app-state]
    (cond (= (:error state) :error/unknown-user)
          [:h2 {:style {:color "black"}} "Sorry, but we don't know that user."]
          (:user state)
          [user-stats state])))

(defn location-page [_]
  [:h1 "Comming Soon..."])

(defn tabs []
  [:div ""])


(def routes ["" {"profile" :profile
                 "people" :people
                 "location" :location}])

(defmulti handlers :handler :default :profile)

(defmethod handlers :profile [] profile-page)

(defmethod handlers :location [] location-page)

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

(defn sign-in-only-content [content]
  (if (domain/user-sign-in-state @domain/app-state)
    content
    [:div]))

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

(defn page []
  [:div
   [:div {:class "header"
          :style {:display         "flex"
                  :flex-direction  "row"
                  :justify-content "space-around"}}
    ;; layout hack: empty divs move the title and sign-off button more to the center
    [:div]
    [:div {:style {:font-size "1.3em"}} "ccDashboard"]
    (sign-in-only-content [:a#logout {:href "/logout"}
                           [:i.icon-off.large-icon]])
    (sign-in-only-content [:a#location {:href "/location"}
                           "location View"])
    [:div]]
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

