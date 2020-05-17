(ns clurator.inbox
  (:require [clurator.exif :as exif]
            [clurator.db :as db]
            [clurator.thumbnails :as thumbnails]
            [java-time :as time]
            [me.raynes.fs :as fs]
            [clurator.file-utils :as futils]
            clurator.settings))

(defn eligible-files [inbox-path]
  (fs/find-files* inbox-path futils/eligible-file?))

(defn remove-common-prefix
  [s prefix]
  (if (.startsWith s prefix)
    (subs s (count prefix))
    s))

(defn subdirectory-under-inbox
  "Take a file denoted by `path` and match it against `basedir` to
  return a meaningful subdirectory under it. If file resides directly
  under `basedir` we return `basename basedir`."
  [basedir path]
  (let [path (remove-common-prefix (str path) (str basedir))
        ;; remove leading /
        path (if (= (first path) \/)
               (subs path 1)
               path)
        comps (fs/split path)]
    (cond
      (> (count comps) 1)
      (first comps)

      (= (count comps) 1)
      (fs/base-name basedir)

      :else
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
  "Produce an Emap from file `f'."
  [basedir f]
  (let [exif-tags (exif/get-exif-parsed f)
        extension (.toLowerCase (fs/extension f))
        uuid (java.util.UUID/randomUUID)
        filename (str uuid extension)]
    (merge
     #:meta{:uuid uuid
            :import-date (time/local-date-time)
            :development-date (file-modification-time f)
            :original-filename (fs/base-name f)
            :original-dir (subdirectory-under-inbox basedir f)
            :original-raw nil
            :raw? (boolean (futils/raw-file? f))
            :storage filename}
     exif-tags)))

(defn process-inbox!
  [inbox-path]
  (fs/mkdir clurator.settings/storage-directory)
  (fs/mkdir clurator.settings/thumbnail-dir)
  (let [files (eligible-files inbox-path)]
    (doseq [f files]
      (let [emap (gather-file-info inbox-path f)]
        (println "Importing" (str f) "...")

        ;; make thumbnail
        (thumbnails/create-thumbnail! f (:meta/uuid emap))

        ;; move file under new storage
        (fs/rename f (str clurator.settings/storage-directory
                          "/"
                          (:meta/storage emap)))

        ;; make a record in db
        (db/add-entry! emap)))
    {:total-files (count files)}))

;; Debug stuffs
(comment
  (defn randf [] (rand-nth (eligible-files clurator.settings/inbox-path)))
  )
