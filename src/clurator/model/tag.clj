(ns clurator.model.tag
  "Tag model."
  (:require [clurator.db :as db]
            [clojure.string :refer [join]]
            [clojure.set :as sset]
            [taoensso.timbre :as timbre :refer [debug spy]]))

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

(defn tag-parent-pairs
  "Get from DB a seq of tuples [tag-id tags-parent-id] called tp-pairs."
  []
  (->> {:select [:id :parent_id]
        :from [:tag]}
       db/query!
       (map (juxt :tag/id :tag/parent_id))))

(defn ->parent-children-map
  "From a tp-pair produce an inverse map of {tag: its-children}. All
  top-level tags can also be found under key `nil`."
  [tp-pairs]
  (->>
   (group-by second tp-pairs)
   (map (fn [[k v]]
          [k (into #{} (mapv first v))]))
   (into {})))

(defn tag-descendants
  "Find all tags that have a tag ID'd `tag-id` as parents or great
  parents. Does a live query from database for data. Returns a set of
  IDs."
  [tag-id]
  ;; More or less a standard breadth-first search to avoid non-TCO
  ;; recursion.
  (let [pc (->parent-children-map (tag-parent-pairs))]
    (loop [tag-ids [tag-id]
           acc #{}]
      (if (seq tag-ids)
        (let [children (get pc (first tag-ids))]
          (recur (concat (rest tag-ids) children)
                 (sset/union acc children)))
        acc))))

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
  "Give tag a nested name label like [Supertag1 Supertag2 Subtag]. It's
  a vector."
  [tag tag-db]
  (if-let [parent-tag (tag-db (:tag/parent_id tag))]
    ;; Tag has a parent...
    (conj (nested-label-tag parent-tag tag-db)
          (:tag/name tag))

    ;; ...or not.
    [(:tag/name tag)]))

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
         (sort-by #(->> %
                        :tag/nested_label
                        (join "/"))))))

(defn create-edit-tag
  [{id :tag/id
    name :tag/name
    desc :tag/description
    parent :tag/parent_id
    color :tag/style_color}]
  (if (and (not (nil? id))
           (not (nil? parent))
           ((tag-descendants id) parent))
    ;; We are trying to place a tag under its own descendant, which is
    ;; no bueno.
    nil
    (db/query! {:insert-or-replace :tag
                :columns [:id :name :description :parent_id :style_color]
                :values [[id name desc parent color]]})))
