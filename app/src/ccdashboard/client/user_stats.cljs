(ns ccdashboard.client.user-stats
  (:require [clojure.string :as str]
            [ccdashboard.domain.days :as days]
            [ccdashboard.domain.core :as domain]
            [ccdashboard.client.dataviz :as dataviz]
            [reagent.core :as reagent]
            [clojure.set :as set]
            [ccdashboard.client.react :refer [select]]))

(defn monthly-stats-hours [state-atom component-name component]
  (let [state @state-atom
        model-data (domain/app-model {:state state})
        monthly-hours (:monthly-hours model-data)]
    (dataviz/create-stacked-bar-view component-name component monthly-hours)))

(defn monthly-stats-update [state-atom component-name component]
  (dataviz/update-bar-data component-name
                           (:chart (reagent/state component))
                           (:monthly-hours (domain/app-model {:state @state-atom}))))

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

(defn monthly-stats [state-atom component-name]
  (let [_ @state-atom]
    [:div {:style {:margin-left  "auto"
                   :margin-right "auto"
                   :width 700
                   :height 300}
           :id    component-name}
     [:svg]]))

(defn monthly-component [state-atom component-name]
  (reagent/create-class {:reagent-render       (partial monthly-stats state-atom component-name)
                         :component-did-mount  (partial monthly-stats-hours state-atom component-name)
                         :component-did-update (partial monthly-stats-update state-atom component-name)}))

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

(defn set-location-profile! [jira-username]
  (set! (.. js/window -location) (str "#profile/" (.-value jira-username))))

(defn user-stats [state]
  (let [model-data (domain/app-model {:state state})
        logged-in-user? (:my-stats? model-data)
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
       [select {:name             "consultant-search"
                :autosize         false
                :clearable        false
                :backspaceRemoves false
                :noResultsText    "Unknown consultant"
                :value            (-> state :consultant :consultant/selected)
                :options          (->> state
                                       :users/all
                                       (into []
                                             (map (fn [consultant]
                                                    (set/rename-keys consultant {:user/display-name  :label
                                                                                 :user/jira-username :value})))))
                :onChange         set-location-profile!}]]
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
      [:div {:style {:display "flex"
                     :flex-wrap "wrap"
                     :justify-content "center"
                     :border-bottom "1px solid #f3f3f3"}}
       [:div [:h2 "Billed hours by month"]
        [monthly-component domain/app-state "monthly-stats"]]]
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
       (if logged-in-user?
         (list [:div {:style {:display        "flex"
                              :flex-direction "column"
                              :align-items    "flex-start"
                              :border-left    "1px solid #f3f3f3"
                              :padding-left   "10px"}}
                [:h2 "Absence"]
                [:div {:style {:display "flex"}}
                 [days-rect "icon-medkit" "black" "#a5e2ed" "Sickness" num-sick-leave-days]
                 (when (pos? number-parental-leave-days)
                   [days-rect "icon-award" "white" "#9eb25d" "Parental leave" number-parental-leave-days])]]))]]]))

(defn profile-page [_]
  (let [state @domain/app-state]
    (cond (= (:error state) :error/unknown-user)
          (list [:h2 {:style {:color "black"}} "Sorry, but we don't know that user. "]
                [:p "Users are created only for users that booked on a TS ticket or users that have a start-date configured in Jira. If you started during the year, ask your administrator to add a start-date."])
          (:user state)
          [user-stats state])))
