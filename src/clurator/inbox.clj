(ns clurator.inbox
  (:require [clurator.exif :as exif]
            [me.raynes.fs :as fs]))

;; The storage for collected files/jibit
(def clurator-collection-path "~/pics/clurator")

;; The location we source new material
(def inbox-path "~/pics/inbox")

;; After importing and processing, inbox should be empty and
;; everything should be moved here. If this path is not specified,
;; assume deletion.
(def inbox-processed-path "~/pics/inbox.processed")

(def remove-after-import? false)

;; Keep in lower case.
(def picture-extensions #{".dng"
                          ".jpg" ".jpeg"
                          ".png" ".tif" ".tiff"
                          ".cr2" ".cr3"
                          ".orf" ".nef"
                          ".arw" ".rw2"})

;;;;;;78 chars;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn eligible-file?
  [path]
  (and
   (fs/file? path)
   (picture-extensions (.toLowerCase (fs/extension path)))))

(defn eligible-files [inbox-path]
  (fs/find-files* (fs/expand-home inbox-path)
                  eligible-file?))

;; Notes on =fs/delete=: It behaves like "rm" or "rmdir" depending on
;; target. Will not touch occupied directories. Now =fs/delete-dir= is
;; your standard "rm -r" that will eat everything. And =fs/delete-dir=
;; will traverse through symlinks so let's be careful.

(defn process-inbox!
  [inbox-path]
  (fs/mkdir (fs/expand-home clurator-collection-path))
  (when-not remove-after-import?
    (fs/mkdir (fs/expand-home inbox-processed-path)))
  (doseq [f (eligible-files inbox-path)]
    (let [filename (str (java.util.UUID/randomUUID) ".image")]
      ;; TODO exif analysis for maybe date, image format
      (println "gonna process" f "into" filename)
      )))
