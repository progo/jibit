(ns clurator.db
  "Database interface"
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as s]
            [clurator.db-schema :refer [update-schema!]]
            clurator.settings))

(declare db)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn query!
  "Make a RO query. Take a map HoneySQL understands."
  [query-map]
  (jdbc/execute! db (sql/format query-map)))

(defn nilify
  "Turn empty strings into nils"
  [x]
  (if (empty? x) nil x))

(defn build-taken-criteria
  [taken-begin taken-end]
  (cond
    (and (not taken-begin) (not taken-end)) nil
    (and (not taken-begin) taken-end)       [:< :photo.taken_ts taken-end]
    (and taken-begin       (not taken-end)) [:> :photo.taken_ts taken-begin]
    :else [:between :photo.taken_ts taken-begin taken-end]))

(defn filter-photos
  ""
  [{order-by :order-by
    offset :offset
    limit :limit
    taken-begin :taken-begin
    taken-end :taken-end

    :or {order-by :taken_ts
         offset 0
         limit 10}}]
  (let [taken-crit (build-taken-criteria (nilify taken-begin) (nilify taken-end))]
    (query! {:select [:photo.* :camera.* :lens.*]
             :from [:photo]
             :join [:camera [:= :camera.id :photo.camera_id]
                    :lens [:= :lens.id :photo.lens_id]]
             :where [:and true taken-crit]
             :order-by [order-by]
             :offset offset
             :limit limit})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def photo-clj->db-key
  "Database keys map to what processed keys."
  {
   :focal_length        :exif/FocalLength
   :focal_length_35     :exif/FocalLength35efl
   :aperture            :exif/Aperture
   :exposure_comp       :exif/ExposureCompensation
   :iso                 :exif/ISO
   :shutter_speed       :exif/ExposureTime
   :light_value         :exif/LightValue
   :rating              nil
   :orig_rating         :exif/Rating
   :taken_ts            :exif/CreateDate
   :process_ts          :meta/development-date
   :import_ts           :meta/import-date
   :width               :exif/ImageWidth
   :height              :exif/ImageHeight
   :megapixels          :exif/Megapixels
   :title               nil
   :subtitle            nil
   :description         nil
   :notes               nil
   :original_file       :meta/original-filename
   :original_raw        :meta/original-raw
   :original_dir        :meta/original-dir
   :uuid                :meta/uuid
   :storage_filename    :meta/storage})

(defn build-sql-keys [e]
  (->>
   (for [[sql-key foo-key] photo-clj->db-key
         :when foo-key]
     [sql-key (get e foo-key)])
   (into {})))

;;;;; Gear is camera or lens

(defn lookup-gear
  [gear make model]
  (query! {:select [:id]
           :from [gear]
           :where [:and
                   [:= :exif_make make]
                   [:= :exif_model model]]}))

(defn create-gear
  [gear make model]
  (query! {:insert-into gear
           :values [{:exif_make make
                     :exif_model model}]}))

(defn get-or-create-gear
  "Get or create gear with these specs. Return ID as integer. If multiple found,
  we can't do anything and return nil."
  [gear make model]
  (let [found (lookup-gear gear make model)
        cnt (count found)]
    (cond
      (every? nil? [make model]) nil
      (= 1 cnt) (-> found
                    first
                    first
                    second)
      (= 0 cnt) (do
                  (create-gear gear make model)
                  (get-or-create-gear gear make model))
      :else nil)))

(defn add-entry!
  "Take an Emap and insert it into DB normalised."
  [e]
  (let [camera-id (get-or-create-gear :camera (:exif/Make e) (:exif/Model e))
        lens-id (get-or-create-gear :lens (:exif/LensMake e) (:exif/LensModel e))]
    (jdbc/execute!
     db
     (-> (s/insert-into :photo)
         (s/values [(merge (build-sql-keys e)
                           {:lens_id lens-id
                            :camera_id camera-id})])
         sql/format))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The rest of the file will be the versioned statements in var `schema-sql`.

;; Create the connection and update the file if needed.
(def db (jdbc/get-datasource {:dbtype "sqlite"
                              :dbname clurator.settings/database-file}))

(update-schema! db)
