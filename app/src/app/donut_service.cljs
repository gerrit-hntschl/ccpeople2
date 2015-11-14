(ns app.donut-service)

(enable-console-print!)

(defn create-donut[url colors]

  (def width 520)

  (def height 250)

  (def radius (/ (min width height) 2))

  (def color (-> js/d3
                 (.-scale)
                 (.ordinal)
                 (.range colors)
                 ))

  (def arc (-> js/d3
               (.-svg)
               (.arc)
               (.outerRadius (- radius 30))
               (.innerRadius (- radius 70))
               ))

  (def pie (-> js/d3
               (.-layout)
               (.pie)
               (.value (fn [d] (.-population d)))
               ))



  (def svg (-> js/d3
               (.select "body")
               (.append "svg")
               (.attr "width" width)
               (.attr "height" height)
               (.append "g")
               (.attr "transform" (str "translate(" (/ width 2) "," (/ height 2) ")"))
               )
    )

  (-> js/d3
      (.json url
             (fn [error data]
               ;;(println (pr-str data))


               (def g (-> svg
                          (.selectAll ".arc")
                          (.data (pie data))
                          (.enter)
                          (.append "g")
                          (.attr "class" "arc")
                          )
                 )

               (-> g
                   (.append "path")
                   (.attr "d" arc)
                   (.style "fill" (fn [d] (color (-> d (.-data) (.-age))))
                           )
                   )

               (-> g
                   (.append "text")
                   (.style "fill" "white")
                   (.style "font-family" "arial")
                   (.attr "transform" (fn [d] (str "translate(" (-> arc (.centroid d)) ")")))
                   (.attr "dy" ".15em")
                   (.style "text-anchor" "middle")
                   (.text (fn [d] (-> d (.-data) (.-age))))
                   )

               )
             )
      )
  )