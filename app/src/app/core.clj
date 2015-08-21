(ns app.core)

(defrecord Person [first-name last-name])

(defn foo [x y]
  (println x "Hello, World: " y))

(defn bar [a b]
  (+ a b))

