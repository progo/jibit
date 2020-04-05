(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn tag-ids-for-photo
  [photo]
  (mapv :photo_tag/tag_id
        (db/query! {:select [:tag_id]
                    :from [:photo_tag]
                    :where [:= :photo_id (:photo/id photo)]})))

(defn filter-tags
  []
  (db/query! {:select [:*]
              :from [:tag]}))
