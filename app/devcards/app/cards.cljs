(ns app.cards
  (:require
    [devcards.core]
    [app.client :refer [current-stats-component]]
    [app.domain :as domain]
    [reagent.core :as reagent]
    [cljs-time.core :as time]
    [app.days :as days]
) ; just for example
  (:require-macros
    [devcards.core :as dc :refer [defcard defcard-rg]]))

(defn billing-summary [state-atom]
      (let [state @state-atom]
           [:p
            "Today: " (days/month-day-today (:today state))
            " Billed days: " (count (:worklogs state))
            " Taken holidays: " (/ (domain/sum-past-vacation-hours state (:today state)) 8.)]))

(defn stats-and-summary [state-atom component-name]
      [:div [billing-summary state-atom]
       [current-stats-component state-atom component-name]])

(def init-state (atom domain/initial-state))

(defcard-rg nothing-booked
            [stats-and-summary init-state "goal-init"])

(def good-state (atom (merge domain/initial-state
                             {:today    (time/local-date 2016 1 15)
                              :worklogs (repeat 10 {:worklog/hours 8
                                                    :worklog/ticket 9})
                              :tickets [{:ticket/id 9
                                         :ticket/customer 1}]})))

(def bad-state-atom (atom (merge domain/initial-state
                             {:today    (time/local-date 2016 1 15)
                              :worklogs (repeat 7 {:worklog/hours 8
                                                    :worklog/ticket 9})
                              :tickets [{:ticket/id 9
                                         :ticket/customer 1}]})))

(defcard-rg positive-billable-hours-balance
            [stats-and-summary good-state "goal-good"])

(defcard-rg negative-billable-hours-balance
            [stats-and-summary bad-state-atom "goal-bad"])

(def last-day-state (merge domain/initial-state
                           {:today    (time/local-date 2016 12 31)
                            :worklogs (repeat 180 {:worklog/hours 8
                                                   :worklog/ticket 9})
                            :tickets [{:ticket/id 9
                                       :ticket/customer 1}]}))

(defcard-rg last-day-no-holidays-taken
            [stats-and-summary (atom last-day-state) "goal-last-day-no-holidays"])

(def all-holidays-atom (atom (merge-with concat
                                         last-day-state
                                         {:worklogs (repeat 30 {:worklog/hours 8
                                                                :worklog/ticket domain/vacation-ticket-id
                                                                :worklog/work-date (time/local-date 2016 3 3)})
                                          :tickets [{:ticket/id domain/vacation-ticket-id
                                                     :ticket/customer 0}]
                                          :customers [{:customer/id 0
                                                       :customer/name "codecentric"}]})))

(defcard-rg last-day-all-holidays-taken
            [stats-and-summary all-holidays-atom "goal-last-day-all-holidays"] )
