(ns clurator.settings
  "Shared settings for Clurator the backend."
  (:require [me.raynes.fs :as fs]))

;; Read these from an edn or environment vars

;; The storage for collected files/jibit
(def storage-directory (fs/expand-home "~/pics/clurator"))

;; The location we source new material
(def inbox-path (fs/expand-home "~/pics/inbox"))

;; After importing and processing, inbox should be empty and
;; everything should be moved here. If this path is not specified,
;; assume deletion.
(def inbox-processed-path (fs/expand-home "~/pics/inbox.processed"))

(def thumbnail-cache-dir (fs/expand-home "~/pics/clurator/thumbnails"))

(def database-file (fs/expand-home "~/clurator.db"))
