(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn -add-tags-for-photos
  [tag-id photo-ids]
  (db/query! {:insert-or-replace :photo_tag
              :columns [:photo_id :tag_id]
              :values (for [pid photo-ids]
                        [pid tag-id])}))

(defn -remove-tags-from-photos
  [tag-id photo-ids]
  (db/query! {:delete-from :photo_tag
              :where [:and
                      [:= :tag_id tag-id]
                      [:in :photo_id photo-ids]]}))

(defn -toggle-tag-from-photos
  "If the set has one photo that has been tagged with the tag, add it
  to every photo. If every photo has the tag, remove it from all. "
  [tag-id photo-ids]
  (let [num (db/query-count! {:select [:%count]
                              :from [:photo_tag]
                              :where [:and
                                      [:= :tag_id tag-id]
                                      [:in :photo_id photo-ids]]})]
  (cond
    ;; Every photo in the set has been tagged with this tag
    (= num (count photo-ids))
    (-remove-tags-from-photos tag-id photo-ids)

    ;; At least one is tagged
    (pos? num)
    (-add-tags-for-photos tag-id photo-ids)

    ;; No tags
    (zero? num)
    (-add-tags-for-photos tag-id photo-ids)
    )))

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
  (case mode
    :add    (-add-tags-for-photos     tag-id photo-ids)
    :remove (-remove-tags-from-photos tag-id photo-ids)
    :toggle (-toggle-tag-from-photos  tag-id photo-ids)))

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
