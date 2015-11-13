(ns app.domain
  (:require [app.days :as days]
            #?@(:cljs [[reagent.core :refer [atom]]
                       [ajax.core :as ajax]])))

(def average-working-days-2015 253)

(def billable-days-goal 180)

(def initial-state {:days/workdays-till-end-of-year (days/workdays-till-end-of-year (days/today-at-midnight))})

(defonce app-state (atom initial-state))

(defn error-handler [{:keys [status status-text]}]
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
                                                             :error-handler   error-handler
                                                             :response-format :edn
                                                             :keywords?       true}))
                                         #?(:clj (println "todo"))
                                         (and (not (:user new))
                                              (:user old))
                                         (reset! app-state initial-state))))
