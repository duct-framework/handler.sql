(ns duct.handler.sql-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [duct.database.sql :as db]
            [duct.handler.sql :as sql]
            [integrant.core :as ig]))

(defn- create-database []
  (doto {:connection (jdbc/get-connection {:connection-uri "jdbc:sqlite:"})}
    (jdbc/execute! "CREATE TABLE posts    (id INT PRIMARY KEY, subject TEXT, body TEXT)")
    (jdbc/execute! "CREATE TABLE comments (id INT PRIMARY KEY, post_id INT,  body TEXT)")
    (jdbc/insert! :posts    {:id 1, :subject "Test", :body "Testing 1, 2, 3."})
    (jdbc/insert! :comments {:id 1, :post_id 1, :body "Great!"})
    (jdbc/insert! :comments {:id 2, :post_id 1, :body "Rubbish!"})))

(deftest get-select-test
  (let [config  {::sql/get-select
                 {:db      (db/->Boundary (create-database))
                  :request '{{:keys [post-id]} :route-params}
                  :query   '["SELECT body FROM comments WHERE post_id = ?" post-id]}}
        handler (::sql/get-select (ig/init config))]
    (is (= (handler {:request-method :get, :route-params {:post-id "1"}})
           {:status 200, :headers {}, :body [{:body "Great!"} {:body "Rubbish!"}]}))))
