(ns clurator.export
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [me.raynes.fs :as fs]
            [java-time :as time]
            clurator.utils
            clurator.settings))

(defn format-output-file-name
  [photo]
  (let [taken (time/local-date-time (:taken_ts photo))]
    (time/format "yyyy-MM-dd (ccc) HH-mm-ss"
                 (time/local-date-time taken))))

(defn export-resize-photos
  "Export selected photos as JPEGs, resize to certain size limit. With
  raw files we probably want to either run dcraw or extract the
  preview files if possible. Or just ignore for now."
  [photos target-dir]
  (fs/mkdir target-dir)
  (doseq [photo photos]
    (let [source (str clurator.settings/storage-directory "/"
                      (:storage_filename photo))
          target (str target-dir "/"
                      (format-output-file-name photo)
                      ".jpeg")]
      (debug "Converting" source "=>" target)
      (fs/exec "convert" source
               "-resize" "1600x1600>"
               "-quality" "90"
               "-unsharp" "0x0.75+0.75+0.008"
               target)))
  ;; We could be deleting the empty dir if there are problems with
  ;; input data. But might be as convenient to leave it be.
  ;; (fs/delete target-dir)
  )
