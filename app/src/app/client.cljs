(ns ^:figwheel-always app.client
  (:require
              [reagent.core :as reagent]
              [reagent.interop :refer-macros [.']]
              [app.domain :as domain]
              [bidi.bidi :as bidi]
              [goog.events :as events]
              [app.gsignin :as gs]
              [app.donut-service :as donut-service]
              [cljs-time.core :as time]
              [material-ui.core :as ui :include-macros true]
              [goog.history.EventType :as EventType])
  (:import [goog.history Html5History EventType]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(donut-service/create-donut "data.json" #js["red" "green" "blue", "grey"])

;; define your app data so that it doesn't get over-written on reload

(defn working-days-left [app-state]
  (str (count (:days/workdays-till-end-of-year app-state))))

(defn days-needed-to-reach-goal [app-state]
  (str (- domain/billable-days-goal
          (/ (reduce + (map :worklog/hours (:worklogs app-state))) 8))))

(defn plus-one-days-left [remaining-working-days days-to-100-percent]
  (max 0 (- remaining-working-days days-to-100-percent)))

(defn latest-worklog-work-date [state]
  (if-let [worklogs (seq (:worklogs state))]
    (apply max-key #(.getTime %) (map :worklog/work-date worklogs))
    "?"))

(defn profile-page [_]
  (let [state @domain/app-state
        remaining-working-days (working-days-left state)
        days-to-100-percent (days-needed-to-reach-goal state)]
    (println "rerender" (:user state))
    [:div {:style {:margin-left "auto"
                   :margin-right "auto"
                   :width 700}}
     [:h1 (str (:user/first-name (:user state))
               " "
               (:user/last-name (:user state)))]
     [:pre (str state)]
     [ui/GridList {:cols       3
                   :cellHeight 130
                   :padding    1
                   :style {:width 700}}
      [ui/GridTile {:title           "workdays left"
                    ;:style           {:color "black"}
                    ;:titlePosition "top"
                    :titleBackground (.' js/MaterialUI :Styles.Colors.cyan600)
                    }
       [ui/Avatar {:size            80
                   :backgroundColor (.' js/MaterialUI :Styles.Colors.blueA400)}
        remaining-working-days]]
      [ui/GridTile {:title "days needed to reach 100%"
                    ;:titlePosition "top"
                    :titleBackground (.' js/MaterialUI :Styles.Colors.cyan600)
                    }
       [ui/Avatar {:size 80
                   :backgroundColor (.' js/MaterialUI :Styles.Colors.blueA700)}
        days-to-100-percent]]
      [ui/GridTile {:title "+1 days left"
                    ;:titlePosition "top"
                    :titleBackground (.' js/MaterialUI :Styles.Colors.cyan600)
                    }
       [ui/Avatar {:size 80
                   :backgroundColor (.' js/MaterialUI :Styles.Colors.blueA400)}
        (plus-one-days-left remaining-working-days days-to-100-percent)]]]
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
  [ui/AppCanvas
   [ui/AppBar {:class                    "mui-dark-theme"
               :title                    "LUBTFY"
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

(defn start []
  (reagent/render-component [page]
                            (.getElementById js/document "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)

)

