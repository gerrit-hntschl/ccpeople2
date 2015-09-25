(ns app.domain
  #?(:cljs (:require
             [reagent.core :refer [atom]])))

(defonce app-state (atom {}))
