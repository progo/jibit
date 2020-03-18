(ns clurator.inbox
  (:require [clurator.exif :as exif]
            [clurator.db :as db]
            [clurator.thumbnails :as thumbnails]
            [java-time :as time]
            [me.raynes.fs :as fs]
            clurator.settings))


;;;;;;78 chars;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn eligible-file?
  [path]
  (and
   (fs/file? path)
   (clurator.settings/image-extensions (.toLowerCase (fs/extension path)))))

(defn eligible-files [inbox-path]
  (fs/find-files* inbox-path eligible-file?))

(defn remove-common-prefix
  [s prefix]
  (if (.startsWith s prefix)
    (subs s (count prefix))
    s))

(defn subdirectory-under-inbox
  [path]
  (let [path (remove-common-prefix (str path)
                                   (str clurator.settings/inbox-path))
        ;; remove leading /
        path (if (= (first path) \/)
               (subs path 1)
               path)
        comps (fs/split path)]
    (if (> (count comps) 1)
      (first comps)
      "")))

;; Notes on =fs/delete=: It behaves like "rm" or "rmdir" depending on
;; target. Will not touch occupied directories. Now =fs/delete-dir= is
;; your standard "rm -r" that will eat everything. And =fs/delete-dir=
;; will traverse through symlinks so let's be careful.

(defn file-modification-time [f]
  (-> f
      fs/mod-time
      time/fixed-clock
      time/local-date-time))

(defn gather-file-info
  [f]
  (let [exif-tags (exif/get-exif-parsed f)
        extension (.toLowerCase (fs/extension f))
        uuid (java.util.UUID/randomUUID)
        filename (str uuid extension)]
    (merge
     #:meta{:uuid uuid
            :import-date (time/local-date-time)
            :development-date (file-modification-time f)
            :original-filename (fs/base-name f)
            :original-dir (subdirectory-under-inbox f)
            :original-raw nil
            :storage filename}
     exif-tags)))

(defn process-inbox!
  [inbox-path]
  (fs/mkdir clurator.settings/storage-directory)
  (when clurator.settings/inbox-processed-path)
    (fs/mkdir clurator.settings/inbox-processed-path)
  (doseq [f (eligible-files inbox-path)]
    (let [info (gather-file-info f)]
      (println "gonna process" f "into" (:meta/storage info))

      ;; copy/move file
      (fs/copy f (str clurator.settings/storage-directory
                      "/"
                      (:meta/storage info)))

      ;; make thumbnail

      ;; make a record in db
      (db/add-entry! info)
      )))

;; Debug stuffs
(comment
  (defn randf [] (rand-nth (eligible-files clurator.settings/inbox-path)))
  )
