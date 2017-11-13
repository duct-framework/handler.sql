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

(deftest query-test
  (testing "with destructuring"
    (let [config  {::sql/query
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [post-id]} :route-params}
                    :sql     '["SELECT body FROM comments WHERE post_id = ?" post-id]}}
          handler (::sql/query (ig/init config))]
      (is (= (handler {:route-params {:post-id "1"}})
             {:status 200, :headers {}, :body [{:body "Great!"} {:body "Rubbish!"}]}))))

  (testing "without destructuring"
    (let [config  {::sql/query
                   {:db  (db/->Boundary (create-database))
                    :sql ["SELECT subject FROM posts"]}}
          handler (::sql/query (ig/init config))]
      (is (= (handler {})
             {:status 200, :headers {}, :body [{:subject "Test"}]}))))

  (testing "with renamed keys"
    (let [config  {::sql/query
                   {:db     (db/->Boundary (create-database))
                    :sql    ["SELECT subject, body FROM posts"]
                    :rename {:subject :post/subject}}}
          handler (::sql/query (ig/init config))]
      (is (= (handler {})
             {:status 200, :headers {}, :body [{:post/subject "Test"
                                                :body "Testing 1, 2, 3."}]}))))

  (testing "with hrefs"
    (let [config  {::sql/query
                   {:db    (db/->Boundary (create-database))
                    :sql   ["SELECT id, subject FROM posts"]
                    :hrefs {:href "/posts{/id}"}}}
          handler (::sql/query (ig/init config))]
      (is (= (handler {})
             {:status 200, :headers {}, :body [{:id   1
                                                :href "/posts/1"
                                                :subject "Test"}]}))))

  (testing "with removed keys"
    (let [config  {::sql/query
                   {:db     (db/->Boundary (create-database))
                    :sql    ["SELECT id, subject FROM posts"]
                    :hrefs  {:href "/posts{/id}"}
                    :remove [:id]}}
          handler (::sql/query (ig/init config))]
      (is (= (handler {})
             {:status 200, :headers {}, :body [{:href "/posts/1"
                                                :subject "Test"}]})))))

(deftest query-one-test
  (testing "with destructuring"
    (let [config  {::sql/query-one
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [id]} :route-params}
                    :sql     '["SELECT subject, body FROM posts WHERE id = ?" id]}}
          handler (::sql/query-one (ig/init config))]
      (is (= (handler {:route-params {:id "1"}})
             {:status 200, :headers {}, :body {:subject "Test", :body "Testing 1, 2, 3."}}))
      (is (= (handler {:route-params {:id "2"}})
             {:status 404, :headers {}, :body {:error :not-found}}))))

  (testing "with renamed keys"
    (let [config  {::sql/query-one
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [id]} :route-params}
                    :sql     '["SELECT subject, body FROM posts WHERE id = ?" id]
                    :rename  {:subject :post/subject}}}
          handler (::sql/query-one (ig/init config))]
      (is (= (handler {:route-params {:id "1"}})
             {:status 200, :headers {}, :body {:post/subject "Test"
                                               :body "Testing 1, 2, 3."}}))))

  (testing "with hrefs"
    (let [config  {::sql/query-one
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [id]} :route-params}
                    :sql     '["SELECT id, subject FROM posts WHERE id = ?" id]
                    :hrefs   {:href "/posts{/id}"}}}
          handler (::sql/query-one (ig/init config))]
      (is (= (handler {:route-params {:id "1"}})
             {:status 200, :headers {}, :body {:id      1
                                               :href    "/posts/1"
                                               :subject "Test"}}))))

  (testing "with removed keys"
    (let [config  {::sql/query-one
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [id]} :route-params}
                    :sql     '["SELECT id, subject FROM posts WHERE id = ?" id]
                    :hrefs   {:href "/posts{/id}"}
                    :remove  [:id]}}
          handler (::sql/query-one (ig/init config))]
      (is (= (handler {:route-params {:id "1"}})
             {:status 200, :headers {}, :body {:href "/posts/1"
                                               :subject "Test"}})))))
