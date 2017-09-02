(ns duct.handler.sql
  (:require [clojure.java.jdbc :as jdbc]
            [integrant.core :as ig]
            [ring.util.response :as resp]))

(defmethod ig/init-key ::get-select [_ {:keys [db request query]}]
  (let [f (eval `(fn [db#]
                   (fn [~request]
                     (resp/response (jdbc/query db# ~query)))))]
    (f db)))
