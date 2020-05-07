(ns clurator.view.gear
  (:require [clurator.db :as db]
            [next.jdbc.result-set :as rs]
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.view.filtering :as filtering]))

(defn unqualify [kw]
  (keyword (name kw)))

(defn list-gear
  [req]
  {:resp {:gear (db/query! {:select [:*] :from [:vw_gear]})}})

(defn update-gear
  [req]
  (debug (filtering/read-edn req))
  {:resp :ok})
