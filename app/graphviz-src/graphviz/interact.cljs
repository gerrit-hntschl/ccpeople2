(ns graphviz.interact
  (:import [goog.dom query])
  (:require
    [goog.dom :as gdom]
    goog.fx.dom
    goog.fx.easing
    [goog.dom.classlist :as classes]
    [plumbing.core :refer-macros [fnk]]
    [plumbing.graph :as graph]
    [goog.events :as gevents]
    [clojure.set :as set]))

(enable-console-print!)

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

(def graph-desc-atom (atom nil))
(def selected-node (atom nil))

(defn append-arrow! [position-x1 position-y1 position-x2 position-y2]
  (let [arrow (doto (.createElementNS js/document "http://www.w3.org/2000/svg" "line")
                (.setAttribute "x1" position-x1)
                (.setAttribute "y1" position-y1)
                (.setAttribute "x2" position-x2)
                (.setAttribute "y2" position-y2)
                (.setAttribute "marker-end" "url(#lacij-end-arrow-marker)")
                (.setAttribute "style" "stroke: #000000;stroke-width: 1;")
                (classes/add "highlight-arrow"))]
    (.appendChild (gdom/getElement "graph0") arrow)))

(def in-eles-positioning
  {:position-x1 (fnk [center-x highlighted-nodes-distance]
                  (- center-x highlighted-nodes-distance))
   :position-y1 (fnk [ele-height new-center-y]
                  (+ new-center-y (/ ele-height 3)))
   :position-x2 (fnk [center-x]
                  center-x)
   :position-y2 (fnk [center-y arrow-y-offset ele-height]
                  (+ center-y (/ ele-height 2) arrow-y-offset))
   :translate-x (fnk [center-x ele-width highlighted-nodes-distance]
                  (- center-x (+ ele-width highlighted-nodes-distance)))
   :translate-y (fnk [new-center-y]
                  new-center-y)})

(def out-eles-positioning
  {:position-x1 (fnk [center-x ele-width]
                  (+ center-x ele-width))
   :position-y1 (fnk [ele-height center-y arrow-y-offset]
                  (+ center-y (/ ele-height 3) arrow-y-offset))
   :position-x2 (fnk [center-x ele-width highlighted-nodes-distance]
                  (+ center-x (+ ele-width highlighted-nodes-distance)))
   :position-y2 (fnk [new-center-y ele-height]
                  (+ new-center-y (/ ele-height 3)))
   :translate-x (fnk [center-x ele-width highlighted-nodes-distance]
                  (+ center-x (+ ele-width highlighted-nodes-distance)))
   :translate-y (fnk [new-center-y]
                  new-center-y)})

(def move-eles-graph
  {:highlighted-nodes-distance (fnk [] 100)
   :number-eles      (fnk [node-eles]
                       (count node-eles))
   :half-number-eles (fnk [number-eles]
                       (int (/ number-eles 2)))
   :node-offsets     (fnk [number-eles half-number-eles node-ele-height]
                       (if (even? number-eles)
                         (range (+ (/ node-ele-height 2) (- (* half-number-eles node-ele-height)))
                                (- (* (inc half-number-eles) node-ele-height) (/ node-ele-height 2))
                                node-ele-height)
                         (range (- (* half-number-eles node-ele-height))
                                (* (inc half-number-eles) node-ele-height)
                                node-ele-height)))
   :arrow-y-offsets  (fnk [node-offsets]
                       (into []
                             (map (partial * 0.15))
                             node-offsets))
   :new-ys           (fnk [node-offsets center-y]
                       (into []
                             (map (partial + center-y))
                             node-offsets))
   :move-all!        (fnk [node-eles new-ys arrow-y-offsets center-x center-y node-ele-height positions node-ele-width highlighted-nodes-distance]
                       (doseq [[ele new-center-y arrow-y-offset] (map vector node-eles new-ys arrow-y-offsets)]
                         (let [{:keys [position-x1 position-y1 position-x2 position-y2 translate-x translate-y]}
                               (positions
                                 {:center-x       center-x
                                  :center-y       center-y
                                  :ele-height     node-ele-height
                                  :ele-width      node-ele-width
                                  :new-center-y   new-center-y
                                  :arrow-y-offset arrow-y-offset
                                  :highlighted-nodes-distance highlighted-nodes-distance})]
                           (.setAttribute ele "transform" (str "translate(" translate-x "," translate-y ")"))
                           (classes/add ele "highlighted-node")
                           (append-arrow! position-x1 position-y1 position-x2 position-y2))))})

;; wrap :positions in a fnk, otherwise it would be compiled into the graph as a nested subgraph,
;; but we want it for polymorphic behavior inside a loop.
(def move-in-eles (graph/compile (assoc move-eles-graph :positions (fnk [] (graph/compile in-eles-positioning)))))
(def move-out-eles (graph/compile (assoc move-eles-graph :positions (fnk [] (graph/compile out-eles-positioning)))))

(defn get-eles [ele-ids]
  (map (fn [id] (gdom/getElement id))
       ele-ids))

(defn float-attr [ele attr-name]
  (js/parseFloat (.getAttribute ele attr-name)))

(defn get-viewport-size []
  (let [window (gdom/getWindow)
        viewport-size (gdom/getViewportSize window)]
    {:width (.-width viewport-size)
     :height (.-height viewport-size)}))

(defn scroll! [el start end time]
  (.play (goog.fx.dom.Scroll. el (clj->js start) (clj->js end) time goog.fx.easing/inAndOut)))

(defn scroll-to! [dest-x-and-y animation-duration]
  (scroll! (.-body js/document)
           [(.-scrollX js/window) (.-scrollY js/window)]
           dest-x-and-y
           animation-duration))

(defn revert-to-default-positions [graph-desc-state prev-selected]
  (classes/remove prev-selected "selected-node")
  (classes/remove prev-selected "highlighted-node")
  (let [in-ele-ids (get-in graph-desc-state [(.-id prev-selected) "in"])
        out-ele-ids (get-in graph-desc-state [(.-id prev-selected) "out"])]
    (doseq [ele (get-eles (concat in-ele-ids out-ele-ids))]
      (let [orig-x (float-attr ele "data-x")
            orig-y (float-attr ele "data-y")]
        (.setAttribute ele "transform" (str "translate(" orig-x "," orig-y ")"))))))

(defn highlight-selection [graph-desc-state sizes new-selected]
  (classes/add new-selected "selected-node")
  (classes/add new-selected "highlighted-node")
  (let [in-ele-ids (get-in graph-desc-state [(.-id new-selected) "in"])
        out-ele-ids (get-in graph-desc-state [(.-id new-selected) "out"])
        in-eles (get-eles in-ele-ids)
        out-eles (get-eles out-ele-ids)
        sorted-in-eles (sort-by (fn [ele] (float-attr ele "data-y")) in-eles)
        sorted-out-eles (sort-by (fn [ele] (float-attr ele "data-y")) out-eles)
        highlighted-gs (into #{new-selected} (concat in-eles out-eles))
        all-gs (into #{} (query "#graph0 g"))
        other-gs (set/difference
                   all-gs
                   highlighted-gs)
        dest-x (float-attr new-selected "data-x")
        dest-y (float-attr new-selected "data-y")
        {:keys [width height]} (get-viewport-size)
        node-width (get sizes "node/width")
        node-height (get sizes "node/height")]
    (scroll-to! [(+ (/ node-width 2) (- dest-x (/ width 2))) (+ (/ node-height 2) (- dest-y (/ height 2)))] 300)
    (move-in-eles
      {:center-x  dest-x
       :center-y  dest-y
       :node-eles sorted-in-eles
       :node-ele-width node-width
       :node-ele-height node-height})
    (move-out-eles
      {:center-x  dest-x
       :center-y  dest-y
       :node-eles sorted-out-eles
       :node-ele-width node-width
       :node-ele-height node-height})
    (doseq [other-g other-gs]
      (classes/add other-g "background"))
    (doseq [g highlighted-gs]
      (classes/remove g "background"))))

(defn revert-to-normal-display []
  (doseq [g (query "#graph0 g")]
    (classes/remove g "background")))

(defn remove-highlight-arrows []
  (doseq [arrow (gdom/getElementsByClass "highlight-arrow")]
    (gdom/removeNode arrow)))

(add-watch selected-node :selected-node-focus
           (fn [_ _ prev-selected new-selected]
             (let [graph-desc-state @graph-desc-atom
                   nodes-description (get graph-desc-state "nodes")
                   sizes (get graph-desc-state "sizes")]
               (remove-highlight-arrows)
               (when prev-selected
                 (revert-to-default-positions nodes-description prev-selected))
               (if new-selected
                 (highlight-selection nodes-description sizes new-selected)
                 (revert-to-normal-display)))))

(defn add-click-listener [ele callback]
  (gevents/listen
    ele
    goog.events.EventType.CLICK
    callback))

(defn ^:export start [graph-desc]
  (reset! graph-desc-atom (js->clj graph-desc))
  (add-click-listener
    (.-body js/document)
    (fn [_]
      (reset! selected-node nil)))
  (doseq [node-ele (seq (query ".gnode"))]
    (add-click-listener node-ele
                        (fn [evt]
                          (reset! selected-node (-> evt .-currentTarget))
                          (.preventDefault evt)
                          (.stopPropagation evt)))))
