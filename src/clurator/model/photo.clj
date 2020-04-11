(ns clurator.model.photo
  "Photo model."
  (:require [clurator.db :as db]
            [clurator.model.tag :as tag]
            [taoensso.timbre :as timbre :refer [spy debug]]))

(defn build-taken-criteria
  [taken-begin taken-end]
  (cond
    (and (not taken-begin) (not taken-end)) nil
    (and (not taken-begin) taken-end)       [:< :photo.taken_ts taken-end]
    (and taken-begin       (not taken-end)) [:> :photo.taken_ts taken-begin]
    :else [:between :photo.taken_ts taken-begin taken-end]))

(defn build-make-model-criteria
  [make model]
  (let [a (if make
            [:like :camera.exif_make (str \% make \%)])
        b (if model
            [:like :camera.exif_model (str \% model \%)])
        a+b (cond
              (and a b) [:and a b]
              a a
              b b
              :else nil)]
    a+b))

(defn build-tags-criteria
  [tags union?]
  (let [tags (seq tags)]
    (cond
      ;; Union
      (and tags union?)
      [:in :photo.id {:select [:photo_id]
                      :from [:photo_tag]
                      :where [:in :tag_id tags]}]

      ;; Intersection
      (and tags (not union?))
      [:exists {:select [nil]
                :from [:photo_tag]
                :join [:tag [:= :photo_tag.tag_id :tag.id]]
                :where [:and
                        [:in :tag.id tags]
                        [:= :photo_tag.photo_id :photo.id]]
                :group-by [:photo_tag.photo_id]
                :having [:= (count tags) [:count :tag.id]]}])))

(defn fetch-tags
  [photos]
  (for [p photos]
    (assoc p :tagged/ids (tag/get-tag-ids-for-photo p))))

(defn filter-photos
  "Take user's input (parsed in some way) and build/execute a SQL query."
  [{order-by :order-by
    offset :offset
    limit :limit
    taken-begin :taken-begin
    taken-end :taken-end
    camera-make :camera-make
    camera-model :camera-model

    tags :tags
    tags-union :tags-union

    :or {order-by :taken_ts
         offset 0
         limit 1234}}]
  (let [taken-crit (build-taken-criteria taken-begin taken-end)
        make-model-crit (build-make-model-criteria camera-make camera-model)
        tags-crit (build-tags-criteria tags tags-union)]
    (-> {:select [:photo.* :camera.* :lens.*]
         :from [:photo]
         :left-join [:camera [:= :camera.id :photo.camera_id]
                     :lens [:= :lens.id :photo.lens_id]]
         :where [:and true
                 taken-crit
                 make-model-crit
                 tags-crit]
         :order-by [order-by]
         :offset offset
         :limit limit}
        db/query!
        fetch-tags)))
