(ns app.donut-service
  (:require [cljs.pprint :as pprint]))

(enable-console-print!)

(def sample-data (clj->js
            [{:age "16", :population 2704659},
             {:age "18", :population 4499890},
             {:age "25", :population 1499890},
             {:age "32", :population 500000}]))

(def max-value 1440)

(def two-pi (* js/Math.PI 2))

(def balance-cap 40)
(def balance-lower-bound (- balance-cap))
(def balance-upper-bound balance-cap)

(def transition-duration 2000)

(defn append-line [svg line-class line-color width half-height x-line-padding]
  (-> svg
      (.append "line")
      (.classed line-class true)
      (.attr "x1" x-line-padding)
      (.attr "y1" half-height)
      (.attr "x2" (- width x-line-padding))
      (.attr "y2" half-height)
      (.attr "stroke-width" 2)
      (.attr "stroke-dasharray" #js [6 6])
      (.attr "stroke" line-color)))

(defn append-right-side-text [svg text-class line-color x]
  (-> svg
      (.append "text")
      (.classed text-class true)
      (.style "fill" line-color)
      (.attr "x" (+ 10 x))
      (.style "text-anchor" "start")
      (.attr "dy" ".3em")))

(defn append-left-side-text [svg text-class line-color x]
  (-> svg
      (.append "text")
      (.classed text-class true)
      (.style "fill" line-color)
      (.style "text-anchor" "end")
      (.attr "x" (- x 10))
      (.attr "dy" ".3em")))

(defn append-label [svg label-class line-color label-text width]
  (-> svg
      (.append "text")
      (.classed label-class true)
      (.attr "x" (/ width 2))
      (.style "fill" line-color)
      (.style "text-anchor" "middle")
      (.text label-text)))

(defn create-balance-view [width height]
  (let [x-padding (* width 0.2)
        x-line-padding (* width 0.15)
        balance-width (- width (* 2 x-padding))
        balance-line-color "blue"
        goal-line-color "grey"
        svg (-> js/d3
                (.select "#current-stats svg")
                (.attr "width" width)
                (.attr "height" height)
                (.attr "viewBox" (str "0 0 " width " " height))
                (.attr "preserveAspectRatio" "xMidYMid meet")
                (.append "g"))]
    (-> svg
        (.append "rect")
        (.classed "balance-area" true)
        (.attr "x" x-padding)
        (.attr "y" (/ height 2))
        (.attr "height" 0)
        (.attr "width" balance-width))

    (append-label svg "actual-hours-label" balance-line-color "Your booked time" width)
    (append-line svg
                 "goal-line"
                 goal-line-color
                 width
                 (/ height 2)
                 x-line-padding)

    ;; goal stats
    (append-right-side-text svg "goal-percent-text" goal-line-color (- width x-line-padding))
    (append-left-side-text svg "goal-hours-text" goal-line-color x-line-padding)
    (append-label svg "goal-label" goal-line-color "Today's goal" width)

    ;; balance stats
    (append-line svg "actual-hours-line" balance-line-color width (/ height 2) x-line-padding)
    (append-right-side-text svg "actual-hours-percent-text" balance-line-color (- width x-line-padding))
    (append-left-side-text svg "actual-hours-hours-text" balance-line-color x-line-padding)
    (-> svg
        (.append "text")
        (.classed "balance-in-days-text" true)
        (.style "fill" "white")
        (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")"))
        (.attr "dy" ".15em")
        (.style "text-anchor" "middle")
        (.text "?"))))

(defn format-percent [percent]
  (pprint/cl-format nil
                    "~,1f%"
                    percent))

(defn update-line-y [line y]
  (-> line
      (.attr "y1" y)
      (.attr "y2" y)))

(defn update-percent-and-y [text y percentage]
  (-> text
      (.attr "y" y)
      (.text (pprint/cl-format nil
                               "~,1f%"
                               percentage))))

(defn update-hours-and-y [text y hours]
  (-> text
      (.attr "y" y)
      (.text (str (js/Math.round hours) "h"))))

(def ^:const empty-hours "+- \u2205")

(defn format-days-and-hours [hours]
  (let [days? (pos? (js/Math.abs (js/Math.floor (/ hours 8))))]
    (str (cond (= 0 hours) empty-hours
               (pos? hours) "+"
               :else "-")
         (when days?
           (str "" (js/Math.abs ((if (pos? hours)
                                   js/Math.floor
                                   js/Math.ceil) (/ hours 8))) "d"))
         (when (not= 0 (js/Math.round (rem hours 8)))
           (str (when days? " ") (js/Math.round (js/Math.abs (rem hours 8))) "h")))))

(def days-and-hours-regex #"([+-])(?:(\d+)d)? ?(?:(\d+)h)?")

(def sign-fns {"-" -
               "+" +})

(defn to-int-or-zero [x]
  (let [i (js/parseInt x)]
    (if (js/isNaN i)
      0
      i)))

(defn parse-days-and-hours [xdyh-string]
  (if (= empty-hours xdyh-string)
    0
    (let [[_ sign days-string hours-string] (re-find days-and-hours-regex xdyh-string)
          days (to-int-or-zero days-string)
          hours (to-int-or-zero hours-string)
          sign-fn (get sign-fns sign)]
      (sign-fn (+ (* days 8) hours)))))

(defn update-percent-and-y-transitioned
  [d3-percent-text center-y new-y start-percentage new-percentage end-duration start-fraction]
  (let [center-val (* (- new-percentage start-percentage)
                      start-fraction)]
    (-> d3-percent-text
        (.attr "y" center-y)
        (.tween "text"
                (fn [data index]
                  (let [interpolator (.interpolate js/d3 start-percentage
                                                   center-val)]
                    (fn [fraction]
                      (this-as dom-element
                        (set! (.-textContent dom-element)
                              (format-percent (interpolator fraction))))))))
        (.transition)
        (.ease "cubic-out")
        (.duration end-duration)
        (.attr "y" new-y)
        (.tween "text" (fn [data index]
                         (let [interpolator (.interpolate js/d3 center-val
                                                          new-percentage)]
                           (fn [fraction]
                             (this-as dom-element
                               (set! (.-textContent dom-element)
                                     (format-percent (interpolator fraction)))))))))))

(defn tween-text-round [start-val end-val format-fn]
  (fn [data index]
    (let [interpolator (.interpolateRound js/d3 start-val end-val)]
      (fn [fraction]
        (this-as dom-element
          (set! (.-textContent dom-element)
                (format-fn (interpolator fraction))))))))

(defn update-hours-and-y-transitioned
  [d3-hours-text center-y new-y start-hours new-hours end-duration start-fraction]
  (let [center-val (* (- new-hours start-hours)
                      start-fraction)
        hour-format-fn #(str % "h")]
    (-> d3-hours-text
        (.attr "y" center-y)
        (.tween "text"
                (tween-text-round start-hours center-val hour-format-fn))
        (.transition)
        (.ease "cubic-out")
        (.duration end-duration)
        (.attr "y" new-y)
        (.tween "text" (tween-text-round center-val new-hours hour-format-fn)))))


(defn update-line-y-transitioned [d3-line center-y new-y end-duration]
  (-> d3-line
      (.attr "y1" center-y)
      (.attr "y2" center-y)
      (.transition)
      (.duration end-duration)
      (.ease "cubic-out")
      (.attr "y1" new-y)
      (.attr "y2" new-y)))

(defn update-y [d3-element y]
  (.attr d3-element "y" y))

(defn update-area [d3-area y height color-interpolator]
  (-> d3-area
      (.attr "y" y)
      (.attr "height" height)
      (.style "fill" color-interpolator)))

(defn update-y-transitioned [d3-element center-y new-y end-duration]
  (-> d3-element
      (.attr "y" center-y)
      (.transition)
      (.ease "cubic-out")
      (.duration end-duration)
      (.attr "y" new-y)))

(defn update-balance-view-transitioned [todays-target-hours actual-hours-today]
  (let [svg (-> js/d3
                (.select "#current-stats svg"))
        height (.attr svg "height")
        actual-percentage (* 100 (/ actual-hours-today max-value))
        balance-hours (- actual-hours-today todays-target-hours)
        balance-height (-> (js/Math.abs balance-hours) (* 4) (min 250))
        balance-y-upper (- (/ height 2) (/ balance-height 2))
        balance-y-lower (+ balance-y-upper balance-height)
        [actual-hours-y goal-y actual-hours-label-offset-fn goal-label-offset-fn]
        (if (pos? balance-hours)
          [balance-y-upper balance-y-lower #(- % 15) (partial + 25)]
          [balance-y-lower balance-y-upper (partial + 25) #(- % 15)])
        rect-color-interpolator (-> js/d3
                                    (.interpolateLab "#FFBC25" "green"))
        normalized-balance-ratio (-> balance-hours
                                     (min balance-upper-bound)
                                     (max balance-lower-bound)
                                     (+ balance-cap)
                                     (/ (* 2 balance-cap)))


        start-y (.attr (.select js/d3 "#current-stats .balance-area") "y")
        center-y (/ height 2)
        center-dist-start (js/Math.abs (- center-y start-y))
        center-dist-end (js/Math.abs (- center-y balance-y-upper))
        total-dist (+ center-dist-start center-dist-end)
        start-fraction (/ center-dist-start total-dist)
        end-fraction (/ center-dist-end total-dist)
        start-duration (* start-fraction transition-duration)
        end-duration (* end-fraction transition-duration)

        d3-balance-in-days-text (-> js/d3 (.select "#current-stats .balance-in-days-text"))
        current-balance-text (.text d3-balance-in-days-text)

        transition1 (-> svg
                        (.transition)
                        (.ease "cubic-in")
                        (.duration start-duration))
        d3-balance-area (.select transition1 "#current-stats .balance-area")
        d3-actual-hours-line (.select transition1 "#current-stats .actual-hours-line")
        d3-actual-hours-hours-text (.select transition1 "#current-stats .actual-hours-hours-text")
        d3-actual-hours-percent-text (.select transition1 "#current-stats .actual-hours-percent-text")
        d3-actual-hours-label (.select transition1 "#current-stats .actual-hours-label")
        d3-goal-line (.select transition1 "#current-stats .goal-line")
        d3-goal-hours-text (.select transition1 "#current-stats .goal-hours-text")
        d3-goal-percent-text (.select transition1 "#current-stats .goal-percent-text")
        d3-goal-label (.select transition1 "#current-stats .goal-label")]
    (-> d3-balance-area
        (.attr "y" center-y)
        (.attr "height" 0)
        (.transition)
        (.duration end-duration)
        (.ease "cubic-out")
        (.attr "y" balance-y-upper)
        (.attr "height" balance-height)
        (.styleTween "fill" (fn [_ _ current-color]
                              (-> js/d3
                                  (.interpolateLab current-color (rect-color-interpolator normalized-balance-ratio))))))

    (update-line-y-transitioned d3-goal-line center-y goal-y end-duration)
    (update-line-y-transitioned d3-actual-hours-line center-y actual-hours-y end-duration)
    (update-hours-and-y-transitioned d3-actual-hours-hours-text
                                     center-y
                                     actual-hours-y
                                     0
                                     actual-hours-today
                                     end-duration
                                     start-fraction)
    (update-percent-and-y-transitioned d3-actual-hours-percent-text
                                       center-y
                                       actual-hours-y
                                       0
                                       actual-percentage
                                       end-duration
                                       start-fraction)
    (update-y-transitioned
      d3-goal-percent-text center-y goal-y end-duration)
    (update-y-transitioned
      d3-goal-hours-text center-y goal-y end-duration)
    (update-y-transitioned
      d3-actual-hours-label center-y (actual-hours-label-offset-fn actual-hours-y) end-duration)
    (update-y-transitioned
      d3-goal-label center-y (goal-label-offset-fn goal-y) end-duration)
    (-> d3-balance-in-days-text
        (.transition )
        (.duration transition-duration)
        (.tween "text" (tween-text-round
                         (parse-days-and-hours current-balance-text)
                         balance-hours
                         format-days-and-hours)))))

(defn update-balance-view [height todays-target-hours actual-hours-today]
  (let [target-percentage (* 100 (/ todays-target-hours max-value))
        actual-percentage (* 100 (/ actual-hours-today max-value))
        balance-hours (- actual-hours-today todays-target-hours)
        balance-height (-> (js/Math.abs balance-hours) (* 4) (min 250))
        balance-y-upper (- (/ height 2) (/ balance-height 2))
        balance-y-lower (+ balance-y-upper balance-height)
        [actual-hours-y goal-y actual-hours-label-offset-fn goal-label-offset-fn]
        (if (pos? balance-hours)
          [balance-y-upper balance-y-lower #(- % 15) (partial + 25)]
          [balance-y-lower balance-y-upper (partial + 25) #(- % 15)])
        rect-color-interpolator (-> js/d3
                                    (.interpolateLab "#FFBC25" "green"))
        normalized-balance-ratio (-> balance-hours
                                     (min balance-upper-bound)
                                     (max balance-lower-bound)
                                     (+ balance-cap)
                                     (/ (* 2 balance-cap)))
        d3-balance-area (.select js/d3 "#current-stats .balance-area")
        d3-actual-hours-line (.select js/d3 "#current-stats .actual-hours-line")
        d3-actual-hours-hours-text (.select js/d3 "#current-stats .actual-hours-hours-text")
        d3-actual-hours-percent-text (.select js/d3 "#current-stats .actual-hours-percent-text")
        d3-actual-hours-label (.select js/d3 "#current-stats .actual-hours-label")
        d3-goal-line (.select js/d3 "#current-stats .goal-line")
        d3-goal-hours-text (.select js/d3 "#current-stats .goal-hours-text")
        d3-goal-percent-text (.select js/d3 "#current-stats .goal-percent-text")
        d3-goal-label (.select js/d3 "#current-stats .goal-label")
        d3-balance-in-days-text (.select js/d3 "#current-stats .balance-in-days-text")]
    (update-area
      d3-balance-area balance-y-upper balance-height (rect-color-interpolator normalized-balance-ratio))
    (update-line-y d3-actual-hours-line actual-hours-y)
    (update-line-y d3-goal-line goal-y)
    (update-percent-and-y
      d3-actual-hours-percent-text actual-hours-y actual-percentage)
    (update-percent-and-y
      d3-goal-percent-text goal-y target-percentage)
    (update-hours-and-y d3-actual-hours-hours-text actual-hours-y actual-hours-today)
    (update-hours-and-y d3-goal-hours-text goal-y todays-target-hours)
    (update-y d3-actual-hours-label (actual-hours-label-offset-fn actual-hours-y))
    (update-y d3-goal-label (goal-label-offset-fn goal-y))
    (-> d3-balance-in-days-text
        (.text (format-days-and-hours balance-hours)))))

(defn balance-view [viewport-size todays-target-hours actual-hours-today]
  (let [{total-width :width} viewport-size
        balance-view-width (min (* 0.85 total-width) 500)
        balance-view-height (* 0.6 balance-view-width)]
    (create-balance-view balance-view-width balance-view-height)
    (update-balance-view balance-view-height todays-target-hours actual-hours-today)))

(defn progress [todays-target-hours actual-hours-today]
  (let [width 250
        height 250
        radius 120
        target-percentage (/ todays-target-hours max-value)
        actual-percentage (/ actual-hours-today max-value)
        target-angle (* two-pi target-percentage)
        actual-angle (* two-pi actual-percentage)
        arc-data {:startAngle 0
                  :endAngle target-angle}
        target-arc (-> js/d3
                       (.-svg)
                       (.arc)
                       (.startAngle 0)
                       (.endAngle target-angle)
                       (.outerRadius (- radius 40))
                       (.innerRadius (- radius 80)))
        actual-arc (-> js/d3
                       (.-svg)
                       (.arc)
                       (.startAngle 0)
                       (.endAngle actual-angle)
                       (.outerRadius radius)
                       (.innerRadius (- radius 40)))
        svg (-> js/d3
                (.select "#current-stats svg")
                (.attr "width" width)
                (.attr "height" height)
                ;(.append "g")
                ;(.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")"))
                )]
    (-> svg
        (.append "path")
        (.attr "d" target-arc)
        ;        (.attr "class" "arc")
        (.style "fill" "blue")
        (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")")))
    (-> svg
        (.append "path")
        (.attr "d" actual-arc)
        ;        (.attr "class" "arc")
        (.style "fill" "grey")
        (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")")))))

(defn create-donut [colors billed-hours]
  (let [width 250
        height 250
        radius (/ (min width height) 2)
        ratio (/ billed-hours max-value)
        half-pi 0
        end-angle (* (min (* 360 ratio) 360) half-pi)
        color (-> js/d3
                  (.-scale)
                  (.ordinal)
                  (.range colors)
                  )
        arc (-> js/d3
                (.-svg)
                (.arc)
                (.outerRadius radius)
                (.innerRadius (- radius 80)))
        pie
        (-> js/d3
            (.-layout)
            (.pie)
            (.value (fn [d] (.-population d))))
        svg (-> js/d3
                (.select "#current-stats svg")
                ;(.append "svg")
                (.attr "width" width)
                (.attr "height" height)
                (.append "g")
                (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")")))]
    (let [g (-> svg
                (.selectAll ".arc")
                                (.data (pie sample-data))

                (.enter)
                (.append "g")
                (.attr "class" "arc"))]
      (-> g
          (.append "path")
          (.attr "d" arc)
          (.style "fill" (fn [d] (color (-> d (.-data) (.-age)))))
          ;(.datum end-angle)
          )
      (-> g
          (.append "text")
          (.style "fill" "white")
          (.style "font-family" "arial")
          (.attr "transform" (fn [d] (str "translate(" (-> arc (.centroid d)) ")")))
          (.attr "dy" ".15em")
          (.style "text-anchor" "middle")
          (.text (fn [d] (-> d (.-data) (.-age))))))))