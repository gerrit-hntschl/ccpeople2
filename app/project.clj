(defproject app "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [aleph "0.4.0"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [com.cognitect/transit-clj "0.8.281"]
                 [environ "1.0.1"]
                 [duct "0.4.2"]
                 [meta-merge "0.1.1"]
                 [ring-middleware-format "0.6.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [org.clojure/tools.reader "0.9.2"]
                 [cljs-ajax "0.3.14"]
                 [prismatic/schema "0.4.3"]
                 [com.stuartsierra/component "0.3.0"]
                 [io.rkn/conformity "0.3.5"]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.datomic/datomic-pro "0.9.5206"
                  :exclusions [org.slf4j/slf4j-nop
                               joda-time org.slf4j/slf4j-log4j12
                               org.slf4j/slf4j-api]]
                 [ch.qos.logback/logback-classic "1.0.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [bidi "1.20.3"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [hiccup "1.0.5"]
                 [reagent "0.5.0"]
                 [buddy/buddy-auth "0.6.2"]
                 [sudharsh/clj-oauth2 "0.5.3"]]

  :profiles
  {:dev           [:project/dev :profiles/dev]
   :test          [:project/test :profiles/test]
   :uberjar {:aot          :all
             :uberjar-name "app.jar"
             :omit-source  true}
   :repl          {:resource-paths ^:replace ["resources" "target/figwheel"]
                   :prep-tasks     ^:replace [["compile"]]}
   :profiles/dev  {}
   :profiles/test {}
   :dev   {:source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :dependencies [[alembic "0.3.2"]
                                  [figwheel-sidecar "0.4.0"]
                                  [reloaded.repl "0.2.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [eftest "0.1.0"]
                                  [kerodon "0.7.0"]
                                  [duct/figwheel-component "0.2.0"]
                                  [figwheel "0.4.0"]]
                   :plugins      [[lein-cljsbuild "1.1.0"]
                                  [lein-figwheel "0.4.0"]
                                  [lein-environ "1.0.1"]]
                   :jvm-opts     ^:replace ["-Dfile.encoding=UTF-8" ;"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
                                            ]
                   :env          {:port 8000}}
   :project/test  {}}

  :main app.main

  :source-paths ["src"]
  :resource-paths ["resources" "target/cljsbuild"]
  :prep-tasks [["cljsbuild" "once"] ["compile"]]
  :jvm-opts ^:replace ["-Dfile.encoding=UTF-8"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {
              :builds [{:id           "main"
                        :jar          true
                        :source-paths ["src"]
                        :compiler     {:output-to     "target/cljsbuild/public/js/main.js"
                                       :optimizations :advanced}}
                       {:id           "dev"
                          :source-paths ["src" "dev"]
                          :figwheel     {:on-jsload "app.client/on-js-reload"}

                          :compiler     {:main                 app.client
                                         :asset-path           "js/compiled/out"
                                         :output-to            "resources/public/js/compiled/app.js"
                                         :output-dir           "resources/public/js/compiled/out"
                                         :source-map-timestamp true}}
                       {:id           "min"
                          :source-paths ["src"]
                          :compiler     {:output-to     "resources/public/js/compiled/app.js"
                                         :main          app.core
                                         :optimizations :advanced
                                         :pretty-print  false}}]}

  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username [:env/datomic_user]
                                   :password [:env/datomic_password]}}


)
