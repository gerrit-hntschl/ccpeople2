(defproject app "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/test.check "0.9.0"]
;                 [datascript "0.13.3"]
                 [aleph "0.4.1-beta2"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [environ "1.0.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.apache.httpcomponents/httpclient "4.5.1"]
                 [duct "0.4.2"]
                 [enlive "1.1.6"]
                 [meta-merge "0.1.1"]
                 [ring-middleware-format "0.7.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [cljs-ajax "0.5.3"]
                 [prismatic/schema "1.0.5"]
                 [prismatic/plumbing "0.5.2"]
                 [com.stuartsierra/component "0.3.1"]
                 [io.rkn/conformity "0.3.5"]
                 [org.postgresql/postgresql "9.4.1207"]
                 [com.datomic/datomic-pro "0.9.5344"
                  :exclusions [org.slf4j/slf4j-nop
                               joda-time org.slf4j/slf4j-log4j12
                               org.slf4j/slf4j-api]]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [org.clojure/core.async "0.2.374"]
                 [bidi "1.25.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [clj-time "0.11.0"]
                 [hiccup "1.0.5"]
                 [reagent "0.6.0-alpha"]
                 [cljsjs/react "0.14.3-0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [net.oauth.core/oauth "20090617"]
                 [net.oauth.core/oauth-httpclient4 "20090617"]
                 [buddy/buddy-auth "0.9.0"]
                 [cljsjs/d3 "3.5.7-1"]
                 [cljsjs/nvd3 "1.8.2-1"]
                 [cljsjs/react-select "1.0.0-beta13-0"]]

  :profiles
  {:dev           {:source-paths ["dev"]
                   :repl-options {:init-ns user
                                  ;                                      :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                                  }
                   :dependencies [[alembic "0.3.2"]
                                  [figwheel-sidecar "0.5.0-6"]
                                  [devcards "0.2.1-5"]
                                  [reloaded.repl "0.2.1"]
                                  [eftest "0.1.0"]
                                  [kerodon "0.7.0"]
                                  [figwheel "0.5.0-6"]]
                   :plugins      [[lein-cljsbuild "1.1.1"]]
                   :jvm-opts     ^:replace ["-Dfile.encoding=UTF-8" "-Xmx1G" "-Xms512m" ;"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
                                            ]}
   :test          {}
   :uberjar       {:aot          :all
                   :uberjar-name "ccdashboard.jar"
                   :prep-tasks [["clean"] ["cljsbuild" "once" "min"] ["compile"]]
                   :omit-source  true}
   :repl          {:resource-paths ^:replace ["resources" "target/figwheel"]
                   :prep-tasks     ^:replace [["compile"]]} }

  :main ccdashboard.main

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources" "target/cljsbuild"]
  :jvm-opts ^:replace ["-Dfile.encoding=UTF-8"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src" "dev"]
                        :figwheel     {:on-jsload "ccdashboard.client/on-js-reload"}

                        :compiler     {:main                 ccdashboard.client
                                       :asset-path           "js/compiled/out"
                                       :output-to            "resources/public/js/compiled/ccdashboard.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :source-map-timestamp true}}
                       {:id           "min"
                        :source-paths ["src"]
                        :compiler     {:output-to     "resources/public/js/main.js"
                                       :main          ccdashboard.client.start
                                       :optimizations :advanced
                                       :closure-defines {ccdashboard.client.core/timetrack-uri
                                                         ~(str (java.lang.System/getenv "JIRA_BASE_URL") "/secure/TempoUserBoard!timesheet.jspa")}
                                       :externs       ["externs/mixpanel_externs.js"]
                                       ;                                         :pseudo-names true
                                       ;:pretty-print  true
                                       }}]}

  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username [:env/datomic_user]
                                   :password [:env/datomic_password]}
                 "artifactory.codecentric.de" {:url "https://artifactory.codecentric.de/artifactory/repo"
                                               :username [:env/ccartuser]
                                               :password [:env/ccartpass]}
                 }

  )
