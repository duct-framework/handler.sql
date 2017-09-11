(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [duct.database.sql :as sql]
            [integrant.core :as ig]
            [ring.util.response :as resp]))

(defprotocol RelationalDatabase
  (query    [db sql])
  (execute! [db sql]))

(extend-protocol RelationalDatabase
  duct.database.sql.Boundary
  (query    [{:keys [spec]} sql] (jdbc/query spec sql))
  (execute! [{:keys [spec]} sql] (jdbc/execute! spec sql)))

(defmethod ig/init-key ::select [_ {:keys [db request query] :or {request '_}}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (resp/response (query db# ~query)))))]
    (f db)))

(defmethod ig/init-key ::select-one [_ {:keys [db request query] :or {request '_}}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (if-let [result# (first (query db# ~query))]
                       (resp/response result#)
                       (resp/not-found {:error :not-found})))))]
    (f db)))

(defmethod ig/init-key ::execute [_ {:keys [db request query] :or {request '_}}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (execute! db# ~query)
                     {:status 204, :headers {}, :body nil})))]
    (f db)))
