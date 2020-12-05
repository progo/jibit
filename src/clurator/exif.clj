(ns clurator.exif
  "Read exif tags with the help of Exiftool (clurator.exiftool)."
  (:require [java-time :as time]
            [clurator.exiftool :as exiftool]
            clurator.utils))

(defn str->datetime
  [s]
  (time/local-date-time "yyyy:MM:dd HH:mm:ss" s))

(defn get-best-lens-data
  "Exiftool is giving us several candidates for lens identification. But
  they are not uniform across brands and file formats."
  [emap]
  (assoc emap "BestLens"
         (cond
           (get emap "LensID")
           (get emap "LensID")

           (get emap "LensSpec")
           (get emap "LensSpec")

           :else
           (get emap "Lens"))))

(defn get-best-creation-date
  "CreateDate is common but sometimes not used. Query other values and
  supply it."
  [emap]
  (assoc emap "CreateDate"
         (cond
           (get emap "CreateDate")
           (get emap "CreateDate")

           (get emap "DateTimeOriginal")
           (get emap "DateTimeOriginal")

           ;; will crash at later step
           :else nil)))

(defn get-exif-map
  "From file denoted by `file-path`, get keyworded map of parsed EXIF."
  [file-path]
  (-> (exiftool/get-exif-map file-path)
      get-best-lens-data
      get-best-creation-date
      (update "CreateDate" str->datetime)
      (clurator.utils/keywordify-map :exif)))

(comment
  (get-exif-map "/home/progo/pics/nikon-exif-test/DDF_9205_rawtherapee.jpg")

  (get-exif-map "/home/progo/pics/nikon-exif-test/DDF_9205_darktable.jpg")
  )
