(ns clurator.db-schema
  "Maintain the versioned database schema."
  (:require [next.jdbc :as jdbc]))

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

      "create table tag
      ( id                      integer primary key autoincrement
      , name                    text
      , type                    text
      , description             text
      , parent_id               integer
      , style_color             text
      , style_attrs             text
      , public                  integer
      , foreign key (parent_id) references tag(id) on delete restrict on update cascade
      , unique (name)
      );"
      "create index ix_tag_name on tag(name);"
      "create index ix_tag_type on tag(type);"
      "create index ix_tag_parent_id on tag(parent_id);"

      "create table photo_tag
      ( id                      integer primary key autoincrement
      , photo_id                integer not null
      , tag_id                  integer not null
      , orderno                 integer
      , rating                  integer
      , description             text
      , foreign key (photo_id) references photo(id) on delete cascade  on update cascade
      , foreign key (tag_id)   references tag(id)   on delete set null on update cascade
      , unique (photo_id, tag_id)
      );"
      "create index ix_phototag_photo_id on photo_tag (photo_id);"
      "create index ix_phototag_tag_id on photo_tag (tag_id);"

      "CREATE VIEW vw_photo AS SELECT
      ifnull(c.exif_make, 'N/A') || ' ' || ifnull(c.exif_model, 'N/A') AS Camera,
      ifnull(l.exif_make, 'N/A') || ' ' || ifnull(l.exif_model, 'N/A') AS Lens,
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
      LEFT JOIN camera c ON p.camera_id = c.id
      LEFT JOIN lens l ON p.lens_id = l.id;"
      ]
   })

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
