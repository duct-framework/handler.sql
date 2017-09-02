(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [integrant.core :as ig]
            [ring.util.response :as resp]))

(defmethod ig/init-key ::get-select [_ {:keys [db request query]}]
  (let [f (eval `(fn [{spec# :spec}]
                   (fn [~request]
                     (resp/response (jdbc/query spec# ~query)))))]
    (f db)))
