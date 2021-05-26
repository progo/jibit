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

(defn match-tags-between-photos
  "Match tags between photos so that every photo on the seq has the same
  tags. Yes, the doseq approach is not the most elegant way but it
  works fine for the originally intended usecase of matching 2-3
  photos at a time. Return the set of tags."
  [photo-ids]
  (let [tags (db/query! {:select [:tag_id]
                         :from [:photo_tag]
                         :where [:in :photo_id photo-ids]})
        tag-ids (into #{} (map :tag_id tags))]
    (doseq [tag-id tag-ids]
      (-add-tags-for-photos tag-id photo-ids))
    tag-ids))

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
  (mapv :tag_id
        (db/query! {:select [:tag_id]
                    :from [:photo_tag]
                    :where [:= :photo_id (:id photo)]})))

(defn tag-children
  "Return basic things about a tag's children."
  [tag-id]
  (db/query! {:select [:id :name]
              :from [:tag]
              :where [:= :parent_id tag-id]}))

(defn tagged-photos#
  "Return a count of tagged photos using this tag-id."
  [tag-id]
  (db/query-count! {:select [:%count]
                    :from [:photo_tag]
                    :where [:= :tag_id tag-id]}))

(defn tag-parent-pairs
  "Get from DB a seq of tuples [tag-id tags-parent-id] called tp-pairs."
  []
  (->> {:select [:id :parent_id]
        :from [:tag]}
       db/query!
       (map (juxt :id :parent_id))))

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
    (:style_color tag) (:style_color tag)
    ;; Otherwise check if parent possibly has color, it will be
    ;; inherited.
    (:parent_id tag) (recur (tag-db (:parent_id tag)) tag-db)
    :t nil))

(defn nested-label-tag
  "Give tag a nested name label like [Supertag1 Supertag2 Subtag]. It's
  a vector."
  [tag tag-db]
  (if-let [parent-tag (tag-db (:parent_id tag))]
    ;; Tag has a parent...
    (conj (nested-label-tag parent-tag tag-db)
          (:name tag))

    ;; ...or not.
    [(:name tag)]))

(defn query-tags
  "Get all tags from DB, processed and ordered."
  []
  (let [all-tags (-> {:select [:*]
                      :from [:tag]}
                     (db/query!))
        tag-db (into {} (map (juxt :id identity) all-tags))]
    (->> all-tags
         (map #(assoc %
                      :computed_color (find-color-for-tag % tag-db)
                      :nested_label (nested-label-tag % tag-db)))
         (sort-by #(->> %
                        :nested_label
                        (join "/"))))))

(defn create-edit-tag
  [{id :id
    name :name
    desc :description
    parent :parent_id
    color :style_color}]
  (let [value-map {:id id
                   :name name
                   :description desc
                   :parent_id parent
                   :style_color color}]
    (cond
      ;; We are trying to place a tag under its own descendant, which
      ;; is no bueno.
      (and (not (nil? id))
           (not (nil? parent))
           ((tag-descendants id) parent))
      nil

      ;; New tag
      (nil? id)
      (db/query! {:insert-into :tag
                  :values [value-map]})

      ;; Update old one
      :else
      (db/query! {:update :tag
                  :set value-map
                  :where [:= :id id]}))))

(defn delete-tag
  [tag-id]
  (db/query! {:delete-from :tag
              :where [:= :id tag-id]}))

(defn delete-tag*
  "Deletes a tag like `delete-tag` does but this will clean any model
  relations beforehands:

  - photo_tag entries are removed
  - subtags are lifted one level up (so tag hierarchy of 'VEHICES >
    CARS > SUBARU' becomes 'VEHICES > SUBARU' if CARS deleted)
  "
  [tag-id]
  (let [parent-id (-> (db/query-1! {:select [:parent_id]
                                    :from [:tag]
                                    :where [:= :id tag-id]})
                      :parent_id)]
    (db/query! {:update :tag
                :set {:parent_id parent-id}
                :where [:= :parent_id tag-id]}))
  (db/query! {:delete-from :photo_tag
              :where [:= :tag_id tag-id]})
  (delete-tag tag-id))
