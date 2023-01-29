(ns clurator.model.photo
  "Photo model."
  (:require [clurator.db :as db]
            [clurator.model.tag :as tag]
            [taoensso.timbre :as timbre :refer [spy debug]]))

(defn update-photo
  "Update photo's attributes (map) by photo ID."
  [id attr-map]
  (db/query! {:update :photo
              :set attr-map
              :where [:= :id id]}))

(defn get-by-uuid
  [uuid]
  (db/query-1! {:select [:*]
                :from [:photo]
                :where [:= :uuid uuid]}))

(defn get-photos-by-id
  "Get corresponding photos for given collection of IDs."
  [ids]
  (db/query! {:select [:*]
              :from [:photo]
              :where [:in :id ids]}))

(defn build-date-range-criteria
  "For a photo table column `column` and dt values `begin` and `end`
  build a suitable filter predicate."
  [column begin end]
  ;; SQL: date(col) truncates hours,minutes,seconds off
  (let [col (keyword (str "%date.photo." (name column)))]
    (cond
      (and (not begin) (not end)) nil
      (and (not begin) end)       [:<= col end]
      (and begin       (not end)) [:>= col begin]
      :else [:between col begin end])))

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
  (update photo :is_raw #(case %
                           0 false
                           1 true)))

(defn build-photo-filter
  "Build a map of criteria to  query photos from db."
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
         limit 72}}]
  {:select [:photo.*]
   :from [:photo]
   :left-join [[:gear :camera] [:= :camera.id :photo.camera_id]
               [:gear :lens]   [:= :lens.id   :photo.lens_id]]
   :where [:and true
           (build-date-range-criteria :taken_ts taken-begin taken-end)
           (build-date-range-criteria :import_ts imported-begin imported-end)
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
   :limit limit})

(defn filter-photos
  "Take user's input (parsed in some way) and build/execute a SQL query.
  Look for keys under `build-photo-filter'."
  [criteria]
  (let [crit (build-photo-filter criteria)
        offset (:offset criteria)
        total-count (-> crit
                        (merge {:select [:%count]
                                :offset 0
                                :limit -1})
                        (db/query-count!))
        ;; reset offset if it doesn't make sense.
        ;; (FIXME) might be we want to do this on the client
        offset (if (> offset total-count)
                 0
                 offset)
        photos (-> (assoc crit :offset offset)
                   db/query!
                   (#(map fetch-tags %))
                   (#(map massage-content %)))]
    {:photos photos
     :meta {:total-count total-count
            :offset offset
            :limit (crit :limit)
            }}))
