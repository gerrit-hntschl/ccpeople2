(ns app.util)

(defn matching [k v]
  (fn [m]
    (= v (get m k))))
