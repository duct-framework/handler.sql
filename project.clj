(defproject duct/handler.sql "0.4.0"
  :description "Duct library for building simple database-driven handlers"
  :url "https://github.com/duct-framework/handler.sql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [duct/core "0.7.0"]
                 [duct/database.sql "0.1.0"]
                 [integrant "0.7.0"]
                 [medley "1.1.0"]
                 [ring/ring-core "1.7.1"]
                 [uritemplate-clj "1.2.1"]]
  :profiles
  {:dev {:dependencies [[org.xerial/sqlite-jdbc "3.25.2"]]}})
