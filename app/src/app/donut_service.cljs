(ns app.donut-service
  (:require [cljs.pprint :as pprint]))

(enable-console-print!)

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
      ; (.attr "stroke-dasharray" #js [6 6])
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

(defn create-monthly-bar-view [component-name width height billed-hours-by-month]

  (let [x-padding (* width 0.26)
        x-line-padding (* width 0.21)
        balance-width (- width (* 2 10))
        label-color "black"
        dataset (clj->js '(100 200 300))
        balance-line-color "#e9e8e8"
        svg (-> js/d3
                (.select (str "#" component-name " svg"))
                (.attr "width" width)
                (.attr "height" height)
                (.attr "viewBox" (str "0 0 " width " " height))
                (.attr "preserveAspectRatio" "xMidYMid meet")
               )]

    (-> svg
        (.append "rect")
        (.attr "x" x-padding)
        (.attr "width" 550)
        (.attr "y" 0)
        (.attr "height" height)
        (.style "fill" "#f3f3f3")
        )

    (-> svg
        (.selectAll "rect.month")
        (.data billed-hours-by-month)
        (.enter)
        (.append "rect")
        (.attr "x" (fn [d,i] (+ (* i 40) 350)))
        (.attr "y" (fn [d] (- height d)))
        (.attr "width" 38)
        (.attr "height" (fn [d] d))
        (.style "fill" (fn [d] (str "rgb(0,0," (int (Math/ceil d)) ")")))
        )

    (-> svg (.selectAll "text")
        (.data billed-hours-by-month)
        (.enter)
        (.append "text")
        (.text (fn [d] (int (Math/ceil d))))
        (.attr "x" (fn [d,i] (+ 355 (* (/ (if (> d 99) 480 494) 12) i))))
        (.attr "y" (fn [d] (+ (if (> d 70) 50 (if(> d 20) 20 10) ) (- height d))))
        (.style "fill" "#ffffff")
        )

    )
  )

(defn create-balance-view [component-name width height]
  (let [x-padding (* width 0.24)
        x-line-padding (* width 0.21)
        balance-width (- width (* 2 x-padding))
        label-color "black"
        balance-line-color "#e9e8e8"
        svg (-> js/d3
                (.select (str "#" component-name " svg"))
                (.attr "width" width)
                (.attr "height" height)
                (.attr "viewBox" (str "0 0 " width " " height))
                (.attr "preserveAspectRatio" "xMidYMid meet")
                (.append "g"))]
    (-> svg
        (.append "rect")
        (.attr "x" x-padding)
        (.attr "width" balance-width)
        (.attr "y" 0)
        (.attr "height" height)
        (.style "fill" "#f3f3f3"))
    (-> svg
        (.append "rect")
        (.classed "balance-area" true)
        (.attr "x" x-padding)
        (.attr "y" (/ height 2))
        (.attr "height" 0)
        (.attr "width" balance-width))

    (append-label svg "actual-hours-label" label-color "Your billable hours" width)
    (append-line svg
                 "goal-line"
                 balance-line-color
                 width
                 (/ height 2)
                 x-line-padding)

    ;; goal stats
    (append-right-side-text svg "goal-percent-text" label-color (- width x-line-padding))
    (append-left-side-text svg "goal-hours-text" label-color x-line-padding)
    (append-label svg "goal-label" label-color "Today's goal" width)

    ;; balance stats
    (append-line svg "actual-hours-line" balance-line-color width (/ height 2) x-line-padding)
    (append-right-side-text svg "actual-hours-percent-text" label-color (- width x-line-padding))
    (append-left-side-text svg "actual-hours-hours-text" label-color x-line-padding)
    (-> svg
        (.append "text")
        (.classed "balance-in-days-text" true)
        (.style "fill" "white")
                (.style "text-shadow" "-1px 0 black, 0 1px black, 1px 0 black, 0 -1px black")
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

(defn update-balance-view-transitioned [component-name todays-target-hours actual-hours-today total-hours-goal]
  (let [svg (-> js/d3
                (.select (str "#" component-name " svg")))
        height (.attr svg "height")
        actual-percentage (* 100 (/ actual-hours-today total-hours-goal))
        goal-percentage (* 100 (/ todays-target-hours total-hours-goal))
        balance-hours (- actual-hours-today todays-target-hours)
        balance-height (-> (js/Math.abs balance-hours) (* 4) (min (* 0.7 height)))
        balance-y-upper (- (/ height 2) (/ balance-height 2))
        balance-y-lower (+ balance-y-upper balance-height)
        [actual-hours-y goal-y actual-hours-label-offset-fn goal-label-offset-fn]
        (if (pos? balance-hours)
          [balance-y-upper balance-y-lower #(- % 15) (partial + 25)]
          [balance-y-lower balance-y-upper (partial + 25) #(- % 15)])
        rect-color-interpolator (-> js/d3
                                    (.interpolateLab "#1FB7D4" "#7FFBC6"))
        normalized-balance-ratio (-> balance-hours
                                     (min balance-upper-bound)
                                     (max balance-lower-bound)
                                     (+ balance-cap)
                                     (/ (* 2 balance-cap)))

        component-id-selector (partial str "#" component-name " ")
        start-y (.attr (.select js/d3 (component-id-selector ".balance-area")) "y")
        center-y (/ height 2)
        center-dist-start (js/Math.abs (- center-y start-y))
        center-dist-end (js/Math.abs (- center-y balance-y-upper))
        total-dist (+ center-dist-start center-dist-end)
        start-fraction (/ center-dist-start total-dist)
        end-fraction (/ center-dist-end total-dist)
        start-duration (* start-fraction transition-duration)
        end-duration (* end-fraction transition-duration)

        d3-balance-in-days-text (.select js/d3 (component-id-selector ".balance-in-days-text"))
        current-balance-text (.text d3-balance-in-days-text)
        prev-goal-hours (-> js/d3
                            (.select (component-id-selector ".goal-hours-text"))
                            (.text)
                            (to-int-or-zero))
        prev-goal-percent (-> js/d3
                              (.select (component-id-selector ".goal-percent-text"))
                              (.text)
                              (to-int-or-zero))
        transition1 (-> svg
                        (.transition)
                        (.ease "cubic-in")
                        (.duration start-duration))
        d3-balance-area (.select transition1 (component-id-selector ".balance-area"))
        d3-actual-hours-line (.select transition1 (component-id-selector ".actual-hours-line"))
        d3-actual-hours-hours-text (.select transition1 (component-id-selector ".actual-hours-hours-text"))
        d3-actual-hours-percent-text (.select transition1 (component-id-selector ".actual-hours-percent-text"))
        d3-actual-hours-label (.select transition1 (component-id-selector ".actual-hours-label"))
        d3-goal-line (.select transition1 (component-id-selector ".goal-line"))
        d3-goal-hours-text (.select transition1 (component-id-selector ".goal-hours-text"))
        d3-goal-percent-text (.select transition1 (component-id-selector ".goal-percent-text"))
        d3-goal-label (.select transition1 (component-id-selector ".goal-label"))]
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
    (update-percent-and-y-transitioned d3-goal-percent-text
                                       center-y
                                       goal-y
                                       prev-goal-percent
                                       goal-percentage
                                       end-duration
                                       start-fraction)
    (update-hours-and-y-transitioned d3-goal-hours-text
                                     center-y
                                     goal-y
                                     prev-goal-hours
                                     todays-target-hours
                                     end-duration
                                     start-fraction)
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

(defn update-balance-view [component-name height todays-target-hours actual-hours-today total-hours-goal]
  (let [target-percentage (* 100 (/ todays-target-hours total-hours-goal))
        actual-percentage (* 100 (/ actual-hours-today total-hours-goal))
        balance-hours (- actual-hours-today todays-target-hours)
        balance-height (-> (js/Math.abs balance-hours) (* 4) (min (* 0.4 height)))
        balance-y-upper (- (/ height 2) (/ balance-height 2))
        balance-y-lower (+ balance-y-upper balance-height)
        [actual-hours-y goal-y actual-hours-label-offset-fn goal-label-offset-fn]
        (if (pos? balance-hours)
          [balance-y-upper balance-y-lower #(- % 15) (partial + 25)]
          [balance-y-lower bxalance-y-upper (partial + 25) #(- % 15)])
        rect-color-interpolator (-> js/d3
                                    (.interpolateLab "#1FB7D4" "#7FFBC6"))
        normalized-balance-ratio (-> balance-hours
                                     (min balance-upper-bound)
                                     (max balance-lower-bound)
                                     (+ balance-cap)
                                     (/ (* 2 balance-cap)))
        component-id-selector (str "#" component-name " ")
        d3-balance-area (.select js/d3 (str component-id-selector ".balance-area"))
        d3-actual-hours-line (.select js/d3 (str component-id-selector ".actual-hours-line"))
        d3-actual-hours-hours-text (.select js/d3 (str component-id-selector ".actual-hours-hours-text"))
        d3-actual-hours-percent-text (.select js/d3 (str component-id-selector ".actual-hours-percent-text"))
        d3-actual-hours-label (.select js/d3 (str component-id-selector ".actual-hours-label"))
        d3-goal-line (.select js/d3 (str component-id-selector ".goal-line"))
        d3-goal-hours-text (.select js/d3 (str component-id-selector ".goal-hours-text"))
        d3-goal-percent-text (.select js/d3 (str component-id-selector ".goal-percent-text"))
        d3-goal-label (.select js/d3 (str component-id-selector ".goal-label"))
        d3-balance-in-days-text (.select js/d3 (str component-id-selector ".balance-in-days-text"))]
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

(defn chart-view [component-name monthly-hours]
  (->> monthly-hours
       (into (sorted-map-by <))
       (vals)
       (clj->js)
       (create-monthly-bar-view component-name 1200 200))
  )

(defn balance-view [component-name viewport-size todays-target-hours actual-hours-today total-days-goal]
  (let [{total-width :width} viewport-size
        balance-view-width (min (* 0.95 total-width) 500)
        balance-view-height (* 0.6 balance-view-width)
        total-hours-goal (* 8 total-days-goal)]
    (create-balance-view component-name balance-view-width balance-view-height)
    (update-balance-view component-name balance-view-height todays-target-hours 0 total-hours-goal)
    (update-balance-view-transitioned component-name todays-target-hours actual-hours-today total-hours-goal)))

(defn progress [component-name current-value total-value format-fn]
  (let [width 180
        height 180
        radius 85
        current-percentage (- 1 (/ current-value total-value))
        target-angle (* two-pi current-percentage)
        component-id-selector (str "#" component-name " ")
        inner-radius (- radius 10)
        target-arc (-> js/d3
                       (.-svg)
                       (.arc)
                       (.startAngle 0)
                       (.endAngle target-angle)
                       (.outerRadius radius)
                       (.innerRadius inner-radius))
        svg (-> js/d3
                (.select (str component-id-selector "svg"))
                (.attr "width" width)
                (.attr "height" height))]
    (-> svg
        (.append "circle")
        (.style "fill" "#e9e8e8")
        (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")"))
        (.attr "r" radius))
    (-> svg
        (.append "circle")
        (.style "fill" "#a5e2ed")
        (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")"))
        (.attr "r" inner-radius))
    (-> svg
        (.append "text")
        (.attr "x" (/ width 2))
        (.attr "y" (/ height 2))
        (.style "fill" "#196674")
        (.style "text-anchor" "middle")
        (.style "alignment-baseline" "middle")
        (.style "font-size" "3.0em")
        (.text (format-fn current-value)))
    (-> svg
        (.append "path")
        (.attr "d" target-arc)
        (.style "fill" "#196674")
        (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")")))))

#_(defn create-donut [colors billed-hours]
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