(ns ccdashboard.graph
  (:require [plumbing.graph :as graph]
            [plumbing.map :as map]
            [plumbing.core :as plumbing]
            [plumbing.fnk.pfnk :as pfnk]))

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