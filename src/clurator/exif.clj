(ns clurator.exif
  "Read exif tags with the help of Exiftool (clurator.exiftool)."
  (:require [java-time :as time]
            [clurator.exiftool :as exiftool]
            clurator.utils))

(defn str->datetime
  [s]
  (time/local-date-time "yyyy:MM:dd HH:mm:ss" s))

(defn get-exif-map
  "From file denoted by `file-path`, get keyworded map of parsed EXIF."
  [file-path]
  (-> (exiftool/get-exif-map file-path)
      (update "CreateDate" str->datetime)
      (clurator.utils/keywordify-map :exif)))

(comment
  (get-exif-map "/home/progo/panasonic-test-material/2020-05-16-15-50-P1020617.jpg")
  )
