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

(deftest select-test
  (let [config  {::sql/select
                 {:db      (db/->Boundary (create-database))
                  :request '{{:keys [post-id]} :route-params}
                  :query   '["SELECT body FROM comments WHERE post_id = ?" post-id]}}
        handler (::sql/select (ig/init config))]
    (is (= (handler {:route-params {:post-id "1"}})
           {:status 200, :headers {}, :body [{:body "Great!"} {:body "Rubbish!"}]}))))

(deftest select-one-test
  (let [config  {::sql/select-one
                 {:db      (db/->Boundary (create-database))
                  :request '{{:keys [id]} :route-params}
                  :query   '["SELECT subject, body FROM posts WHERE id = ?" id]}}
        handler (::sql/select-one (ig/init config))]
    (is (= (handler {:route-params {:id "1"}})
           {:status 200, :headers {}, :body {:subject "Test", :body "Testing 1, 2, 3."}}))
    (is (= (handler {:route-params {:id "2"}})
           {:status 404, :headers {}, :body {:error :not-found}}))))
