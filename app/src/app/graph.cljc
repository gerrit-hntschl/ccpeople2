(ns app.graph
  (:require [plumbing.graph :as graph]
            [plumbing.map :as map]
            [plumbing.core :as plumbing]
            [plumbing.fnk.pfnk :as pfnk]
    #?@(:clj [
            [lacij.edit.graph :as lacij]
            [lacij.layouts.layout :as lacij-layout]
            [lacij.view.graphview :as lacij-view]
            [tikkba.utils.dom :as tikkba]])
            [clojure.set :as set])
  #?(:clj (:import (lacij.view.core Decorator))))

(defn merge-inputs [f]
  (pfnk/fn->fnk
    (fn [m] (merge m (f m)))
    [(pfnk/input-schema f)
     (merge (pfnk/output-schema f)
            (map/keep-leaves identity (pfnk/input-schema f)))]))

(defn handle-exi [exi m k im]
  (let [data (-> (ex-data exi)
                 (assoc :exception-during k)
                 (assoc :input-params (dissoc im :jira))
                 (assoc :graph (dissoc m :jira)))]
    (def bb data)
    (throw (ex-info #?(:clj (.getMessage exi)
                       :cljs (.-message exi))
                    data
                    exi))))

(defn compile-cancelling [g]
  (graph/simple-hierarchical-compile
    g
    true
    (fn [m] m)
    (fn [m k f]
      (let [im (select-keys m (pfnk/input-schema-keys f))]
        (assoc m k (try (f im)
                        (catch #?(:clj Throwable
                                  :cljs js/Error) ex
                          (def oex ex)
                          (handle-exi ex m k im))))))))

(def compile-cancelling-with-input (comp merge-inputs compile-cancelling))

(defn constant [value]
  (plumbing/fnk [] value))

#?(:clj
   (defn add-nodes [g nodes style]
     (reduce (fn [g node]
               (lacij/add-node g node (name node)
                               :style style
                               :class "node"
                               :width (max 100 (* (count (name node)) 6))))
             g
             nodes)))

#?(:clj (defn add-edges [g edges]
          (reduce (fn [g [src dst]]
                    (let [id (keyword (str (name src) "-" (name dst)))]
                      (lacij/add-edge g id src dst)))
                  g
                  edges)))


(defn find-max-y [g]
  (->> g
       (:edges)
       (vals)
       (map :view)
       (keep :points)
       (mapcat identity)
       (map second)
       (apply max)))

#?(:clj (defn add-decorator [g nodes decorator]
          (reduce (fn [g node] (lacij/add-decorator g node decorator))
                  g
                  nodes)))

#?(:clj (defn foreign-object [text]
          [:foreignObject {:width "100" :height "30"}
           [:body [:p text]]]))

#?(:clj (defrecord CodeDecorator [g]
          Decorator
          (decorate [this view context]
            (clojure.pprint/pprint {:view view})
            (-> g (get (:id view)) (meta) (:source) (foreign-object)))))

#?(:clj (defmacro fnk [bindings & body]
          `(vary-meta (plumbing/fnk ~bindings ~@body) assoc :source ~(apply str body))))

#?(:clj (defn as-svg [g]
          (let [nodes (into #{} (concat (mapcat pfnk/input-schema-keys (vals g)) (keys g)))
                edges (->> nodes
                           (map (juxt identity
                                      (fn [node] (some-> (get g node) (pfnk/input-schema-keys)))))
                           (filter (comp some? second))
                           (mapcat (fn [[dest srcs]]
                                     (map (fn [src]
                                            [src dest])
                                          srcs))))
                target-nodes (->> edges (group-by second) (keys) (into #{}))
                in-nodes (set/difference nodes target-nodes)
                source-nodes (->> edges (group-by first) (keys) (into #{}))
                out-nodes (set/difference nodes source-nodes)
                inner-nodes (set/difference nodes (set/union in-nodes out-nodes))
                svg-graph (-> (lacij/graph)
                              (lacij/add-default-node-attrs :rx 5 :ry 5)
                              (add-nodes in-nodes {:fill "lightgreen"})
                              (add-nodes out-nodes {:fill "royalblue"})
                              (add-nodes inner-nodes {:fill "white"})
                              (add-decorator inner-nodes (->CodeDecorator g))
                              (add-edges edges)
                              (lacij-layout/layout :hierarchical :flow :out))
                fix-height (max (+ 30 (find-max-y svg-graph)) (:height svg-graph))]
            (-> svg-graph
                (assoc :height fix-height)
                (lacij/build)))))

#?(:clj (defn as-svg-str [g]
          (tikkba/spit-str (:xmldoc (as-svg g)))))