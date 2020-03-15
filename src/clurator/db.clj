(ns clurator.db
  "Database interface"
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as s]
            clurator.settings))

(def db (jdbc/get-datasource {:dbtype "sqlite" :dbname clurator.settings/database-file}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn database-version!
  "Get the version number from SQlite database."
  [db]
  (->
   (jdbc/execute-one! db ["pragma user_version;"])
   :user_version))

(defn set-database-version!
  "Set the version number for SQlite database."
  [db version]
  {:pre [(Integer. version)]}
  (jdbc/execute-one! db [(str "pragma user_version = " (Integer. version))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def photo-clj->db-key
  "Database keys map to what processed keys."
  {
   :lens_make           :exif/LensMake
   :lens_model          :exif/LensModel
   :lens_min_fl         nil
   :lens_max_fl         nil
   :focal_length        :exif/FocalLength
   :focal_length_35     :exif/FocalLength35efl
   :aperture            :exif/Aperture
   :exposure_comp       :exif/ExposureCompensation
   :camera_make         :exif/Make
   :camera_model        :exif/Model
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
   :storage_filename    :meta/storage
   })

(defn build-sql-keys [e]
  (->>
   (for [[sql-key foo-key] photo-clj->db-key
         :when foo-key]
     [sql-key (get e foo-key)])
   (into {})))

(defn add-entry!
  [e]
  (jdbc/execute!
   db
   (-> (s/insert-into :photo)
       (s/values [(build-sql-keys e)])
       sql/format)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn query!
  "Make a RO query. Take a map HoneySQL understands."
  [query-map]
  (jdbc/execute! db (sql/format query-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare schema-sql)

(defn update-schema!
  [db]
  (let [current-version (max 0 (database-version! db))
        latest-version (apply max (keys schema-sql))
        versions (range (inc current-version) (inc latest-version))]
    (doseq [v versions]
      (println "Updating db from v" (dec v) "to v" v)
      (doseq [stmt (schema-sql v)]
        (println stmt)
        (jdbc/execute! db [stmt]))
      (set-database-version! db v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The rest of the file will be the versioned statements in var `schema-sql`.

(def schema-sql
  {1 ["create table photo
      ( id              integer primary key autoincrement
      , lens_make       text
      , lens_model      text
      , lens_min_fl     real
      , lens_max_fl     real
      , focal_length    real
      , focal_length_35 real
      , aperture        real
      , exposure_comp   real
      , camera_make     text
      , camera_model    text
      , iso             integer
      , shutter_speed   real
      , light_value     number
      , rating          integer
      , orig_rating     integer
      , taken_ts        date
      , process_ts      date
      , import_ts       date
      , width           integer
      , height          integer
      , megapixels      real
      , title           text
      , subtitle        text
      , description     text
      , notes           text
      , original_file   text
      , original_raw    text
      , original_dir    text
      , uuid            text
      , storage_filename text
      );"
      "create index ix_photo_rating on photo(rating);"
      "create index ix_photo_camera on photo(camera_make);"
      "create index ix_photo_cameramodel on photo(camera_model);"
      "create index ix_photo_taken on photo(taken_ts);"
      "create index ix_photo_process on photo(process_ts);"
      "create index ix_photo_import on photo(import_ts);"
      "create index ix_photo_iso on photo(iso);"
      "create index ix_photo_uuid on photo(uuid);"
      "create index ix_photo_fl on photo(focal_length);"
      "create index ix_photo_fl35 on photo(focal_length_35);"
      "create index ix_photo_ec on photo(exposure_comp);"
      "create index ix_photo_aperture on photo(aperture);"
      "create index ix_photo_lensmake on photo(lens_make);"
      "create index ix_photo_lensmodel on photo(lens_model);"
      "create index ix_photo_shutter on photo(shutter_speed);"
      "create index ix_photo_megapixels on photo(megapixels);"
      "create index ix_photo_width on photo(width);"
      "create index ix_photo_height on photo(height);"
      "create index ix_photo_lv on photo(light_value);"
      "create index ix_photo_title on photo(title);"
      "create index ix_photo_subtitle on photo(subtitle);"
      "create index ix_photo_desc on photo(description);"
      "create index ix_photo_notes on photo(notes);"
      "create index ix_photo_original_file on photo(original_file);"
      "create index ix_photo_original_raw on photo(original_raw);"
      "create index ix_photo_original_dir on photo(original_dir);"
      ]
   })

