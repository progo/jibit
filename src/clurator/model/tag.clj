(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn set-tag-for-photos
  [tag-id photo-ids]
  (db/query! {:insert-into :photo_tag
              :columns [:photo_id :tag_id]
              :values (for [pid photo-ids]
                        [pid tag-id])
              }))

(defn get-tag-ids-for-photo
  [photo]
  (mapv :photo_tag/tag_id
        (db/query! {:select [:tag_id]
                    :from [:photo_tag]
                    :where [:= :photo_id (:photo/id photo)]})))

(defn filter-tags
  []
  (db/query! {:select [:*]
              :from [:tag]}))
