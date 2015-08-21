(defproject app "0.1.0-SNAPSHOT"
  :description "People app"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0-alpha2"]
                 [aleph "0.4.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [prismatic/schema "0.4.3"]
                 [com.stuartsierra/component "0.2.3"]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.datomic/datomic-pro "0.9.5206"
                  :exclusions [org.slf4j/slf4j-nop joda-time org.slf4j/slf4j-log4j12 org.slf4j/slf4j-api]]]
  :profiles {:dev        {:source-paths ["dev"]
                          :plugins      [[lein-cljsbuild "1.0.5"]
                                         [lein-environ "1.0.0"]]}
             :uberjar    {:aot :all
                          :uberjar-name "app.jar"
                          :omit-source true}}
  :source-paths ["src"]
  :jvm-opts ^:replace ["-Dfile.encoding=UTF-8"]
  :cljsbuild {:builds
              [{:id           "client"
                :source-paths ["src"]
                :compiler     {:optimizations :advanced
                               :warnings      true,
                               :pretty-print  false
                               :static-fns    true
                               :output-dir    "resources/public/js/out"
                               :output-to     "resources/public/js/out/client.js"
                               :source-map    "resources/public/js/out/client.js.map"
                               ;;                           :source-map-path "js/out"
                               }}]}
;  :main people.system
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username [:env/datomic_user]
                                   :password [:env/datomic_password]}})
