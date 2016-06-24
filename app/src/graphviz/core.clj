(ns graphviz.core
  (:require [analemma.svg :as s]
            [lacij.view.core :as lacij-view-core]
            [lacij.edit.graph :as lacij]
            [lacij.layouts.layout :as lacij-layout]
            [lacij.view.graphview :as lacij-view]
            [tikkba.utils.dom :as tikkba]
            [clojure.set :as set]
            [tikkba.utils.dom :as dom]
            [analemma.svg :as s]
            [clojure.pprint :as pprint]
            [lacij.view.core :as lacij-view-core]
            [analemma.xml :as xml]
            [plumbing.fnk.pfnk :as pfnk]
            [plumbing.core :refer [map-vals] :as plumbing]
            [clojure.string :as str])
  (:import
    (java.awt Rectangle)
    (org.apache.batik.dom.svg SVGDOMImplementation)
    (lacij.view.core Decorator))
  )

(def ^{:doc "The SVG namespace URI."} svg-ns SVGDOMImplementation/SVG_NAMESPACE_URI)

(defn view-labels
  [labels options]
  (let [{:keys [x y xmargin text-anchor text-anchor-multi x-multi y-multi]
         :or {x 0 y 0 xmargin 0 text-anchor "middle"
              x-multi 0 y-multi 0 text-anchor-multi "start"}} options]
    (map (fn [label]
           ;; TODO: use the position indicator
           (let [txt (:text label)
                 pos (:position label)
                 style (:style label)
                 font-size (or (:font-size (:attrs label)) 12)
                 ;; TODO support font size expressed in px
                 dy font-size
                 text (if (string? txt)
                        (s/text {:x         x :y y :text-anchor text-anchor
                                 :font-size font-size} txt)
                        (apply s/text {:text-anchor text-anchor-multi
                                       :font-size   font-size :x x-multi :y y-multi}
                               (map (fn [s]
                                      (s/tspan {:dy dy :x xmargin} s))
                                    txt)))]
             (lacij.view.utils.style/apply-styles text {:dominant-baseline :central} style)))
         labels)))

(defn by-two
  [i]
  (int (/ i 2)))

(defn document-element
  "See org.w3c.dom.Document.getDocumentElement"
  [doc]
  (.getDocumentElement doc))

(defn create-document
  "See org.w3c.dom.DOMImplementation.createDocument"
  [domimpl ns name doctype]
  (.createDocument domimpl ns name doctype))

(defn set-attribute-ns
  "See org.w3c.dom.Element.setAttributeNS."
  [elt ns name value]
  (.setAttributeNS elt ns name value))

(defn set-attribute
  "See org.w3c.dom.Element.setAttribute."
  [elt name value]
  (.setAttribute elt name value))

(defn attribute
  "See org.w3c.dom.Element.getAttribute."
  [elt name]
  (.getAttribute elt name))

(defn set-text-content
  "See org.w3c.dom.Node.setTextContent."
  [node text-content]
  (.setTextContent node text-content))

(defn create-element-ns
  "See org.w3c.dom.Document.createElementNS."
  [doc ns name]
  (.createElementNS doc ns name))

(defn append-child
  "See org.w3c.dom.Node.appendChild"
  [node child]
  (.appendChild node child))

(defn insert-before
  "See org.w3c.dom.Node.insertBefore"
  [node new-child ref-child]
  (.insertBefore node new-child ref-child))

(defn remove-child
  "See org.w3c.dom.Node.appendChild"
  [node child]
  (.removeChild node child))

(defn child-nodes
  "See org.w3c.dom.Node.getChildNodes"
  [node]
  (.getChildNodes node))

(defn next-sibling
  "See org.w3c.dom.Node.getNextSibling"
  [node]
  (.getNextSibling node))

(defn element-by-id
  "See org.w3c.dom.Document.getElementById"
  [doc id]
  (.getElementById doc id))

(defn append-children
  "Add the children to node."
  [node children]
  (doseq [child children]
    (append-child node child)))

(defn add-attrs
  "Adds the attributes to the element elt."
  [elt & attrs]
  (doseq [[key value] (partition 2 attrs)]
    (let [name (name key)
          value (str value)
          [local-name ns-alias] (reverse (str/split name #":"))]
      (if ns-alias
        (set-attribute-ns elt (.lookupNamespaceURI elt ns-alias) local-name value)
        (set-attribute elt name value)))))

(defn add-map-attrs
  "Adds the attributes represented by a map to the element elt"
  [elt attrs]
  (apply add-attrs elt (flatten (seq attrs))))

(defn get-ns [tag ns]
  (if (and (> (count tag) 1)
           (map? (second tag)))
    (get (second tag) :ns ns)
    ns))

(defn elt [doc ns tag]
  (if (string? tag)
    (throw (IllegalArgumentException. (format "Illegal argument %s" tag)))
    (let [name (xml/get-name tag)
          e (create-element-ns doc ns name)
          attrs (xml/get-attrs tag)]
      (add-map-attrs e attrs)
      e)))

(defn elements-helper
  ([doc ns tag]
   (let [ns (get-ns tag ns)
         root-elt (elt doc ns tag)

         children (xml/get-content tag)
         children-elts (map #(elt doc ns %) children)]
     (append-children root-elt children-elts)
     (elements-helper doc ns root-elt children children-elts)))
  ([doc ns root-elt queued-tags queued-elts]
   (if (empty? queued-tags)
     root-elt
     (let [[tag & xs] queued-tags
           [e & rst-elts] queued-elts]
       (if (string? tag)
         (recur doc ns root-elt xs queued-elts)
         (let [ns (get-ns tag ns)
               children (xml/get-content tag)
               fchild (first children)]
           (if (and (= (count children) 1) (string? fchild))
             (do
               (set-text-content e fchild)
               (recur doc
                      ns
                      root-elt
                      (concat xs children)
                      rst-elts))
             (let [children-elts (map #(elt doc ns %) children)]
               (append-children e children-elts)
               (recur doc
                      ns
                      root-elt
                      (concat xs children)
                      (concat rst-elts children-elts))))))))))

(defn elements
  "Converts the XML vector representations into XML elements."
  [doc ns tag]
  (elements-helper doc ns tag))

(defn foreign-object [text width input-keys]
  (let [input-names (into #{}
                          (map (fn [input-key]
                                 (-> input-key name (.replace \-  \u2011))))
                          input-keys)
        formatted-code (-> (read-string text)
                           (pprint/write
                             :dispatch pprint/code-dispatch)
                           (with-out-str)
                           (.replace \-  \u2011))
        code-with-marked-input (reduce (fn [code input-name]
                                             (-> code
                                                 (str/replace input-name (str "@@@" input-name "@@@"))))
                                           formatted-code
                                           input-names)
        code-markup (-> code-with-marked-input
                        (str/split #"@@@")
                        (->> (map (fn [s]
                                    (if (contains? input-names s)
                                      [:span {:class "input-param"} s]
                                      [:span s])))))

        number-of-lines (-> formatted-code (.split "\n") (count))
        height (str (* 60 number-of-lines))]
    [:foreignObject {:width     width
                     :height    height
                     :ns        "http://www.w3.org/1999/xhtml"
                     :font-size "12"
                     :y         "40"}
     [:body
      [:div {:style "border-radius: 5px; background:white; overflow:auto;"
             :class "node-code"}
       [:pre
        (into [:code {:class "clojure"}]
              code-markup)]]]]))

(defrecord RectNodeView2
  [id
   x
   y
   width
   height
   labels
   default-style
   style
   attrs
   decorators]
  lacij-view-core/NodeView

  (view-node
    [this node context]
    (let [{:keys [doc]} context
          [x-center y-center] (lacij-view-core/center this)
          texts (view-labels labels {:x (by-two width)
                                     :y (by-two height)
                                     :xmargin 5
                                     :text-anchor "middle"
                                     :text-anchor-multi "start"})
          decorations (map #(lacij-view-core/decorate % this context) decorators)
          xml (concat (s/group
                        {:id (name id)
                         :class "gnode"
                         :data-x x
                         :data-y y
                         :transform (format "translate(%s, %s)" x y)}
                        (-> [:rect {:height height :width width}]
                            (lacij.view.utils.style/apply-styles default-style style)
                            (lacij.view.utils.style/apply-attrs attrs)))
                      texts
                      decorations
                      )]
      ;      (prn "xml =")
      ;(pprint/pprint xml)
      (elements doc svg-ns xml)))

  (center
    [this]
    [(double (+ x (by-two width)))
     (double (+ y (by-two height)))])

  (ports
    [this]
    [[x y] [(double (+ x (by-two width))) y] [(+ x width) y]
     [x (double (+ y (by-two height)))] [(+ x width) (double (+ y (by-two height)))]
     [x (+ y height)] [(double (+ x (by-two width))) (+ y height)] [(+ x width) (+ y height)]])

  (contains-pt?
    [this x y]
    (.contains (Rectangle. (:x this) (:y this) width (* 2 height)) x y))

  (bounding-box
    [this]
    (let [margin 5]
      [(- x margin) (- y margin) (+ width (* 2 margin)) (+ (* 2 height) (* 2 margin))])))

(defn add-nodes [g nodes style min-width]
  (reduce (fn [g node]
            (lacij/add-node g node (name node)
                            :style style
                            :class "node"
                            :width (max min-width (* (count (name node)) 6))))
          g
          nodes))

(defn add-edges [g edges]
  (reduce (fn [g [src dst]]
            (let [id (keyword (str (name src) "-" (name dst)))]
              (lacij/add-edge g id src dst)))
          g
          edges))

(defn find-max-y [g]
  (->> g
       (:edges)
       (vals)
       (map :view)
       (keep :points)
       (mapcat identity)
       (map second)
       (apply max)))

(defn add-decorator [g nodes decorator]
        (reduce (fn [g node] (lacij/add-decorator g node decorator))
                g
                nodes))

(defrecord CodeDecorator [g]
  Decorator
   (lacij-view-core/decorate [this view context]
             (-> g (get (:id view)) (meta) (:source) (foreign-object (:width view) (pfnk/input-schema-keys (get g (:id view)))))))

(defmacro fnk [bindings & body]
            `(vary-meta (plumbing/fnk ~bindings ~@body) assoc :source ~(apply str body)))

(defn get-nodes [g]
  (into #{} (concat (mapcat pfnk/input-schema-keys (vals g)) (keys g))))

(defn get-edges [g nodes]
  (->> nodes
       (map (juxt identity
                  (fn [node] (some-> (get g node) (pfnk/input-schema-keys)))))
       (filter (comp some? second))
       (mapcat (fn [[dest srcs]]
                 (map (fn [src]
                        [src dest])
                      srcs)))))

(defn as-svg [g node-width]
  (let [nodes (get-nodes g)
        edges (get-edges g nodes)
        target-nodes (->> edges (group-by second) (keys) (into #{}))
        in-nodes (set/difference nodes target-nodes)
        source-nodes (->> edges (group-by first) (keys) (into #{}))
        out-nodes (set/difference nodes source-nodes)
        inner-nodes (set/difference nodes (set/union in-nodes out-nodes))
        svg-graph (-> (lacij/graph)
                      (lacij/add-default-node-attrs :rx 5 :ry 5)
                      (lacij/set-node-view-factory (fn [id shape x y style attrs]
                                                     (when (= :rect shape)
                                                       (let [default-style {:fill "white" ;:stroke "black"
                                                                            }
                                                             {:keys [x y width height r] :or {x x y y width 100 height 40 r 20}} attrs]
                                                         (->RectNodeView2 id x y width height [] default-style style attrs #{})))))
                      (add-nodes in-nodes {:fill "lightgreen"} node-width)
                      (add-nodes out-nodes {:fill "royalblue"} node-width)
                      (add-nodes inner-nodes {:fill "white"} node-width)
                      (add-decorator inner-nodes (->CodeDecorator g))
                      (add-decorator out-nodes (->CodeDecorator g))
                      (add-edges edges)
                      (lacij-layout/layout :hierarchical :flow :out :inlayer-space 80))
        fix-height (max (+ 30 (find-max-y svg-graph)) (:height svg-graph))]
    (-> svg-graph
        (assoc :height fix-height)
        (lacij/build))))

(defn as-svg-str [g node-width]
  (tikkba/spit-str (:xmldoc (as-svg g node-width))))

(defn graph-description [g node-width]
  (let [nodes (get-nodes g)
        edges (get-edges g nodes)
        node->out-nodes (->> edges
                             (group-by first)
                             (map-vals (fn [edges] (into #{} (map second) edges))))
        node->in-nodes (->> edges
                            (group-by second)
                            (map-vals (fn [edges] (into #{} (map first) edges))))]
    {:nodes (into {}
                  (map (fn [node]
                         [node {:in  (get node->in-nodes node)
                                :out (get node->out-nodes node)}]))
                  nodes)
     :sizes {:node/width node-width
             :node/height 120}}))
