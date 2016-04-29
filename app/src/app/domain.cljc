(ns app.domain
  (:require [app.days :as days]
            [app.consultant :as consultant]
            [plumbing.core :refer [map-vals]]
            [ajax.core :as ajax]
            [cognitect.transit :as transit]
            [app.mixpanel :as mixpanel]
            [app.util :refer [matching]]

            [app.graph :as graph]
    #?@(:cljs [[reagent.core :refer [atom]]
               [goog.array :as garray]
               [cljs-time.core :as time]
               [goog.dom :as dom]
               cljs-time.extend
               [plumbing.core :refer-macros [fnk]]]
        :clj [[clj-time.core :as time]
              [plumbing.core :refer [fnk]]])))

(def standard-billable-days-goal 180)

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

(def initial-state {:today         (time/today)
                    :viewport/size (get-viewport-size)})

(defonce app-state (atom initial-state))

(defn error-handler-fn [{:keys [status status-text]}]
  (reset! app-state (assoc-in initial-state [:user :user/signed-in?] false))
  (mixpanel/track "hit")
  (println (str "api response: " status " " status-text)))

(defn track-user [jira-username]
  (mixpanel/identify jira-username)
  (mixpanel/track "signin"))

(defn handle-api-response [data]
  (swap! app-state merge (assoc-in data [:user :user/signed-in?] true))
  (track-user (get-in data [:user :user/jira-username])))


(defn call-api []
  (ajax/GET "/api"
     {:handler         handle-api-response
      :error-handler   error-handler-fn
      :response-format :transit
      :keywords?       true
      :reader          (transit/reader :json
                                       {:handlers
                                        {"date/local" (fn [date-fields]
                                                        (apply time/local-date date-fields))}})}))

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

(defn hours-billed [app-state]
  (reduce + (map :worklog/hours
                 (billable-worklogs app-state))))

(defn sum-of [k]
  (fn [vs]
    (reduce + (map k vs))))

;; vacation ticket TS-2
(def vacation-ticket-id 68000)

;; illness ticket TS-5
(def sick-leave-ticket-id 68003)

;; parental leave TS-345
(def parental-leave-ticket-id 71746)

(defn worktype-fn [{:keys [worklogs tickets customers]}]
  (let [codecentric-id (customer-id-by-name "codecentric" customers)
        codecentric-ticket-ids (->> tickets
                                    (filter (matching :ticket/customer codecentric-id))
                                    (into #{} (map :ticket/id)))
        billable-ticket-ids (->> tickets
                                 (filter (fn [ticket]
                                           (not= (:ticket/invoicing ticket)
                                                 :invoicing/not-billable)))
                                 (into #{} (map :ticket/id)))
        ticket->worktype (-> (zipmap billable-ticket-ids (repeat :billable))
                             (assoc vacation-ticket-id :vacation
                                    sick-leave-ticket-id :sickness
                                    parental-leave-ticket-id :parental-leave
                                    )
                             )

        ]
    (fn [worklog]
      (get ticket->worktype (:worklog/ticket worklog) :other)
      )
    )
  )

(defn hours-by-month [app-state]
  (->> (:worklogs app-state)
       (group-by (comp time/month :worklog/work-date))
       (map-vals (fn [worklogs]
                   (->> worklogs
                        (group-by (worktype-fn app-state))
                        (map-vals (sum-of :worklog/hours)))))))

(defn working-days-left-without-today [today]
  (dec (count (days/workdays-till-end-of-year today))))



(defn ticket-days [ticket-id worklogs]
  (into #{}
        (comp (filter (matching :worklog/ticket ticket-id))
              (map :worklog/work-date))
        worklogs))

(defn sick-leave-hours [{:keys [worklogs]}]
  (->> worklogs
       (into []
             (comp (filter (matching :worklog/ticket sick-leave-ticket-id))
                   (map :worklog/hours)))
       (reduce +)))

(defn sum-vacation-hours [interval-pred app-state today]
  (->> (:worklogs app-state)
       (filter (every-pred
                 (matching :worklog/ticket vacation-ticket-id)
                 (interval-pred today)))
       (map :worklog/hours)
       (apply +)))

(def sum-past-vacation-hours
  "vacation booked in the past including today"
  (partial sum-vacation-hours (fn [today]
                                (fn [{:keys [worklog/work-date]}]
                                  (or (= work-date today)
                                      (time/before? work-date today))))))

(def sum-planned-vacation-hours
  "vacation booked in the future, excluding today"
  (partial sum-vacation-hours (fn [today]
                                (fn [{:keys [worklog/work-date]}]
                                  (time/after? work-date today)))))

(defn user-start-date [state]
  (-> state (:user) (:user/start-date)))

(defn goal-start-date
  "If a consultant starts working during the current year, then the :user/start-date is used, otherwise 1st of January."
  [state]
  (let [current-year (time/year (:today state))]
    (if-let [start-date (user-start-date state)]
      (if (= current-year (time/year start-date))
        start-date
        (time/local-date current-year 1 1))
      (time/local-date current-year 1 1))))

(defn goal-start-date-scaled-percentage
  "Depending on the goal-start-date we have to scale goals and vacation days. Returns the percentage of the year the
  consultant works for codecentric. Calculates the percentage based on months."
  ;; TODO does it happen that people start on days other than 1st and 15th?
  [start-date]
  (let [start-month (time/month start-date)
        number-of-complete-months (- 12 start-month)
        start-month-days (time/number-of-days-in-the-month start-date)
        number-of-worked-days-in-start-month (- start-month-days
                                                (dec (time/day start-date)))
        start-month-percentage (/ number-of-worked-days-in-start-month start-month-days)]
    (/ (+ number-of-complete-months start-month-percentage) 12)))

;; TODO make user dependent
(def vacation-per-year 30)


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

(defn unbooked-days [app-state period-start-date]
  (let [today (:today app-state)
        yesterday (time/minus today (-> 1 time/days))
        ;; ignore today
        period-days (days/workdays-between period-start-date yesterday)
        worklogs (:worklogs app-state)
        worklogs-in-period (filter (fn [{:keys [worklog/work-date]}]
                                     ;; the end of the period has to be today, because within excludes dates equal to end
                                     (time/within? period-start-date today work-date))
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


(defn user-sign-in-state [state]
  (get-in state [:user :user/signed-in?]))

(def app-model-graph
  {:monthly-hours                    (fnk [state]
                                            (hours-by-month state))
   :worklogs-billable                     (fnk [state]
                                            (billable-worklogs state))
   ;; the consultant specific goal start date...
   ;; 1st of January if consultant employment did not start this year, otherwise employment start.
   :consultant-start-date                 (fnk [state]
                                            (goal-start-date state))

   ;; the current open period for booking billable hours, unless before the consultant start date
   :current-period-start-date             (fnk [state consultant-start-date]
                                            (let [period-start (consultant/current-period-start (:today state))]
                                              (if (time/before? period-start consultant-start-date)
                                                consultant-start-date
                                                period-start)))

   :hours-billed                          (fnk [worklogs-billable]
                                            (reduce + (map :worklog/hours
                                                           worklogs-billable)))
   :parental-leave-days                   (fnk [state]
                                            (into #{}
                                                  (comp (filter (matching :worklog/ticket parental-leave-ticket-id))
                                                        (map :worklog/work-date))
                                                  (:worklogs state)))
   :number-parental-leave-days            (fnk [parental-leave-days]
                                            (count parental-leave-days))
   :parental-leave-days-till-today        (fnk [consultant-start-date state parental-leave-days]
                                            (into #{}
                                                  (filter (days/in-range-pred consultant-start-date (:today state)))
                                                  parental-leave-days))
   :number-parental-leave-days-till-today (fnk [parental-leave-days-till-today]
                                            (count parental-leave-days-till-today))
   :work-duration-scale-factor            (fnk [consultant-start-date]
                                            (goal-start-date-scaled-percentage consultant-start-date))
   :billable-days-goal-scaled             (fnk [number-parental-leave-days work-duration-scale-factor]
                                            (- (* standard-billable-days-goal work-duration-scale-factor)
                                               number-parental-leave-days))
   :days-to-reach-goal                    (fnk [billable-days-goal-scaled hours-billed]
                                            (- billable-days-goal-scaled
                                               (/ hours-billed 8)))
   ;; TODO implement vacation reduction when first of month is booked on parental leave ticket while last day of previous month is not
   :vacation-per-year-scaled              (fnk [work-duration-scale-factor]
                                            (* vacation-per-year work-duration-scale-factor))
   :number-taken-vacation-days            (fnk [state]
                                            (/ (sum-past-vacation-hours state (:today state)) 8.))
   :number-planned-vacation-days          (fnk [state]
                                            (/ (sum-planned-vacation-hours state (:today state)) 8.))
   :number-holidays-remaining             (fnk [number-taken-vacation-days number-planned-vacation-days vacation-per-year-scaled]
                                            (- vacation-per-year-scaled number-taken-vacation-days number-planned-vacation-days))
   :workdays-left-except-today            (fnk [state]
                                            (working-days-left-without-today (:today state)))
   :workdays-left-actually                (fnk [number-holidays-remaining workdays-left-except-today number-planned-vacation-days]
                                            (max 0 (- workdays-left-except-today number-holidays-remaining number-planned-vacation-days)))
   :number-workdays-till-today            (fnk [consultant-start-date state]
                                            (count (days/workdays-between consultant-start-date (:today state))))

   :workdays-total                        (fnk [consultant-start-date vacation-per-year-scaled number-parental-leave-days]
                                            (- (count (days/workdays-till-end-of-year consultant-start-date))
                                               vacation-per-year-scaled
                                               number-parental-leave-days))
   :burndown-hours-per-workday            (fnk [billable-days-goal-scaled workdays-total]
                                            (/ (* billable-days-goal-scaled 8)
                                               workdays-total))
   :hour-goal-today                       (fnk [number-workdays-till-today
                                                number-taken-vacation-days
                                                number-parental-leave-days-till-today
                                                burndown-hours-per-workday]
                                            (* (- number-workdays-till-today
                                                  number-taken-vacation-days
                                                  number-parental-leave-days-till-today)
                                               burndown-hours-per-workday))
   :days-without-booked-hours             (fnk [state current-period-start-date]
                                            (unbooked-days state current-period-start-date))
   :number-sick-leave-days                (fnk [state]
                                            (/ (sick-leave-hours state) 8))
   })

(def app-model (graph/compile-cancelling app-model-graph))