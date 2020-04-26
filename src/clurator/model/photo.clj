(ns clurator.model.photo
  "Photo model."
  (:require [clurator.db :as db]
            [clurator.model.tag :as tag]
            [taoensso.timbre :as timbre :refer [spy debug]]))

(defn get-by-uuid
  [uuid]
  (db/query-1! {:select [:*]
                :from [:photo]
                :where [:= :uuid uuid]}))

(defn build-imported-criteria
  [imported-begin imported-end]
  (cond
    (and (not imported-begin) (not imported-end)) nil
    (and (not imported-begin) imported-end)       [:< :photo.import_ts imported-end]
    (and imported-begin       (not imported-end)) [:> :photo.import_ts imported-begin]
    :else [:between :photo.import_ts imported-begin imported-end]))


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
  "Given a collection of `tags` (ids) build a query condition to filter
  photos that are somehow tagged with these tags. `union?` denotes if
  we get a union of tagged photos -- intersection if false.
  `zero-tags?` is a bolted on special case when we want to query
  photos that have no tags. In case it's true, we don't bother with
  further conditions."
  [tags union? zero-tags?]
  (let [tags (seq tags)]
    (cond
      ;; No tags first
      zero-tags?
      [:not-in :photo.id {:select [:photo_id]
                          :from [:photo_tag]}]

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

(defn build-rated-criterion
  [only-unrated?]
  (if only-unrated?
    [:= :photo.rating nil]
    true))

(defn build-cooked-criterion
  [only-uncooked?]
  (if only-uncooked?
    [:= :photo.is_raw false]
    true))

(defn build-title-criterion
  [only-untitled?]
  (if only-untitled?
    [:= :photo.title nil]
    true))

(defn fetch-tags
  "Run an extra query to get tag IDs for given photo."
  [photo]
  (assoc photo :tagged/ids (tag/get-tag-ids-for-photo photo)))

(defn massage-content
  "Turn some integers into booleans etc"
  [photo]
  (update photo :photo/is_raw #(if (zero? %) false true)))

(defn filter-photos
  "Take user's input (parsed in some way) and build/execute a SQL query."
  [{:keys
    [order-by
     offset
     limit

     taken-begin
     taken-end
     imported-begin
     imported-end

     camera-make
     camera-model

     tags
     tags-union?

     show-only-untitled?
     show-only-untagged?
     show-only-unrated?
     show-only-uncooked?]
    :or {order-by :taken_ts
         offset 0
         limit 1234}}]

  (-> {:select [:photo.* :camera.* :lens.*]
       :from [:photo]
       :left-join [:camera [:= :camera.id :photo.camera_id]
                   :lens [:= :lens.id :photo.lens_id]]
       :where [:and true
               (build-taken-criteria taken-begin taken-end)
               (build-imported-criteria imported-begin imported-end)
               (build-make-model-criteria camera-make camera-model)
               (build-tags-criteria tags tags-union? show-only-untagged?)
               (build-rated-criterion show-only-unrated?)
               (build-cooked-criterion show-only-uncooked?)
               (build-title-criterion show-only-untitled?)]
       :order-by [order-by]
       :offset offset
       :limit limit}
      db/query!
      (#(map fetch-tags %))
      (#(map massage-content %))))
