(ns cljs.user
  (:require [figwheel.client :as figwheel]
            [app.client :as client]))

(js/console.info "Starting in development mode")

(enable-console-print!)

(figwheel/start {:websocket-url "ws://localhost:3449/figwheel-ws"})

(client/start)