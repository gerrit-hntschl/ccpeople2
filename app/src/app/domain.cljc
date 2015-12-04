(ns app.domain
  (:require [app.days :as days]
            #?@(:cljs [[reagent.core :refer [atom]]
                       [ajax.core :as ajax]])))

(def average-working-days-2015 253)

(def billable-days-goal 180)

(def initial-state {:days/workdays-till-end-of-year (days/workdays-till-end-of-year (days/today-at-midnight))})

(defonce app-state (atom initial-state))

(defn error-handler-fn [{:keys [status status-text]}]
  (println (str "something bad happened: " status " " status-text)))

(defn handle-api-response [data]
  (println "server data" (pr-str data))
  (swap! app-state (partial merge-with merge) data))

(add-watch app-state :user-watch (fn [_ _ old new]
                                   (cond (and (not (:user old))
                                              (:user new))
                                         ;; todo post + CSRF protection
                                         #?(:cljs (ajax/GET "/auth"
                                                            {:params          {:token (-> new :user :user/token)}
                                                             :handler         handle-api-response
                                                             :error-handler   error-handler-fn
                                                             :response-format :edn
                                                             :keywords?       true}))
                                         #?(:clj (println "todo"))
                                         (and (not (:user new))
                                              (:user old))
                                         (reset! app-state initial-state))))

(defn matching [k v]
  (fn [m]
    (= v (get m k))))

(defn customer-id-by-name [customer-name customers]
  (->> customers
       (filter (matching :customer/name customer-name))
       (first)
       (:customer/id)))

(defn billable-worklogs [{:keys [worklogs tickets customers]}]
  (let [codecentric-id (customer-id-by-name "codecentric" customers)
        codecentric-ticket-ids (->> tickets
                                    (filter (matching :ticket/customer codecentric-id))
                                    (into #{} (map :ticket/id)))
        billable-ticket-ids (->> tickets
                                 (filter (fn [ticket]
                                           (not= (:ticket/invoicing ticket)
                                                 :invoicing/not-billable)))
                                 (into #{} (map :ticket/id)))]
    (->> worklogs
         (remove (comp codecentric-ticket-ids :worklog/ticket))
         (filter (comp billable-ticket-ids :worklog/ticket)))))

(defn billable-days [state]
  (- billable-days-goal
     (/ (reduce + (map :worklog/hours
                       (billable-worklogs state)))
        8)))