(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn filter-tags
  []
  (db/query! {:select [:*]
              :from [:tag]}))
