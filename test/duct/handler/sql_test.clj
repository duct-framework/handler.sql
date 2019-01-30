(ns duct.handler.sql-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [duct.core :as duct]
            [duct.database.sql :as db]
            [duct.handler.sql :as sql]
            [integrant.core :as ig]))

(duct/load-hierarchy)

(defn- create-database []
  (doto {:connection (jdbc/get-connection {:connection-uri "jdbc:sqlite:"})}
    (jdbc/execute! "CREATE TABLE posts (id INTEGER PRIMARY KEY, subject TEXT, body TEXT)")
    (jdbc/execute! "CREATE TABLE comments (id INTEGER PRIMARY KEY, post_id INT, body TEXT)")
    (jdbc/insert! :posts    {:id 1, :subject "Test", :body "Testing 1, 2, 3."})
    (jdbc/insert! :comments {:id 1, :post_id 1, :body "Great!"})
    (jdbc/insert! :comments {:id 2, :post_id 1, :body "Rubbish!"})))

(deftest derive-test
  (isa? ::sql/query     :duct.module.sql/requires-db)
  (isa? ::sql/query-one :duct.module.sql/requires-db)
  (isa? ::sql/execute   :duct.module.sql/requires-db)
  (isa? ::sql/insert    :duct.module.sql/requires-db))

(deftest prep-test
  (testing "query"
    (is (= (ig/prep {::sql/query
                     {:sql ["SELECT * FROM comments"]}})
           {::sql/query
            {:db  (ig/ref :duct.database/sql)
             :sql ["SELECT * FROM comments"]}})))

  (testing "query-one"
    (is (= (ig/prep {::sql/query
                     {:request '{{:keys [id]} :route-params}
                      :sql     '["SELECT subject, body FROM posts WHERE id = ?" id]}})
           {::sql/query
            {:db      (ig/ref :duct.database/sql)
             :request '{{:keys [id]} :route-params}
             :sql     '["SELECT subject, body FROM posts WHERE id = ?" id]}})))

  (testing "execute"
    (is (= (ig/prep {::sql/query
                     {:request '{{:keys [id]} :route-params, {:strs [body]} :form-params}
                      :sql     '["UPDATE comments SET body = ? WHERE id = ?" body id]}})
           {::sql/query
            {:db      (ig/ref :duct.database/sql)
             :request '{{:keys [id]} :route-params, {:strs [body]} :form-params}
             :sql     '["UPDATE comments SET body = ? WHERE id = ?" body id]}})))

  (testing "insert"
    (is (= (ig/prep {::sql/query
                     {:request  '{{:keys [pid]} :route-params, {:strs [body]} :form-params}
                      :sql      '["INSERT INTO comments (post_id, body) VALUES (?, ?)" pid body]
                      :location "/posts{/pid}/comments{/last_insert_rowid}"}})
           {::sql/query
            {:db       (ig/ref :duct.database/sql)
             :request  '{{:keys [pid]} :route-params, {:strs [body]} :form-params}
             :sql      '["INSERT INTO comments (post_id, body) VALUES (?, ?)" pid body]
             :location "/posts{/pid}/comments{/last_insert_rowid}"}}))))

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

  (testing "with hrefs from request vars"
    (let [config  {::sql/query
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [pid]} :route-params}
                    :sql     ["SELECT id, body FROM comments"]
                    :hrefs   {:href "/posts{/pid}/comments{/id}"}}}
          handler (::sql/query (ig/init config))]
      (is (= (handler {:route-params {:pid "1"}})
             {:status  200
              :headers {}
              :body    [{:id 1, :href "/posts/1/comments/1", :body "Great!"}
                        {:id 2, :href "/posts/1/comments/2", :body "Rubbish!"}]}))))

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

  (testing "with hrefs from request vars"
    (let [config  {::sql/query-one
                   {:db      (db/->Boundary (create-database))
                    :request '{{:keys [id]} :route-params}
                    :sql     '["SELECT subject FROM posts WHERE id = ?" id]
                    :hrefs   {:href "/posts{/id}"}}}
          handler (::sql/query-one (ig/init config))]
      (is (= (handler {:route-params {:id "1"}})
             {:status 200, :headers {}, :body {:href "/posts/1"
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

(deftest execute-test
  (let [db      (create-database)
        config  {::sql/execute
                 {:db      (db/->Boundary db)
                  :request '{{:keys [id]} :route-params, {:strs [body]} :form-params}
                  :sql     '["UPDATE comments SET body = ? WHERE id = ?" body id]}}
        handler (::sql/execute (ig/init config))]
    (testing "valid update"
      (is (= (handler {:route-params {:id "1"}, :form-params {"body" "Average"}})
             {:status 204, :headers {}, :body nil}))
      (is (= (jdbc/query db ["SELECT * FROM comments WHERE id = ?" 1])
             [{:id 1, :post_id 1, :body "Average"}])))

    (testing "update of invalid ID"
      (is (= (handler {:route-params {:id "3"}, :form-params {"body" "Average"}})
             {:status 404, :headers {}, :body {:error :not-found}})))))

(deftest insert-test
  (testing "with location"
    (let [db      (create-database)
          config  {::sql/insert
                   {:db       (db/->Boundary db)
                    :request  '{{:keys [pid]} :route-params, {:strs [body]} :form-params}
                    :sql      '["INSERT INTO comments (post_id, body) VALUES (?, ?)" pid body]
                    :location "/posts{/pid}/comments{/last_insert_rowid}"}}
          handler (::sql/insert (ig/init config))]
      (is (= (handler {:route-params {:pid "1"}, :form-params {"body" "New comment"}})
             {:status 201, :headers {"Location" "/posts/1/comments/3"}, :body nil}))
      (is (= (jdbc/query db ["SELECT * FROM comments WHERE id = ?" 3])
             [{:id 3, :post_id 1, :body "New comment"}]))))

  (testing "without location"
    (let [db     (create-database)
          config {::sql/insert
                  {:db      (db/->Boundary db)
                   :request '{{:keys [pid]} :route-params, {:strs [body]} :form-params}
                   :sql     '["INSERT INTO comments (post_id, body) VALUES (?, ?)" pid body]}}
          handler (::sql/insert (ig/init config))]
      (is (= (handler {:route-params {:pid "1"}, :form-params {"body" "New comment"}})
             {:status 201, :headers {}, :body nil}))
      (is (= (jdbc/query db ["SELECT * FROM comments WHERE id = ?" 3])
             [{:id 3, :post_id 1, :body "New comment"}])))))
