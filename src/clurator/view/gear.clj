(ns clurator.view.gear
  (:require [clurator.db :as db]
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.view.filtering :as filtering]))

(defn list-gear
  [req]
  {:resp
   {:camera (db/query! {:select [:*]
                        :from [:camera]})
    :lens (db/query! {:select [:*]
                      :from [:lens]})}})

(defn update-gear
  [req]
  (debug (filtering/read-edn req))
  {:resp :ok})
