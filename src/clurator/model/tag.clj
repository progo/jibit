(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn filter-tags
  []
  (mapv :tag/name (db/query! {:select [:name]
                              :from [:tag]})))
