(ns clurator.db
  "Database interface"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc.rs]
            [honeysql.core :as sql]
            [honeysql.format :as sqlf]
            [honeysql.helpers :as s]
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.db-schema :refer [update-schema!]]
            clurator.settings))

(declare db)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod sqlf/format-clause :insert-or-replace [[_ table-name] _]
  (str "INSERT OR REPLACE INTO " (sqlf/to-sql table-name)))

(defmethod sqlf/format-clause :insert-or-ignore [[_ table-name] _]
  (str "INSERT OR IGNORE INTO " (sqlf/to-sql table-name)))

(sqlf/register-clause! :insert-or-replace 60)
(sqlf/register-clause! :insert-or-ignore 60)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def jdbc-resultset-type
  jdbc.rs/as-unqualified-lower-maps)

(defn query-1!
  "Make a query-1. Take a map Honeysql understands."
  [query-map]
  (let [formatted-sql (sql/format query-map)]
    (jdbc/execute-one! db formatted-sql {:builder-fn jdbc-resultset-type})))

(defn query!
  "Make a query. Take a map HoneySQL understands."
  [query-map]
  (let [formatted-sql (sql/format query-map)]
    ;; (timbre/debugf "DB QUERY %s => %s" query-map formatted-sql)
    (jdbc/execute! db formatted-sql {:builder-fn jdbc-resultset-type})))

(defn query-count!
  "Make a counting query and extract the resulting integer"
  [query-map]
  (-> (query-1! query-map)
      first
      second))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def photo-clj->db-key
  "Database keys map to what processed keys."
  {:focal_length        :exif/FocalLength
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
   :is_raw              :meta/raw?
   :storage_filename    :meta/storage})

(defn build-sql-keys [e]
  (->>
   (for [[sql-key foo-key] photo-clj->db-key
         :when foo-key]
     [sql-key (get e foo-key)])
   (into {})))

;;;;; Gear is camera or lens
;; TODO move to model.gear

(defn lookup-gear
  [gear-type make model]
  (query! {:select [:id]
           :from [:gear]
           :where [:and
                   [:= :gear_type gear-type]
                   [:= :exif_make make]
                   [:= :exif_model model]]}))

(defn create-gear
  [gear-type make model]
  (query! {:insert-into :gear
           :values [{:gear_type gear-type
                     :exif_make make
                     :exif_model model}]}))

(defn get-or-create-gear
  "Get or create gear with these specs. Return ID as integer. If multiple found,
  we can't do anything and return nil."
  [gear-type make model]
  (let [found (lookup-gear gear-type make model)
        cnt (count found)]
    (cond
      (every? nil? [make model]) nil
      (= 1 cnt) (-> found
                    first
                    first
                    second)
      (= 0 cnt) (do
                  (create-gear gear-type make model)
                  (get-or-create-gear gear-type make model))
      :else nil)))

(defn add-entry!
  "Take an Emap and insert it into DB normalised."
  [e]
  (let [camera-id (get-or-create-gear "camera" (:exif/Make e) (:exif/Model e))
        lens-id (get-or-create-gear "lens" (:exif/LensMake e) (:exif/LensModel e))]
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

;; (def db (jdbc/get-datasource {:dbtype "sqlite"
;;                               :dbname clurator.settings/database-file}))

(let [conn-str (str "jdbc:sqlite:"
                    clurator.settings/database-file
                    "?foreign_keys=on;")]
  (def db (jdbc/get-connection conn-str)))

(update-schema! db)
