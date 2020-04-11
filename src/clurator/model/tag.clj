(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn set-tag-for-photos
  "Apply given tag to all photos denoted by `photo-ids`. Mode will be
  one of the following.

  - :add    : add the tag to every photo
  - :remove : remove the tag from every photo
  - :toggle : If the set has one photo that has been tagged with the tag,
              add it to every photo. If every photo has the tag,
              remove it from all.
  "
  [tag-id photo-ids mode]
  (db/query! {:insert-or-replace :photo_tag
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
