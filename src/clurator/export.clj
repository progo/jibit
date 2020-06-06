(ns clurator.export
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [me.raynes.fs :as fs]
            clurator.utils
            clurator.settings
            ))

(defn export-resize-photos
  "Export selected photos as JPEGs, resize to certain size limit. With
  raw files we probably want to either run dcraw or extract the
  preview files if possible. Or just ignore for now."
  [files target-dir]
  (fs/mkdir target-dir)
  (doseq [f files]
    (let [source (str clurator.settings/storage-directory "/" f)
          [basename ext] (fs/split-ext f)
          target (str target-dir "/" basename ".jpeg")]
      (debug "Doing" source "=>" target)
      (fs/exec "convert" source "-resize" "1600x1600" target)))
  ;; We could be deleting the empty dir if there are problems with
  ;; input data. But might be as convenient to leave it be.
  ;; (fs/delete target-dir)
  )
