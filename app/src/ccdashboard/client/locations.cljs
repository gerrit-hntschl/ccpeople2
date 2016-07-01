(ns ccdashboard.client.locations
  (:require [reagent.core :as reagent]
            [ccdashboard.client.dataviz :as dataviz]
            [clojure.set :as set]
            [ccdashboard.domain.core :as domain]
            [ccdashboard.analytics.mixpanel :as mixpanel] ))

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

(defn location-page [_]
  (mixpanel/track "locations")
  [locations-component domain/app-state "teamstats"])
