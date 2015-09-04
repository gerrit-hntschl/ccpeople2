(ns user
 (:require [figwheel-sidecar.repl-api :as rapi]
           [alembic.still :refer [load-project]]))

(defn cljs-repl []
 (rapi/cljs-repl))




