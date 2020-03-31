(ns clurator.model.photo
  "Photo model."
  (:require [clurator.db :as db]))

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

(defn filter-photos
  ""
  [{order-by :order-by
    offset :offset
    limit :limit
    taken-begin :taken-begin
    taken-end :taken-end
    camera-make :camera-make
    camera-model :camera-model

    :or {order-by :taken_ts
         offset 0
         limit 1234}}]
  (let [taken-crit (build-taken-criteria taken-begin taken-end)
        make-model-crit (build-make-model-criteria camera-make camera-model)]
    (db/query! {:select [:photo.* :camera.* :lens.*]
                :from [:photo]
                :left-join [:camera [:= :camera.id :photo.camera_id]
                            :lens [:= :lens.id :photo.lens_id]]
                :where [:and
                        true
                        taken-crit
                        make-model-crit]
                :order-by [order-by]
                :offset offset
                :limit limit})))
