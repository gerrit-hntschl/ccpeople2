(ns app.cards
  (:require
    [sablono.core :as sab]) ; just for example
  (:require-macros
    [devcards.core :refer [defcard]]))

(defcard my-first-card
         (sab/html [:div [:h1 "Devcards is freaking awesome!"]
                    [:p "It is"]]))
