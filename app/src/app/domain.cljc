(ns app.domain
  (:require [app.days :as days]
            [app.consultant :as consultant]
            [plumbing.core :refer [map-vals]]

    #?@(:cljs [[reagent.core :refer [atom]]
               [ajax.core :as ajax]
               [cognitect.transit :as transit]
               [goog.array :as garray]
               [cljs-time.core :as time]
               [goog.dom :as dom]
               cljs-time.extend]
        :clj [[clj-time.core :as time]])))

(def billable-days-goal 180)

#?(:cljs
   (defn get-viewport-size []
     (let [window (dom/getWindow)
           viewport-size (dom/getViewportSize window)]
       {:width (.-width viewport-size)
        :height (.-height viewport-size)}))
   :clj
   (defn get-viewport-size []
     {:width 500
      :height 300}))

(def initial-state {:days/workdays-till-end-of-year (days/workdays-till-end-of-year (time/today))
                    :today (time/today)
                    :viewport/size (get-viewport-size)})

(defonce app-state (atom initial-state))

(defn error-handler-fn [{:keys [status status-text]}]
  (println (str "something bad happened: " status " " status-text)))

(defn handle-api-response [data]
  (swap! app-state (partial merge-with merge) data))


(add-watch app-state :user-watch (fn [_ _ old new]
                                   (cond (and (not (:user old))
                                              (:user new))
                                         ;; todo post + CSRF protection
                                         #?(:cljs (ajax/GET "/auth"
                                                            {:params          {:token (-> new :user :user/token)}
                                                             :handler         handle-api-response
                                                             :error-handler   error-handler-fn
                                                             :response-format :transit
                                                             :keywords?       true
                                                             :reader (transit/reader :json
                                                                                     {:handlers
                                                                                      {"date/local" (fn [date-fields]
                                                                                                      (apply time/local-date date-fields))}})}))
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

(defn billed-hours [app-state]
  (reduce + (map :worklog/hours
                 (billable-worklogs app-state))))

(defn days-needed-to-reach-goal [app-state]
  (- billable-days-goal
     (/ (billed-hours app-state)
        8)))

(defn sum-of [k]
  (fn [vs]
    (reduce + (map k vs))))

(defn billed-hours-by-month [app-state]
  (->> (billable-worklogs app-state)
       (group-by (comp time/month :worklog/work-date))
       (map-vals (sum-of :worklog/hours))))

(defn working-days-left-without-today [app-state]
  (dec (count (:days/workdays-till-end-of-year app-state))))

;; vacation ticket TS-2
(def vacation-ticket-id 68000)

;; illness TIMXIII-1588
(def sick-leave-ticket-id 56617)

(defn ticket-days [ticket-id worklogs]
  (into #{}
        (comp (filter (matching :worklog/ticket ticket-id))
              (map :worklog/work-date))
        worklogs))

(defn vacation-days [{:keys [worklogs]}]
  (ticket-days vacation-ticket-id worklogs))

(defn sick-leave-hours [{:keys [worklogs]}]
  (->> worklogs
       (into []
             (comp (filter (matching :worklog/ticket sick-leave-ticket-id))
                   (map :worklog/hours)))
       (reduce +)))

(defn sum-past-vacation-hours
  "vacation booked in the past including today"
  [app-state today]
  (->> (:worklogs app-state)
       (filter (every-pred
                 (matching :worklog/ticket vacation-ticket-id)
                 (fn [{:keys [worklog/work-date]}]
                   (or (= work-date today)
                       (time/before? work-date today)))))
       (map :worklog/hours)
       (apply +)))

;; TODO make user dependent
(def vacation-per-year 30)

(defn number-remaining-holidays [app-state]
  (let [number-taken-vacation-days (/ (sum-past-vacation-hours app-state (:today app-state)) 8.)]
    (- vacation-per-year number-taken-vacation-days)))

(defn plus-one-days-left [remaining-working-days days-to-100-percent]
  (max 0 (- remaining-working-days days-to-100-percent)))

#?(:cljs
   (extend-type js/goog.date.Date
     IEquiv
     (-equiv [o other]
       (and (instance? js/goog.date.Date other)
            (.equals o other)))
     IHash
     (-hash [o]
       (+ (.getTime o)
          (* 37 (.getTimezoneOffset o))))
     IComparable
     (-compare [this other]
       (if (instance? js/Date other)
         (garray/defaultCompare (.valueOf this) (.valueOf other))
         (throw (js/Error. (str "Cannot compare " this " to " other)))))))

(def min-hours-per-day 4)

(defn unbooked-days [app-state]
  (let [today (time/today)
        start-date (consultant/current-period-start today)
        period-days (days/workdays-between start-date today)
        worklogs (:worklogs app-state)
        worklogs-in-period (filter (fn [{:keys [worklog/work-date]}]
                                     (time/within? start-date today work-date))
                                   worklogs)
        day->worklogs (group-by :worklog/work-date worklogs-in-period)
        day->worked-hours (map (fn [[day worklogs]]
                                 [day (reduce + (map :worklog/hours worklogs))])
                               day->worklogs)
        days-with-work-hours-above-min-threshold (->> day->worked-hours
                                                      (filter (fn [[_ worked-hours]]
                                                                (>= worked-hours min-hours-per-day)))
                                                      (map (comp first))
                                                      (into #{}))]
    (remove days-with-work-hours-above-min-threshold period-days)))

(defn actual-work-days-left [app-state]
  (let [remaining-working-days (working-days-left-without-today app-state)
        remaining-vacation-days (number-remaining-holidays app-state)]
    (max 0 (- remaining-working-days remaining-vacation-days))))

(def total-working-days
  (- (count (days/workdays-till-end-of-year (time/local-date 2016 1 1)))
     vacation-per-year))

(def daily-burndown-hours
  (/ (* billable-days-goal 8) total-working-days))

(defn todays-hour-goal [app-state]
  (let [actual-work-days-remaining (actual-work-days-left app-state)]
    (* (- 1 (/ actual-work-days-remaining total-working-days))
       total-working-days
       daily-burndown-hours)))

(defn days-balance
  "balance with respect to today's hour goal"
  [app-state]
  (/ (- (billed-hours app-state) (todays-hour-goal app-state))
     8))

(defn number-sick-leave-days [app-state]
  (/ (sick-leave-hours app-state) 8))