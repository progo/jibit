(defproject jibit "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]

                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/tools.macro "0.1.2"]

                 ;; jibit
                 [reagent "0.8.1"]
                 [re-frame "0.11.0-rc3"]
                 [re-pressed "0.3.1"]
                 [day8.re-frame/http-fx "v0.2.0"]
                 [cljsjs/moment "2.24.0-0"]

                 ;; Clurator, file ops
                 [me.raynes/fs "1.4.6"]
                 [com.drewnoakes/metadata-extractor "2.13.0"]
                 [com.github.mjeanroy/exiftool-lib "2.5.0"]

                 ;; Database things
                 [seancorfield/next.jdbc "1.0.13"]
                 [org.xerial/sqlite-jdbc "3.30.1"]
                 [honeysql "0.9.8"]

                 [clojure.java-time "0.3.2"]

                 ;; Clurator, server ops
                 [http-kit "2.3.0"]
                 [compojure "1.6.1"]]

  :main clurator.core
  :source-paths ["src"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "jibit.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.3"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   }})

