(ns cards.consultant
  (:require
    [devcards.core]
    [ccdashboard.client.user-stats :refer [current-stats-component progress-component]]
    [ccdashboard.domain.core :as domain]
    [reagent.core :as reagent]
    [cljs-time.core :as time]
    [ccdashboard.domain.days :as days])
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
                                                    :worklog/ticket 9
                                                    :worklog/work-date (time/local-date 2016 1 11)})
                              :tickets [{:ticket/id 9
                                         :ticket/customer 1}]})))

(def bad-state-atom (atom (merge domain/initial-state
                             {:today    (time/local-date 2016 1 15)
                              :worklogs (repeat 7 {:worklog/hours 8
                                                    :worklog/ticket 9
                                                   :worklog/work-date (time/local-date 2016 1 11)})
                              :tickets [{:ticket/id 9
                                         :ticket/customer 1}]})))

(defcard-rg positive-billable-hours-balance
            [stats-and-summary good-state "goal-good"])

(defcard-rg negative-billable-hours-balance
            [stats-and-summary bad-state-atom "goal-bad"])

(def last-day-state (merge domain/initial-state
                           {:today    (time/local-date 2016 12 31)
                            :worklogs (repeat 180 {:worklog/hours 8
                                                   :worklog/ticket 9
                                                   :worklog/work-date (time/local-date 2016 1 11)})
                            :tickets [{:ticket/id 9
                                       :ticket/customer 1}]}))

(defcard-rg last-day-no-holidays-taken
            [stats-and-summary (atom last-day-state) "goal-last-day-no-holidays"])

(def all-holidays-taken-state
  (merge-with concat
              last-day-state
              {:worklogs (repeat 30 {:worklog/hours 8
                                     :worklog/ticket domain/vacation-ticket-id
                                     :worklog/work-date (time/local-date 2016 3 3)})
               :tickets [{:ticket/id domain/vacation-ticket-id
                          :ticket/customer 0}]
               :customers [{:customer/id 0
                            :customer/name "codecentric"}]}))

(def all-holidays-atom (atom all-holidays-taken-state))

(defcard-rg last-day-all-holidays-taken
            [stats-and-summary all-holidays-atom "goal-last-day-all-holidays"])

(def late-start-state (merge domain/initial-state
                             {:today    (time/local-date 2016 12 31)
                              :worklogs (concat (repeat 16 {:worklog/hours     8
                                                            :worklog/ticket    9
                                                            :worklog/work-date (time/local-date 2016 12 2)})
                                                (repeat 2 {:worklog/hours     8
                                                            :worklog/ticket    domain/vacation-ticket-id
                                                            :worklog/work-date (time/local-date 2016 12 3)}))
                              :tickets  [{:ticket/id       9
                                          :ticket/customer 1}
                                         {:ticket/id domain/vacation-ticket-id
                                          :ticket/customer 0}]
                              :customers [{:customer/id 0
                                           :customer/name "codecentric"}]
                              :user     {:user/start-date (time/local-date 2016 12 1)}}))

(defcard-rg last-day-late-start-balance
            [stats-and-summary (atom late-start-state)
             "goal-late-start"])

(defcard-rg progress-intermediate
            [progress-component good-state "progress-intermediate" :workdays-left-actually :workdays-total])