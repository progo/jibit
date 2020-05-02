(ns clurator.view.gear
  (:require [clurator.db :as db]))

(defn list-gear
  [req]
  {:resp
   {:camera (db/query! {:select [:*]
                        :from [:camera]})
    :lens (db/query! {:select [:*]
                      :from [:lens]})}})
