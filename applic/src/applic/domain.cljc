(ns applic.domain
  #?(:cljs (:require
             [reagent.core :refer [atom]])))

(defonce app-state (atom {:hello/text "Hello world!"
                          :bye/text "Bye world!"}))
