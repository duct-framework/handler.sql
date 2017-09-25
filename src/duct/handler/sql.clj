(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [duct.database.sql :as sql]
            [integrant.core :as ig]
            [ring.util.response :as resp]))

(defprotocol RelationalDatabase
  (query [db query]))

(extend-protocol RelationalDatabase
  duct.database.sql.Boundary
  (query [{:keys [spec]} query] (jdbc/query spec query)))

(defmethod ig/init-key ::select [_ {:keys [db request query rename] :or {request '_}}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (->> (query db# ~query)
                          (map #(set/rename-keys % ~rename))
                          (resp/response)))))]
    (f db)))

(defmethod ig/init-key ::select-one [_ {:keys [db request query rename] :or {request '_}}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (if-let [result# (first (query db# ~query))]
                       (resp/response (set/rename-keys result# ~rename))
                       (resp/not-found {:error :not-found})))))]
    (f db)))
