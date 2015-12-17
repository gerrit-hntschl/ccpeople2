(ns ^:figwheel-always app.client
  (:require
    [reagent.core :as reagent]
    [reagent.interop :refer-macros [.']]
    [app.domain :as domain]
    [bidi.bidi :as bidi]
    [goog.events :as events]
    [app.gsignin :as gs]
    [app.donut-service :as donut-service]
    [material-ui.core :as ui :include-macros true]
    [goog.history.EventType :as EventType]
    [app.days :as days]
    [cljs.pprint :as pprint])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

;(donut-service/create-donut "data.json" #js["red" "green" "blue", "grey"])

;; define your app data so that it doesn't get over-written on reload

(defn latest-worklog-work-date [state]
  (if-let [worklogs (seq (:worklogs state))]
    (days/format-simple-date (apply max-key #(.getTime %) (map :worklog/work-date worklogs)))
    "?"))

(def colors {:primary1Color      (.' js/MaterialUI :Styles.Colors.cyan300)
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
  [:p {:style {:font-size 60
                  :margin-top "auto"
                  :margin-bottom "auto"}}
   text])


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
    (println "rerender" (:user state))
    [:div {:style {:margin-left "auto"
                   :margin-right "auto"
                   :width 700}}
     [:h2 {:style {:color (:alternateTextColor colors)}} "This Month"]
;     [:pre (str state)]
     [ui/GridList {:cols       3
                   :cellHeight 130
                   :padding    1
                   :style {:width 700}}
      [ui/GridTile {:title "today"}
       [ui/Paper {:zDepth 2
                  :style  {:height          80
                           :backgroundColor (:primary3Color colors)
                           :color           (:accent1Color colors)}}
        (metric-style today-str)]]
      [ui/GridTile {:title "percent of goal"}
       [ui/Paper {:zDepth 2
                  :style  {:height          80
                           :backgroundColor (:primary3Color colors)
                           :color           (:accent1Color colors)}}
        (metric-style billed-days)]]
      [ui/GridTile {:title "days balance"}
       [ui/Paper {:zDepth 2
                  :style {:height 80
                          :backgroundColor (:primary3Color colors)
                          :color (:accent1Color colors)}}
        (metric-style balance)]]
      [ui/GridTile {:title "days w/o booked hours"}
       [ui/Paper {:zDepth 2
                  :style  {:height          80
                           :backgroundColor (:primary3Color colors)
                           :color           (:accent1Color colors)}}
        (metric-style unbooked-days-count)]]
      [ui/GridTile {:title "your workdays left"}
       [ui/Paper {:zDepth 2
                  :style  {:height          80
                           :backgroundColor (:primary3Color colors)
                           :color           (:accent1Color colors)}}
        (metric-style remaining-work-days-minus-vacation)]]
      [ui/GridTile {:title "days needed to reach 100%"}
       [ui/Paper {:zDepth 2
                  :style {:height 80
                          :backgroundColor (:primary3Color colors)
                          :color (:accent1Color colors)}}
        (metric-style days-to-100-percent)]]
      [ui/GridTile {:title "remaining leave"}
       [ui/Paper {:zDepth 2
                  :style  {:height          80
                           :backgroundColor (:primary3Color colors)
                           :color           (:accent1Color colors)}}
        (metric-style rem-holidays)]]
      [ui/GridTile {:title "sick leave"}
       [ui/Paper {:zDepth 2
                  :style  {:height          80
                           :backgroundColor (:primary3Color colors)
                           :color           (:accent1Color colors)}}
        (metric-style num-sick-leave-days)]]]
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

(def theme
  #js {:fontFamily "Roboto, sans-serif"
       :spacing    (.' js/MaterialUI :Styles.Spacing)
       :palette    (clj->js colors)})

(def ^:dynamic *mui-theme*
  (.getMuiTheme (.' js/MaterialUI :Styles.ThemeManager) theme))

(defn page []
  [ui/AppCanvas
   [ui/AppBar {:title                    "ccHours"
               :zDepth                   0
               :onMenuIconButtonTouchTap (fn []
                                           (println "touchtap")

                                           )
               :iconElementRight         (reagent/as-element [gs/signed-in-component])
               }
    [:div.action-icons
     [ui/IconButton {:iconClassName "mdfi_navigation_more_vert"}]
     [ui/IconButton {:iconClassName "mdfi_action_favorite_outline"}]
     [ui/IconButton {:iconClassName "mdfi_action_search"}]]
    ]
   [:div {:style {:padding-top (.' js/MaterialUI :Styles.Spacing.desktopKeylineIncrement)
                  :text-align  "center"}}
    [gs/sign-in-component]
    [dispatcher]]])

(defn main-panel []
  (reagent/create-class
    {:display-name "Root Panel"

     :child-context-types
                   #js {:muiTheme js/React.PropTypes.object}

     :get-child-context
                   (fn [this]
                     #js {:muiTheme *mui-theme*})
     :reagent-render
                   page}))


(defn start []
  (reagent/render-component [main-panel]
                            (.getElementById js/document "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)

)

