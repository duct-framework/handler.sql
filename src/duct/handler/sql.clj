(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [duct.database.sql :as sql]
            [integrant.core :as ig]
            [ring.util.response :as resp]
            [uritemplate-clj.core :as ut]))

(defprotocol RelationalDatabase
  (query [db query]))

(extend-protocol RelationalDatabase
  duct.database.sql.Boundary
  (query [{:keys [spec]} query] (jdbc/query spec query)))

(defn- uri-template [template values]
  (ut/uritemplate template (walk/stringify-keys values)))

(defn- assoc-hrefs [result hrefs]
  (reduce-kv #(assoc %1 %2 (uri-template %3 result)) result hrefs))

(defn- remove-keys [result keys]
  (apply dissoc result keys))

(defn transform-result [result {:keys [hrefs remove rename]}]
  (-> result
      (assoc-hrefs hrefs)
      (remove-keys remove)
      (set/rename-keys rename)))

(defmethod ig/init-key ::select
  [_ {:as opts :keys [db request query] :or {request '_}}]
  (let [opts (select-keys opts [:hrefs :remove :rename])
        f    (eval `(fn [db#]
                      (fn [~request]
                        (->> (query db# ~query)
                             (map #(transform-result % ~opts))
                             (resp/response)))))]
    (f db)))

(defmethod ig/init-key ::select-one
  [_ {:as opts :keys [db request query] :or {request '_}}]
  (let [opts (select-keys opts [:hrefs :remove :rename])
        f    (eval `(fn [db#]
                      (fn [~request]
                        (if-let [result# (first (query db# ~query))]
                          (resp/response (transform-result result# ~opts))
                          (resp/not-found {:error :not-found})))))]
    (f db)))
