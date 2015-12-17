
(use 'figwheel-sidecar.repl-api)
(start-figwheel!
 {:figwheel-options {}                                     ;; <-- figwheel server config goes here
  :build-ids        ["dev"]                                ;; <-- a vector of build ids to start autobuilding
  :all-builds                                              ;; <-- supply your build configs here
                    [{:id           "dev"
                      :figwheel     true
                      :source-paths ["src" "dev"]
                      :compiler     {:main            "cljs.user"
                                     :asset-path      "js"
                                     :output-to       "target/figwheel/public/js/main.js"
                                     :output-dir      "target/figwheel/public/js"
                                     :source-map      true
                                     :source-map-path "js"
                                     ;    :verbose    true
                                     }}]}) ;; <-- fetches configuration
(cljs-repl)
