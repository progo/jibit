(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]))


(defn create-edit-tag
  [{id :tag/id
    name :tag/name
    desc :tag/description
    parent :tag/parent_id
    color :tag/style_color}]
  (db/query! {:insert-or-replace :tag
              :columns [:id :name :description :parent_id :style_color]
              :values [[id name desc parent color]]}))

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

(defn get-tag-by-id
  [tag-id all-tags]
  (->> all-tags
       (filter #(= (:tag/id %) tag-id))
       first))

(defn find-color-for-tag
  "Find color for tag, from tag or its parents."
  [tag all-tags]
  (cond
    ;; If tag has own color, use it.
    (:tag/style_color tag) (:tag/style_color tag)
    ;; Otherwise check if parent possibly has color, it will be
    ;; inherited.
    (:tag/parent_id tag) (find-color-for-tag (get-tag-by-id
                                              (:tag/parent_id tag)
                                              all-tags)
                                             all-tags)
    :t nil))

(defn filter-tags
  "All tags from DB."
  []
  (let [all-tags (-> {:select [:*]
                      :from [:tag]}
                     (db/query!))]
    (map #(assoc % :tag/computed_color (find-color-for-tag % all-tags))
         all-tags)))
