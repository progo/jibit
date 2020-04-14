(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]
            [taoensso.timbre :as timbre :refer [debug spy]]))


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

(defn find-color-for-tag
  "Find color for tag, from tag or its parents."
  [tag tag-db]
  (cond
    ;; If tag has own color, use it.
    (:tag/style_color tag) (:tag/style_color tag)
    ;; Otherwise check if parent possibly has color, it will be
    ;; inherited.
    (:tag/parent_id tag) (recur (tag-db (:tag/parent_id tag)) tag-db)
    :t nil))

(defn nested-label-tag
  "Give tag a nested name label like 'Supertag1/Supertag2/Subtag'. We
  use this for sorting only..."
  [tag tag-db]
  (if-let [parent-tag (tag-db (:tag/parent_id tag))]
    ;; Tag has a parent...
    (str (nested-label-tag parent-tag tag-db)
         "/"
         (:tag/name tag))

    ;; ...or not.
    (:tag/name tag)
    ))

(defn query-tags
  "Get all tags from DB, processed and ordered."
  []
  (let [all-tags (-> {:select [:*]
                      :from [:tag]}
                     (db/query!))
        tag-db (into {} (map (juxt :tag/id identity) all-tags))]
    (->> all-tags
         (map #(assoc %
                      :tag/computed_color (find-color-for-tag % tag-db)
                      :tag/nested_label (nested-label-tag % tag-db)))
         (sort-by :tag/nested_label))))
