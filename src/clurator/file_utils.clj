(ns clurator.file-utils
  (:require [me.raynes.fs :as fs]
            clurator.settings))

(defn file-extension-in-set?
  [path ext-set]
  (and
   (fs/file? path)
   (ext-set (.toLowerCase (fs/extension path)))))

(defn raw-file?
  [path]
  (file-extension-in-set? path clurator.settings/image-raw-extensions))

(defn bitmap-file?
  [path]
  (file-extension-in-set? path clurator.settings/image-bitmap-extensions))

(defn eligible-file?
  "The types of files we (try to) support."
  [path]
  (or (raw-file? path)
      (bitmap-file? path)))
