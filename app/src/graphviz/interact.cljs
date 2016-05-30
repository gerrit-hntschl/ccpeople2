(ns graphviz.interact
  (:import [goog.dom query])
  (:require
    [goog.dom :as gdom]
    [goog.dom.classlist :as classes]
    [goog.events :as gevents]))

(enable-console-print!)

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))


(def graph-desc-atom (atom nil))
(def selected-node (atom nil))

(defn move-in-eles-close [center-x center-y in-eles]
  (let [no-eles (count in-eles)
        ele-height 100
        half-eles (int (/ no-eles 2))
        offsets (if (even? no-eles)
                  (range (+ 50 (- (* half-eles ele-height)))
                         (- (* (inc half-eles) ele-height) 50)
                         ele-height)
                  (range (- (* half-eles ele-height))
                         (* (inc half-eles) ele-height)
                         ele-height))
        heights (into [] (map (partial + center-y)) offsets)]
    (doseq [[ele new-height] (map vector in-eles heights)]
      (.setAttribute ele "transform" (str "translate(" (- center-x 450) "," new-height ")")))
    (into {} (map (fn [ele height] [(.-id ele) height]) in-eles heights))))

(defn arrow-ele-ids [dest-id in-ele-ids]
  (map (fn [src-id]
         (str src-id "-" dest-id))
       in-ele-ids))

(defn get-eles [ele-ids]
  (map (fn [id] (gdom/getElement id))
       ele-ids))

(defn move-arrows [dest-x id->height arrow-eles]
  (doseq [ele arrow-eles]
    (.setAttribute ele "x1" (+ dest-x 350))
    (.setAttribute ele "y1" )))

(add-watch selected-node :selected-node-focus
           (fn [_ _ prev-selected new-selected]
             (when prev-selected
               (classes/remove prev-selected "selected-node"))
             (when new-selected
               (classes/add new-selected "selected-node")
               (println (map (fn [in-id] (gdom/getElement in-id))
                             (get-in @graph-desc-atom [(.-id new-selected) "in"])))
               (let [in-ele-ids (get-in @graph-desc-atom [(.-id new-selected) "in"])
                     dest-x (js/parseFloat (.getAttribute new-selected "data-x"))
                     dest-y (js/parseFloat (.getAttribute new-selected "data-y"))
                     id->height (move-in-eles-close
                                       dest-x
                                       dest-y
                                       (get-eles in-ele-ids))]

                 (move-arrows dest-x id->height (get-eles (arrow-ele-ids (.-id new-selected)
                                                       in-ele-ids)))))))

(defn add-click-listener [ele]
  (gevents/listen
    ele
    goog.events.EventType.CLICK
    (fn [evt]
      (println "clicked" (-> evt .-currentTarget (.-id)))
      (reset! selected-node (-> evt .-currentTarget))
      (.preventDefault evt))))

(defn ^:export start [graph-desc]
  (reset! graph-desc-atom (js->clj graph-desc))
  (doseq [node-ele (seq (query ".node"))]
    (add-click-listener node-ele)))

