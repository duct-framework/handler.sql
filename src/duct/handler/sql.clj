(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [duct.database.sql :as sql]
            [integrant.core :as ig]
            [medley.core :as m]
            [ring.util.response :as resp]
            [uritemplate-clj.core :as ut]))

(defprotocol RelationalDatabase
  (query    [db sql])
  (execute! [db sql])
  (insert!  [db sql]))

(extend-protocol RelationalDatabase
  duct.database.sql.Boundary
  (query    [{:keys [spec]} sql] (jdbc/query spec sql))
  (execute! [{:keys [spec]} sql] (jdbc/execute! spec sql))
  (insert!  [{:keys [spec]} sql] (jdbc/db-do-prepared-return-keys spec sql)))

(defn- sanitize-keys [result]
  (m/map-keys #(str/replace % #"[^\w]" "") result))

(defn- find-symbols [x]
  (filter symbol? (tree-seq coll? seq x)))

(defn- request-capture-expr [req]
  (->> (find-symbols req)
       (remove #{'_})
       (map (juxt (comp keyword name) identity))
       (into {})))

(defn- uri-template [template values]
  (ut/uritemplate template (sanitize-keys (walk/stringify-keys values))))

(defn- assoc-hrefs [result hrefs request-vars]
  (let [vars (merge request-vars result)]
    (reduce-kv #(assoc %1 %2 (uri-template %3 vars)) result hrefs)))

(defn- remove-keys [result keys]
  (apply dissoc result keys))

(defn- transform-opts-expr [{:keys [request] :as opts}]
  (-> opts
      (select-keys [:hrefs :remove :rename])
      (assoc :request-vars (request-capture-expr opts))))

(defn transform-result [result {:keys [hrefs remove rename request-vars]}]
  (-> result
      (assoc-hrefs hrefs request-vars)
      (remove-keys remove)
      (set/rename-keys rename)))

(defn generated-uri [result {:keys [location request-vars] :as opts}]
  (uri-template location (merge request-vars result)))

(defmethod ig/init-key ::query
  [_ {:as opts :keys [db request sql] :or {request '_}}]
  (let [opts (transform-opts-expr opts)
        f    (eval `(fn [db#]
                      (fn [~request]
                        (->> (query db# ~sql)
                             (map #(transform-result % ~opts))
                             (resp/response)))))]
    (f db)))

(defmethod ig/init-key ::query-one
  [_ {:as opts :keys [db request sql] :or {request '_}}]
  (let [opts (transform-opts-expr opts)
        f    (eval `(fn [db#]
                      (fn [~request]
                        (if-let [result# (first (query db# ~sql))]
                          (resp/response (transform-result result# ~opts))
                          (resp/not-found {:error :not-found})))))]
    (f db)))

(defmethod ig/init-key ::execute
  [_ {:as opts :keys [db request sql] :or {request '_}}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (if (zero? (first (execute! db# ~sql)))
                       (resp/not-found {:error :not-found})
                       {:status 204, :headers {}, :body nil}))))]
    (f db)))

(defmethod ig/init-key ::insert
  [_ {:as opts :keys [db location request sql] :or {request '_}}]
  (let [opts {:location location, :request-vars (request-capture-expr request)}
        f    (eval (if location
                     `(fn [db#]
                        (fn [~request]
                          (-> (insert! db# ~sql)
                              (generated-uri ~opts)
                              (resp/created))))
                     `(fn [db#]
                        (fn [~request]
                          (insert! db# ~sql)
                          {:status 201, :headers {}, :body nil}))))]
    (f db)))
