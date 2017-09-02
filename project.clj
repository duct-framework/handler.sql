(defproject duct/handler.sql "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha19"]
                 [org.clojure/java.jdbc "0.7.0"]
                 [duct/core "0.6.1"]
                 [integrant "0.6.1"]
                 [ring/ring-core "1.6.2"]]
  :profiles
  {:dev {:dependencies [[org.xerial/sqlite-jdbc "3.20.0"]]}})
