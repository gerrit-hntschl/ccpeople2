
(use 'figwheel-sidecar.repl-api)
(require '[clojure.java.io :as io])
(start-figwheel!
 {:figwheel-options {}                                     ;; <-- figwheel server config goes here
  :build-ids        ["devcards"]                                ;; <-- a vector of build ids to start autobuilding
  :all-builds                                              ;; <-- supply your build configs here
                    [{:id           "devcards"
                      :figwheel     {:devcards true}
                      :source-paths ["src" "dev"]
                      :compiler     {:main            "cards.consultant"
                                     :asset-path      "js/devcards_out"
                                     :output-to       "target/figwheel/public/js/devcards.js"
                                     :output-dir      "target/figwheel/public/js/devcards_out"
                                     :source-map-timestamp true
                                     ;    :verbose    true
                                     }}]}) ;; <-- fetches configuration
(cljs-repl)
