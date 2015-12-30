(ns ^:figwheel-always app.client
  (:require
    [reagent.core :as reagent]
    [reagent.interop :refer-macros [.']]
    [app.domain :as domain]
    [bidi.bidi :as bidi]
    [goog.events :as events]
    [app.gsignin :as gs]
    cljsjs.react
    [app.donut-service :as donut-service]
    ;    [material-ui.core :as ui :include-macros true]
    [goog.history.EventType :as EventType]
    [app.days :as days]
    [cljs.pprint :as pprint])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defn latest-worklog-work-date [state]
  (if-let [worklogs (seq (:worklogs state))]
    (days/format-simple-date (apply max-key #(.getTime %) (map :worklog/work-date worklogs)))
    "?"))

#_(def colors {:primary1Color      (.' js/MaterialUI :Styles.Colors.cyan300)
             :primary2Color      (.' js/MaterialUI :Styles.Colors.cyan700),
             :primary3Color      (.' js/MaterialUI :Styles.Colors.cyan100),
             :accent1Color       (.' js/MaterialUI :Styles.Colors.limeA200),
             :accent2Color       (.' js/MaterialUI :Styles.Colors.pinkA100),
             :accent3Color       (.' js/MaterialUI :Styles.Colors.tealA400),
             :textColor          (.' js/MaterialUI :Styles.Colors.darkBlack),
             :alternateTextColor (.' js/MaterialUI :Styles.Colors.white),
             :canvasColor        (.' js/MaterialUI :Styles.Colors.cyan200),
             :borderColor        (.' js/MaterialUI :Styles.Colors.tealA700),
             :disabledColor      (.' js/MaterialUI :Styles.Colors.darkBlack)})

(defn metric-style [text]
  [:span {:style {:font-size 60}} text])

(defn current-stats-did-mount []
  #_(donut-service/progress 730 250)
  (let [state @domain/app-state
        actual-hours (domain/billed-hours state)
        todays-goal-hours (domain/todays-hour-goal state)]
    (donut-service/balance-view todays-goal-hours actual-hours))
  #_(donut-service/create-donut #js["blue" "green" "grey"] 144))

(defn current-stats-update [this old-argv]
  (let [state @domain/app-state
        actual-hours (domain/billed-hours state)
        todays-goal-hours (domain/todays-hour-goal state)]
    (donut-service/update-balance-view-transitioned todays-goal-hours actual-hours)))

(defn current-stats []
  ;; is it really necessary to deref app-state here just to trigger an invocation of component-did-update??
  (let [_ @domain/app-state]
    [:div#current-stats {:style {:width        "500"
                                 :height       "300"
                                 :margin-left  "auto"
                                 :margin-right "auto"}}
     [:svg]]))

(defn current-stats-component []
  (reagent/create-class {:reagent-render current-stats
                         :component-did-mount current-stats-did-mount
                         :component-did-update current-stats-update}))


(defn profile-page [_]
  (let [state @domain/app-state
        remaining-work-days-minus-vacation (str (domain/actual-work-days-left state))
        rem-holidays (str (domain/number-remaining-holidays state))
        days-to-100-percent (pprint/cl-format nil "~,2f" (domain/days-needed-to-reach-goal state))
        unbooked-days-count (str (count (domain/unbooked-days state)))
        billed-days (pprint/cl-format nil "~,1f%" (* 100 (/ (domain/billed-hours state) 8 domain/billable-days-goal)))
        balance (pprint/cl-format nil "~,2@f" (domain/days-balance state))
        num-sick-leave-days (str (domain/number-sick-leave-days state))
        today-str (days/month-day-today)]
    [:div {:style {:margin-left "auto"
                   :margin-right "auto"
                   :width 700}}
     [:h2 {:style {:color "white"}} "Now"]
;     [:pre (str state)]
     [current-stats-component]
     [:ul {:padding    1
           :style {:width 700}}
      [:li "today" (metric-style today-str)]
      [:li "percent of goal"
       (metric-style billed-days)]
      [:li "days balance"
       (metric-style balance)]
      [:li "days w/o booked hours"
       (metric-style unbooked-days-count)]
      [:li "your workdays left"
       (metric-style remaining-work-days-minus-vacation)]
      [:li "days needed to reach 100%"
       (metric-style days-to-100-percent)]
      [:li "remaining leave"
       (metric-style rem-holidays)]
      [:li "sick leave"
       (metric-style num-sick-leave-days)]]
     [:div (str "Latest workdate considered: " (latest-worklog-work-date state))]]))

(defn bye-world [_]
  [:h1 (:bye/text @domain/app-state)])

(defn tabs []
  [:div ""])


(def routes ["" {"profile" :profile
                 "people" :people}])

(defmulti handlers :handler :default :profile)

(defmethod handlers :people [] bye-world)

(defmethod handlers :profile [] profile-page)

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

(defn page []
  [:div
   [:h3 "ccHours"]
   [:div {:style {:text-align "center"}}
    [gs/sign-in-component]
    [dispatcher]]])


(defn start []
  (reagent/render-component [page]
                            (.getElementById js/document "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)

)

