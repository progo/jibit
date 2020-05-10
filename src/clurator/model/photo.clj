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

(defn build-gear-criteria
  "Gear-type is :camera or :lens"
  [gear-type make+model]
  (when make+model
    [:or
     [:like
      (keyword (str (name gear-type) ".exif_make"))
      (str \% make+model \%)]
     [:like
      (keyword (str (name gear-type) ".exif_model"))
      (str \% make+model \%)]]))

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
  (update photo :is_raw (complement nil?)))

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

     ;; string search gear
     camera
     lens
     ;; exact search by id, trumps string search if not nil
     camera-id
     lens-id

     tags
     tags-union?

     show-only-untitled?
     show-only-untagged?
     show-only-unrated?
     show-only-uncooked?]
    :or {order-by :taken_ts
         offset 0
         limit 1234}}]

  (-> {:select [:photo.*
                [:camera.exif_make      :camera_exif_make]
                [:camera.exif_model     :camera_exif_model]
                [:camera.user_label     :camera_user_label]
                [:lens.exif_make        :lens_exif_make]
                [:lens.exif_model       :lens_exif_model]
                [:lens.user_label       :lens_user_label]]
       :from [:photo]
       :left-join [[:gear :camera] [:= :camera.id :photo.camera_id]
                   [:gear :lens]   [:= :lens.id   :photo.lens_id]]
       :where [:and true
               (build-taken-criteria taken-begin taken-end)
               (build-imported-criteria imported-begin imported-end)
               (if camera-id
                 [:= :photo.camera_id camera-id]
                 (build-gear-criteria :camera camera))
               (if lens-id
                 [:= :photo.lens_id lens-id]
                 (build-gear-criteria :lens lens))
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
