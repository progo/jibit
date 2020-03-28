(ns clurator.db
  "Database interface"
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as s]
            clurator.settings))

(declare db)

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

(defn query!
  "Make a RO query. Take a map HoneySQL understands."
  [query-map]
  (jdbc/execute! db (sql/format query-map)))

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

(def schema-sql
  {1 ["create table camera
      ( id              integer primary key autoincrement
      , exif_make       text
      , exif_model      text
      , user_label      text
      , unique (exif_make, exif_model)
      );"
      "create index ix_camera_make on camera(exif_make);"
      "create index ix_camera_model on camera(exif_model);"

      "create table lens
      ( id              integer primary key autoincrement
      , exif_make       text
      , exif_model      text
      , exif_lens_info  text
      , user_label      text
      , min_fl          real
      , max_fl          real
      , min_apt         real
      , max_apt         real
      , unique (exif_make, exif_model)
      );"
      "create index ix_lens_make on lens(exif_make);"
      "create index ix_lens_model on lens(exif_model);"
      "create index ix_lens_linfo on lens(exif_lens_info);"

      "create table photo
      ( id              integer primary key autoincrement
      , camera_id       integer
      , lens_id         integer
      , focal_length    real
      , focal_length_35 real
      , aperture        real
      , exposure_comp   real
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
      , foreign key (lens_id)   references lens(id)   on delete restrict on update cascade
      , foreign key (camera_id) references camera(id) on delete restrict on update cascade
      );"
      "create index ix_photo_lens_id on photo(lens_id);"
      "create index ix_photo_camera_id on photo(camera_id);"
      "create index ix_photo_rating on photo(rating);"
      "create index ix_photo_taken on photo(taken_ts);"
      "create index ix_photo_process on photo(process_ts);"
      "create index ix_photo_import on photo(import_ts);"
      "create index ix_photo_iso on photo(iso);"
      "create index ix_photo_uuid on photo(uuid);"
      "create index ix_photo_fl on photo(focal_length);"
      "create index ix_photo_fl35 on photo(focal_length_35);"
      "create index ix_photo_ec on photo(exposure_comp);"
      "create index ix_photo_aperture on photo(aperture);"
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

      "CREATE VIEW vw_photo AS SELECT
      c.exif_make || ' ' || c.exif_model AS Camera,
      l.exif_make || ' ' || l.exif_model AS Lens,
      p.focal_length_35 AS FL,
      p.aperture,
      p.iso,
      p.exposure_comp,
      p.shutter_speed,
      p.light_value,
      p.rating,
      p.orig_rating,
      p.taken_ts,
      p.megapixels,
      p.storage_filename
      FROM photo p
      JOIN camera c ON p.camera_id = c.id
      JOIN lens l ON p.lens_id = l.id;"
      ]
   })

;; Create the connection and update the file if needed.

(def db (jdbc/get-datasource {:dbtype "sqlite"
                              :dbname clurator.settings/database-file}))

(defn update-schema!
  [db schema]
  (let [current-version (max 0 (database-version! db))
        latest-version (apply max (keys schema))
        versions (range (inc current-version) (inc latest-version))]
    (doseq [v versions]
      (println "Updating db from v" (dec v) "to v" v)
      (doseq [stmt (schema v)]
        (println stmt)
        (jdbc/execute! db [stmt]))
      (set-database-version! db v))))

(update-schema! db schema-sql)
