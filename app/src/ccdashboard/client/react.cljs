(ns ccdashboard.client.react
  (:require [reagent.core :as reagent]
            cljsjs.react-select))

(def select (reagent/adapt-react-class js/Select))
