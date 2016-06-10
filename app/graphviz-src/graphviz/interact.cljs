(ns graphviz.interact
  (:import [goog.dom query])
  (:require
    [goog.dom :as gdom]
    [goog.dom.classlist :as classes]
    [goog.events :as gevents]
    [clojure.set :as set]))


(enable-console-print!)

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))


(def graph-desc-atom (atom nil))
(def selected-node (atom nil))

(defn move-in-eles-close [center-x center-y in-eles]
  (let [no-eles (count in-eles)
        ele-height 120
        half-eles (int (/ no-eles 2))
        offsets (if (even? no-eles)
                  (range (+ (/ ele-height 2) (- (* half-eles ele-height)))
                         (- (* (inc half-eles) ele-height) (/ ele-height 2))
                         ele-height)
                  (range (- (* half-eles ele-height))
                         (* (inc half-eles) ele-height)
                         ele-height))
        heights (into [] (map (partial + center-y)) offsets)]
    (doseq [[ele new-height] (map vector in-eles heights)]
      (.setAttribute ele "transform" (str "translate(" (- center-x 450) "," new-height ")"))
      (classes/add ele "highlighted-node"))
    heights))

(defn arrow-ele-ids [dest-id in-ele-ids]
  (println "ineleids" in-ele-ids)
  (map (fn [src-id]
         (str src-id "-" dest-id))
       in-ele-ids))

(defn get-eles [ele-ids]
  (println "eleids" ele-ids)
  (map (fn [id] (gdom/getElement id))
       ele-ids))

(defn move-arrows [dest-x heights arrow-eles]
  (println "idheigh" heights)
  (doseq [[ele height] (map vector arrow-eles heights)]
    (doto (gdom/getFirstElementChild ele)
      (.setAttribute "x1" (- dest-x 100))
      (.setAttribute "y1" (+ 30 height)))))

(defn float-attr [ele attr-name]
  (js/parseFloat (.getAttribute ele attr-name)))

(add-watch selected-node :selected-node-focus
           (fn [_ _ prev-selected new-selected]
             (when prev-selected
               (classes/remove prev-selected "selected-node")
               (classes/remove prev-selected "highlighted-node")
               (let [in-ele-ids (get-in @graph-desc-atom [(.-id prev-selected) "in"])]
                 (doseq [ele (get-eles in-ele-ids)]
                   (let [orig-x (float-attr ele "data-x")
                         orig-y (float-attr ele "data-y")]
                     (.setAttribute ele "transform" (str "translate(" orig-x "," orig-y ")"))))))
             (if new-selected
               (do                                          ;(classes/add (.-body js/document) "background")
                   (classes/add new-selected "selected-node")
                   (classes/add new-selected "highlighted-node")
                   (let [in-ele-ids (get-in @graph-desc-atom [(.-id new-selected) "in"])
                         eles (get-eles in-ele-ids)
                         sorted-eles (sort-by (fn [ele] (float-attr ele "data-y")) eles)
                         highlighted-gs (into #{new-selected} eles)
                         all-gs (into #{} (query "#graph0 g"))
                         other-gs (set/difference
                                    all-gs
                                    highlighted-gs)
                         dest-x (float-attr new-selected "data-x")
                         dest-y (float-attr new-selected "data-y")
                         heights (move-in-eles-close
                                   dest-x
                                   dest-y
                                   sorted-eles)]
                     (doseq [other-g other-gs]
                       (classes/add other-g "background"))
                     (doseq [g highlighted-gs]
                       (classes/remove g "background"))
                     #_(move-arrows dest-x heights (get-eles (arrow-ele-ids (.-id new-selected)
                                                                            in-ele-ids)))))
               (doseq [g (query "#graph0 g")]
                 (classes/remove g "background"))
               )))

(defn add-click-listener [ele callback]
  (gevents/listen
    ele
    goog.events.EventType.CLICK
    callback))

(defn ^:export start [graph-desc]
  (reset! graph-desc-atom (js->clj graph-desc))
  (gevents/listen
    (.-body js/document)
    goog.events.EventType.CLICK
    (fn [evt]
      (println "body click")
      (reset! selected-node nil))
    false)
  (doseq [node-ele (seq (query ".gnode"))]
    (add-click-listener node-ele
                        (fn [evt]
                          (println "clicked" (-> evt .-currentTarget (.-id)))
                          (reset! selected-node (-> evt .-currentTarget))
                          (.preventDefault evt)
                          (.stopPropagation evt)))))

