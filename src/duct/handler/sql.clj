(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [integrant.core :as ig]
            [ring.util.response :as resp]))

(defmethod ig/init-key ::select [_ {:keys [db request query]}]
  (let [f (eval `(fn [{spec# :spec}]
                   (fn [~request]
                     (resp/response (jdbc/query spec# ~query)))))]
    (f db)))

(defmethod ig/init-key ::select-one [_ {:keys [db request query]}]
  (let [f (eval `(fn [{spec# :spec}]
                   (fn [~request]
                     (if-let [result# (first (jdbc/query spec# ~query))]
                       (resp/response result#)
                       (resp/not-found {:error :not-found})))))]
    (f db)))
