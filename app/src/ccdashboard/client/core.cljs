(ns ^:figwheel-always ccdashboard.client.core
  (:require
    [reagent.core :as reagent]
    [reagent.interop :refer-macros [$]]
    [ccdashboard.domain.core :as domain]
    [bidi.bidi :as bidi]
    [goog.events :as events]
    cljsjs.react
    cljsjs.react-select
    cljsjs.d3
    cljsjs.nvd3
    [ccdashboard.client.dataviz :as dataviz]
    ;    [material-ui.core :as ui :include-macros true]
    [goog.history.EventType :as EventType]
    [ccdashboard.domain.days :as days]
    [cljs.pprint :as pprint]
    [clojure.string :as str]
    [clojure.set :as set]
    [ccdashboard.analytics.mixpanel :as mixpanel])
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
    (dataviz/balance-view component-name
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
    (dataviz/update-balance-view-transitioned component-name todays-goal-hours actual-hours total-billable-hours-goal)))

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
    (dataviz/progress-create component-name)
    (dataviz/progress-update component-name stat total-stat format-fn)))

(defn progress-update [state-atom component-name stat-key total-stat-key format-fn]
  (let [model-data (domain/app-model {:state @state-atom})
        stat (get model-data stat-key)
        total-stat (get model-data total-stat-key)]
    (dataviz/progress-update component-name stat total-stat format-fn)))

(defn progress-component [state-atom component-name stat-key total-stat-key & [format-fn]]
  (let [format-fn (or format-fn str)]
    (reagent/create-class {:reagent-render       (partial progress-render state-atom component-name)
                           :component-did-mount  (partial progress-did-mount state-atom component-name stat-key total-stat-key format-fn)
                           :component-did-update (partial progress-update state-atom component-name stat-key total-stat-key format-fn)})))

(defn location-stats [state-atom component-name]
  [:div {:style {:margin-left  "auto"
                 :margin-right "auto"}
         :id    component-name}
   [:svg]])

(defn location-stats-did-mount [state-atom component-name]
  (let [state @state-atom
        stats (:team/stats state)
        team-hours-stats (map (fn [team]
                                (update team :value (partial * (/ 8))))
                              (set/rename stats {:team/billable-hours :value}))
        team-member-count (set/rename stats {:team/member-count :value})]
    (dataviz/team-stats-multibarchart component-name
                                      (get-in state [:viewport/size :width])
                                      [{:key    "Billable Work Days"
                                        :color  "#A5E2ED"
                                        :values (sort-by :name team-hours-stats)}
                                       {:key    "Members"
                                        :color  "#F1DB4B"
                                        :values (sort-by :name team-member-count)}])))

(defn locations-component [state-atom component-name]
  (if (nil? (:team/stats @state-atom))
    [:p "Loading Stats..."]
    (reagent/create-class {:reagent-render      (partial location-stats state-atom component-name)
                           :component-did-mount (partial location-stats-did-mount state-atom component-name)})))

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

(defn formatted-days-comma-separated [days]
  (->> days
       (map days/format-simple-date)
       (str/join ", ")))

;; creates a constant that can be overriden at compile time
(goog-define timetrack-uri "https://the.timetracking-system.url")

(defn days-info [description background-color font-color days]
  (let [unbooked-days-count (count days)]
    (when (pos? unbooked-days-count)
      ;; to make the entire div clickable, position it relative
      ;; and add link with empty span which is positioned absolute with width and height of 100%
      [:div {:style {:background-color background-color
                     :color            font-color
                     :padding          "8px 15px 8px 15px"
                     :position "relative"}}
       [:a {:href timetrack-uri
            :class "div-link"
            :target "_blank"} [:span]]
       (str description unbooked-days-count)
       [:br]
       (formatted-days-comma-separated days)])))

(def select (reagent/adapt-react-class js/Select))

(defn set-location-profile! [jira-username]
  (set! (.. js/window -location) (str "#profile/" (.-value jira-username))))

(defn user-stats [state]
  (let [model-data (domain/app-model {:state state})
        rem-holidays (:number-holidays-remaining model-data)
        days-without-booked-hours (:days-without-booked-hours model-data)
        days-below-threshold (:days-below-threshold model-data)
        num-sick-leave-days (:number-sick-leave-days model-data)
        used-leave (:number-taken-vacation-days model-data)
        number-parental-leave-days (:number-parental-leave-days model-data)
        number-planned-vacation-days (:number-planned-vacation-days model-data)
        today-str (days/month-day-today (:today state))]
    [:div {:style {:overflow "hidden"}}
     (days-info "days w/o booked hours: " "#e36588" "white" days-without-booked-hours)
     (days-info "days w/ less than 4 hours booked: " "#f1db4b" "#626161" days-below-threshold)

     [:div {:style {:margin-left  "auto"
                    :margin-right "auto"
                    :width        "100%"
                    :color        "#121212"}}
      [:div {:style {:display         "flex"
                     :justify-content "center"
                     :border-bottom   "1px solid #f3f3f3"}}
       [select {:name     "consultant-search"
                :autosize false
                :clearable false
                :backspaceRemoves false
                :noResultsText "Unknown consultant"
                :value    (-> state :consultant :consultant/selected)
                :options  (->> state
                               :users/all
                               (into []
                                     (map (fn [consultant]
                                            (set/rename-keys consultant {:user/display-name  :label
                                                                         :user/jira-username :value})))))
                :onChange set-location-profile!}]]
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
  (mixpanel/track "locations")
  [locations-component domain/app-state "teamstats"])

(defn tabs []
  [:div ""])

(defn change-selected-consultant [consultant]
  (swap! domain/app-state
         assoc-in
         [:consultant :consultant/selected]
         consultant))

(def routes ["" {"profile/"  {[:consultant ""] :profile}
                 "people"    :people
                 "locations" :locations}])

(defmulti handlers :handler :default :profile)

(defmethod handlers :profile [params]
  (let [{{consultant :consultant} :route-params} params]
    (cond (nil? consultant)
          (change-selected-consultant (:user/identity @domain/app-state))
          (not= consultant (get-in @domain/app-state [:consultant :consultant/selected]))
          (change-selected-consultant consultant)))
  profile-page)

(defmethod handlers :locations [] location-page)

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
                       [:i.icon-off.medium-icon]]]]]]
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

